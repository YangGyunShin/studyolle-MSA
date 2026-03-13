package com.studyolle.study.repository;

import com.studyolle.study.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Study 엔티티의 데이터 접근 계층.
 *
 * Spring Data JPA 가 이 인터페이스를 스캔하여 런타임에 구현체를 자동으로 생성한다.
 * JpaRepository<Study, Long> 상속으로 save, findById, delete 등 기본 CRUD 가 무료로 제공된다.
 * StudyRepositoryExtension 을 추가 상속하여 QueryDSL 기반의 복잡한 쿼리도 지원한다.
 *
 * =============================================
 * @Transactional(readOnly = true)
 * =============================================
 *
 * 인터페이스 레벨에 선언하여 모든 조회 메서드가 읽기 전용 트랜잭션으로 실행된다.
 *
 * 읽기 전용 트랜잭션의 이점:
 * - JPA 의 Dirty Checking(변경 감지)을 비활성화한다.
 *   Dirty Checking 이란 트랜잭션 종료 시점에 조회한 엔티티의 현재 상태와
 *   처음 조회했을 때의 상태(스냅샷)를 비교하여 변경된 게 있으면 UPDATE 를 자동으로 날리는 기능이다.
 *   조회만 하는 경우에는 이 스냅샷 비교가 불필요한 연산이므로, readOnly=true 로 꺼두면 성능이 향상된다.
 * - 쓰기 작업이 필요한 Service 메서드에는 @Transactional 을 별도 선언하면 이 설정보다 우선 적용된다.
 *
 * =============================================
 * @ElementCollection 과 N+1 처리 전략
 * =============================================
 *
 * Study.tagIds, Study.zoneIds, Study.managerIds, Study.memberIds 는 @ElementCollection 이다.
 * @ElementCollection 은 @ManyToMany 와 달리 연관 엔티티가 아닌 "값(Value)" 컬렉션이므로
 * @EntityGraph 를 이용한 fetch join 을 사용할 수 없다.
 *
 * 대신 application.yml 의 아래 설정으로 N+1 문제를 해결한다:
 *   spring.jpa.properties.hibernate.default_batch_fetch_size: 100
 *
 * 이 설정이 있으면 여러 Study 를 조회한 뒤 각 컬렉션에 접근할 때,
 * Study 한 건당 쿼리를 날리는 대신 IN 쿼리 한 번으로 일괄 로딩한다.
 * (예: Study 10건 조회 후 tagIds 접근 → SELECT * FROM study_tag_ids WHERE study_id IN (1,2,...,10))
 * 자세한 내용은 이 파일 하단 블록 주석을 참고한다.
 */
@Transactional(readOnly = true)
public interface StudyRepository extends JpaRepository<Study, Long>, StudyRepositoryExtension {

    // ============================
    // 단순 존재 확인
    // ============================

    /**
     * 해당 path 가 이미 사용 중인지 확인한다.
     *
     * Spring Data JPA 메서드 이름 규칙에 따라 아래 쿼리가 자동 생성된다.
     *   SELECT COUNT(*) > 0 FROM study WHERE path = :path
     *
     * COUNT 전체를 세지 않고 조건에 맞는 행이 하나라도 있으면 즉시 true 를 반환하므로 효율적이다.
     *
     * 사용처: StudyService — 스터디 생성 또는 경로 변경 시 path 중복 검증.
     */
    boolean existsByPath(String path);

    // ============================
    // 단건 조회
    // ============================

    /**
     * path 로 스터디를 조회한다.
     *
     * Study 기본 컬럼만 SELECT 하며, @ElementCollection 컬렉션(tagIds, zoneIds 등)은
     * 이후 해당 컬렉션에 접근할 때 batch fetch 로 일괄 로딩된다.
     *
     * 사용처: 스터디 상세 조회, 관리자 권한 확인, 멤버 가입/탈퇴 처리 등
     * 스터디의 전체 정보가 필요한 모든 경우.
     */
    Study findByPath(String path);

    /**
     * path 로 스터디의 기본 정보만 조회한다.
     *
     * 컬렉션에 접근하지 않는 경우(단순 존재 확인, 이벤트 연결 등)에 사용한다.
     * findByPath 와 의미상 동일한 쿼리를 생성하지만,
     * 호출하는 쪽에서 "이 조회는 컬렉션이 필요 없다"는 의도를 명확히 드러내기 위해 구분한다.
     */
    Study findStudyOnlyByPath(String path);

