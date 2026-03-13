package com.studyolle.modules.board.repository;

import com.studyolle.modules.board.entity.Board;
import com.studyolle.modules.board.entity.BoardCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 게시글(Board) 데이터 접근 계층
 *
 * =============================================
 * @EntityGraph — N+1 쿼리 방지를 위한 2가지 사용 방식
 * =============================================
 *
 * JPA의 @EntityGraph는 "이 쿼리를 실행할 때, 어떤 연관 엔티티를 함께 로딩할지"를
 * 선언적으로 지정하는 기능입니다. LAZY 로딩이 기본인 연관관계를 특정 쿼리에서만
 * EAGER처럼 한 번에 가져오도록 오버라이드합니다.
 *
 * 내부적으로는 SQL의 LEFT JOIN FETCH로 변환되어, 별도 SELECT 없이
 * 한 번의 쿼리로 연관 엔티티까지 함께 조회합니다.
 *
 * ─────────────────────────────────────────────
 * 방식 1: 인라인 방식 — @EntityGraph(attributePaths = {"createdBy"})
 * ─────────────────────────────────────────────
 *
 * Repository 메서드 위에 직접 "어떤 필드를 함께 로딩할지" 지정합니다.
 * 엔티티 클래스에 아무런 선언 없이 Repository에서만 정의하는 방식입니다.
 *
 * [사용 시점]
 * - 로딩할 연관관계가 단순할 때 (1~2개 필드)
 * - 특정 Repository 메서드에서만 사용하는 일회성 그래프일 때
 * - 엔티티 클래스를 수정하지 않고 빠르게 적용하고 싶을 때
 *
 * [예시 — 이 클래스의 목록 조회 메서드]
 *
 *   @EntityGraph(attributePaths = {"createdBy"})
 *   Page<Board> findByStudyOrderByCreatedDateTimeDesc(Study, Pageable);
 *
 *   → 생성되는 SQL:
 *     SELECT b.*, a.*
 *     FROM board b
 *     LEFT JOIN account a ON b.created_by_id = a.id
 *     WHERE b.study_id = ?
 *     ORDER BY b.created_date_time DESC
 *
 *   → createdBy(작성자)를 LEFT JOIN으로 한 번에 가져오므로,
 *     Thymeleaf에서 board.createdBy.nickname 접근 시 추가 쿼리가 발생하지 않습니다.
 *
 * ─────────────────────────────────────────────
 * 방식 2: Named 방식 — @NamedEntityGraph + @EntityGraph("그래프이름")
 * ─────────────────────────────────────────────
 *
 * 엔티티 클래스에 @NamedEntityGraph로 "이름 붙은 그래프"를 미리 정의해두고,
 * Repository에서는 그 이름만 참조하는 방식입니다.
 *
 * [엔티티에서의 선언 — Board.java]
 *
 *   @NamedEntityGraph(
 *       name = "Board.withComments",               // 그래프 이름
 *       attributeNodes = {
 *           @NamedAttributeNode(                    // 1단계: Board의 comments 로딩
 *               value = "comments",
 *               subgraph = "comments-with-account"  // 2단계 서브그래프 지정
 *           ),
 *           @NamedAttributeNode("createdBy")        // 1단계: Board의 createdBy 로딩
 *       },
 *       subgraphs = @NamedSubgraph(
 *           name = "comments-with-account",         // 서브그래프 정의
 *           attributeNodes = @NamedAttributeNode("account")  // 2단계: Comment의 account 로딩
 *       )
 *   )
 *
 * [Repository에서의 참조]
 *
 *   @EntityGraph("Board.withComments")
 *   Optional<Board> findBoardWithCommentsById(Long id);
 *
 *   → 생성되는 SQL:
 *     SELECT b.*, a1.*, c.*, a2.*
 *     FROM board b
 *     LEFT JOIN account a1 ON b.created_by_id = a1.id       -- createdBy
 *     LEFT JOIN board_comment c ON c.board_id = b.id         -- comments
 *     LEFT JOIN account a2 ON c.account_id = a2.id           -- comment의 account
 *     WHERE b.id = ?
 *
 *   → 한 번의 쿼리로 게시글 + 작성자 + 댓글 목록 + 각 댓글 작성자까지 전부 로딩합니다.
 *
 * [사용 시점]
 * - 로딩할 연관관계가 복잡할 때 (2단계 이상 깊이, subgraph 필요)
 * - 여러 Repository 메서드에서 같은 그래프를 재사용할 때
 * - 그래프 구조를 엔티티와 함께 관리하고 싶을 때
 *
 * ─────────────────────────────────────────────
 * 핵심 차이점 요약
 * ─────────────────────────────────────────────
 *
 *   구분               인라인 (attributePaths)        Named (@NamedEntityGraph)
 *   ──────────────    ─────────────────────────    ──────────────────────────
 *   정의 위치           Repository 메서드 위           엔티티 클래스 위
 *   깊이(depth)        1단계만 가능                    subgraph로 다단계 가능
 *                     (board.createdBy 까지)        (board.comments.account 까지)
 *   재사용성            해당 메서드에서만 사용             이름으로 여러 곳에서 참조 가능
 *   적합한 상황          단순 + 일회성                   복잡 + 재사용
 *
 * ─────────────────────────────────────────────
 * 이 Repository에서의 전략
 * ─────────────────────────────────────────────
 *
 * [목록 조회] → 인라인 방식 (attributePaths = {"createdBy"})
 *   목록에서는 게시글 제목 + 작성자 닉네임/아바타만 필요합니다.
 *   댓글은 필요 없고, commentCount 비정규화 필드로 댓글 수를 표시합니다.
 *   → createdBy 1개만 JOIN하면 되므로 인라인이 적합합니다.
 *
 * [상세 조회] → Named 방식 ("Board.withComments")
 *   상세 페이지에서는 댓글 목록 + 각 댓글의 작성자까지 필요합니다.
 *   board → comments → account 로 2단계 깊이의 로딩이 필요하므로
 *   subgraph를 지원하는 Named 방식이 적합합니다.
 *
 * 이렇게 같은 엔티티라도 화면별로 필요한 데이터 범위가 다르므로,
 * EntityGraph를 분리하여 각 상황에 최적화된 쿼리를 실행합니다.
 */
@Transactional(readOnly = true)
public interface BoardRepository extends JpaRepository<Board, Long> {

    // ============================
    // 목록 조회 (인라인 EntityGraph)
    // ============================

    /**
     * 특정 스터디의 게시글을 최신순 페이징 조회
     *
     * @EntityGraph(attributePaths = {"createdBy"})
     * → Board를 조회할 때 createdBy(Account)를 LEFT JOIN FETCH로 함께 로딩
     * → 목록 페이지에서 board.createdBy.nickname 접근 시 추가 SELECT 발생 안 함
     *
     * 만약 이 어노테이션이 없다면?
     * → createdBy는 LAZY 프록시 상태로 조회됨
     * → Thymeleaf에서 각 게시글의 작성자에 접근할 때마다 SELECT 쿼리 발생
     * → 게시글 10개면 1(목록) + 10(작성자) = 11번 쿼리 (N+1 문제)
     */
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Board> findByStudyOrderByCreatedDateTimeDesc(Study study, Pageable pageable);

    /**
     * 특정 스터디 + 카테고리 필터 페이징 조회
     *
     * 위와 동일한 이유로 createdBy를 함께 로딩합니다.
     * 카테고리 필터가 추가되었을 뿐, 화면에 필요한 데이터는 동일하기 때문입니다.
     */
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Board> findByStudyAndCategoryOrderByCreatedDateTimeDesc(Study study, BoardCategory category, Pageable pageable);

    // ============================
    // 상세 조회 (Named EntityGraph)
    // ============================

    /**
     * 게시글 상세 조회 (댓글 + 댓글 작성자 한 번에 로딩)
     *
     * @EntityGraph("Board.withComments")
     * → Board.java에 정의된 Named EntityGraph를 참조
     * → Board.createdBy + Board.comments + Comment.account 를 한 번에 JOIN FETCH
     *
     * 인라인 방식으로는 이 쿼리를 표현할 수 없는 이유:
     * attributePaths = {"comments", "comments.account"} 형태로 적을 수는 있지만,
     * 이는 Spring Data JPA가 내부적으로 subgraph로 변환해주는 편의 기능이고,
     * 명시적으로 subgraph 구조를 선언하는 Named 방식이 의도가 더 명확합니다.
     * 특히 그래프가 복잡해지거나 여러 메서드에서 재사용할 때 Named가 유리합니다.
     */
    @EntityGraph("Board.withComments")
    Optional<Board> findBoardWithCommentsById(Long id);

    // ============================
    // 스터디 뷰 미리보기용
    // ============================

    /** 특정 스터디의 최근 게시글 5개 (스터디 메인 페이지 미리보기) */
    List<Board> findTop5ByStudyOrderByCreatedDateTimeDesc(Study study);

    /** 게시글 수 조회 */
    long countByStudy(Study study);
}