    // ============================
    // 목록 조회 (대시보드용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여 중인 활동 중인 스터디 5개를 최신순으로 조회한다.
     *
     * 메서드 이름 규칙 해석:
     * - findFirst5                            : 상위 5개만 조회 (LIMIT 5)
     * - ByManagerIdsContaining                : managerIds 컬렉션에 accountId 가 포함된 것
     * - AndClosed                             : closed 필드가 파라미터 값과 일치하는 것
     * - OrderByPublishedDateTimeDesc          : 공개일 기준 내림차순 정렬
     *
     * Spring Data JPA 는 @ElementCollection 컬렉션에도 Containing 키워드를 지원한다.
     * 생성되는 쿼리 (개념):
     *   SELECT * FROM study s
     *   WHERE EXISTS (SELECT 1 FROM study_manager_ids WHERE study_id = s.id AND manager_id = :accountId)
     *   AND s.closed = false
     *   ORDER BY s.published_date_time DESC
     *   LIMIT 5
     *
     * 사용처: 대시보드 — "내가 운영 중인 스터디" 섹션.
     */
    List<Study> findFirst5ByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여 중인 활동 중인 스터디 5개를 최신순으로 조회한다.
     *
     * 사용처: 대시보드 — "내가 참여 중인 스터디" 섹션.
     */
    List<Study> findFirst5ByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 공개 상태이며 마감되지 않은 최신 스터디 9개를 조회한다.
     *
     * 사용처: 메인 페이지 — 최신 스터디 목록.
     */
    List<Study> findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(
            boolean published, boolean closed);

    // ============================
    // 목록 조회 (프로필 페이지용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여 중인 활동 중인 스터디 전체를 최신순으로 조회한다.
     *
     * 대시보드(5개 제한)와 달리 프로필 페이지에서는 전체를 표시하므로 First5 제한이 없다.
     *
     * 사용처: 프로필 페이지 — "관리 중인 스터디" 섹션.
     */
    List<Study> findByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여 중인 활동 중인 스터디 전체를 최신순으로 조회한다.
     *
     * 사용처: 프로필 페이지 — "참여 중인 스터디" 섹션.
     */
    List<Study> findByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);
}

/*
 * ============================================================
 * [@ElementCollection 과 Batch Fetch 심층 설명]
 * ============================================================
 *
 * 1. @ElementCollection 이란?
 * ------------------------------------------------------------
 * @ElementCollection 은 엔티티가 아닌 값(Value) 타입을 컬렉션으로 저장할 때 사용한다.
 * Study 의 tagIds, zoneIds, managerIds, memberIds 처럼 Long ID 만 저장하는 경우가 대표적이다.
 *
 * @ManyToMany 와 달리 반대편에 엔티티가 없다. 그냥 숫자 ID 의 집합이다.
 * JPA 는 이 컬렉션을 별도 테이블에 저장한다:
 *
 *   study_tag_ids  (study_id BIGINT, tag_id BIGINT)
 *   study_zone_ids (study_id BIGINT, zone_id BIGINT)
 *   ...
 *
 *
 * 2. N+1 문제란?
 * ------------------------------------------------------------
 * Study 10건을 조회한 뒤 각 스터디의 tagIds 에 접근하면 어떻게 될까?
 *
 * 기본(LAZY) 로딩에서는 다음과 같은 쿼리가 순서대로 발생한다:
 *
 *   SELECT * FROM study LIMIT 10;          (쿼리 1번)
 *   SELECT * FROM study_tag_ids WHERE study_id = 1;  (Study 1번 태그)
 *   SELECT * FROM study_tag_ids WHERE study_id = 2;  (Study 2번 태그)
 *   ... 총 10번 추가 쿼리
 *
 * 이처럼 N 건 조회 후 연관 데이터 N 번 추가 조회가 발생하는 것을 "N+1 문제"라고 한다.
 * 스터디가 100건이면 쿼리가 101번 발생한다.
 *
 *
 * 3. Batch Fetch 로 해결하는 방법
 * ------------------------------------------------------------
 * application.yml 에 아래 설정을 추가하면:
 *
 *   spring:
 *     jpa:
 *       properties:
 *         hibernate:
 *           default_batch_fetch_size: 100
 *
 * Hibernate 가 @ElementCollection 컬렉션에 접근할 때 각각 쿼리를 날리지 않고
 * 현재 컨텍스트에 있는 study_id 를 최대 100개씩 모아서 IN 쿼리 한 번으로 처리한다:
 *
 *   SELECT * FROM study_tag_ids WHERE study_id IN (1, 2, 3, ..., 10);  (쿼리 1번)
 *
 * N+1 쿼리가 IN 쿼리 1번으로 줄어든다.
 *
 *
 * 4. @EntityGraph fetch join 을 쓰지 않는 이유
 * ------------------------------------------------------------
 * @EntityGraph 는 @ManyToMany 나 @OneToMany 같이 연관 엔티티가 있을 때 사용한다.
 * fetch join 은 "두 엔티티를 JOIN 해서 한 번에 가져온다"는 개념이기 때문이다.
 *
 * @ElementCollection 은 엔티티가 아닌 값의 컬렉션이라 JOIN 대상이 없다.
 * 따라서 @EntityGraph 를 선언해도 아무 효과가 없거나 오류가 발생한다.
 * Batch Fetch 가 @ElementCollection 의 올바른 N+1 해결책이다.
 */