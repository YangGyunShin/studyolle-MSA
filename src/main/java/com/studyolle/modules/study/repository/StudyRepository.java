package com.studyolle.modules.study.repository;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.Study;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * StudyRepository - Study 엔티티에 대한 데이터 접근 계층
 * <p>
 * =============================================
 * 아키텍처 위치
 * =============================================
 * <p>
 * Spring Data JPA가 자동으로 구현체를 생성하는 인터페이스입니다.
 * JpaRepository<Study, Long>을 상속받아 기본 CRUD 기능을 포함하며,
 * StudyRepositoryExtension을 추가 상속하여 QueryDSL 기반의 복잡한 쿼리도 지원합니다.
 * <p>
 * 호출 흐름:
 * - StudyService / StudySettingsService -> StudyRepository -> DB
 * <p>
 * =============================================
 *
 * @EntityGraph 활용 전략
 * =============================================
 * <p>
 * Study 엔티티는 4개의 @ManyToMany 연관관계(managers, members, tags, zones)를 가지고 있어,
 * 기본 지연 로딩(LAZY)에서는 컬렉션에 접근할 때마다 추가 쿼리가 발생합니다 (N+1 문제).
 * <p>
 * 이를 해결하기 위해 각 메서드에 @EntityGraph를 적용하여,
 * 해당 기능에 필요한 최소한의 연관관계만 fetch join으로 즉시 로딩합니다.
 * <p>
 * 예를 들어:
 * - 스터디 상세 페이지: 모든 연관 엔티티가 필요 -> findByPath (tags + zones + managers + members)
 * - 태그 설정 페이지: 태그와 관리자만 필요 -> findAccountWithTagsByPath (tags + managers)
 * - 상태 변경: 관리자 권한만 확인 -> findStudyWithManagersByPath (managers)
 * - 존재 확인: 기본 정보만 필요 -> findStudyOnlyByPath (EntityGraph 없음)
 * <p>
 * 이렇게 "필요한 만큼만 로딩"하는 전략으로 쿼리 성능을 최적화합니다.
 * <p>
 * =============================================
 * @Transactional(readOnly = true)
 * =============================================
 * <p>
 * 인터페이스 레벨에 선언하여, 모든 쿼리 메서드가 읽기 전용 트랜잭션으로 실행됩니다.
 * 이점:
 * - Hibernate의 Dirty Checking 비활성화 -> 성능 향상
 * - 실수로 인한 데이터 수정 방지
 * - 일부 DB에서 읽기 전용 최적화 적용 (MySQL의 경우 복제 서버로 라우팅 등)
 */
@Transactional(readOnly = true)
public interface StudyRepository extends JpaRepository<Study, Long>, StudyRepositoryExtension {

    // ============================
    // 단순 존재 확인
    // ============================

    /**
     * 해당 path가 이미 사용 중인지 확인합니다.
     * <p>
     * Spring Data JPA의 메서드 이름 규칙에 따라 자동 생성됩니다.
     * -> SELECT COUNT(*) > 0 FROM study WHERE path = :path
     * <p>
     * 사용처:
     * - StudyFormValidator: 스터디 생성 시 path 중복 검증
     * - StudySettingsService.isValidPath(): 경로 변경 시 중복 검증
     */
    boolean existsByPath(String path);

    // ============================
    // 단건 조회 (EntityGraph별 분류)
    // ============================

    /**
     * [모든 연관 엔티티 로딩] tags + zones + managers + members
     * <p>
     * 사용처: 스터디 상세 페이지, 멤버 목록 페이지
     * 뷰에서 스터디의 모든 정보(태그, 지역, 관리자, 멤버)를 한 번에 표시해야 하므로
     * 모든 연관 엔티티를 함께 조회합니다.
     * <p>
     * EntityGraph.EntityGraphType.LOAD:
     * - 명시된 속성은 EAGER로 로딩
     * - 나머지는 엔티티에 정의된 기본 fetch 전략을 따름
     */
    @EntityGraph(attributePaths = {"tags", "zones", "managers", "members"}, type = EntityGraph.EntityGraphType.LOAD)
    Study findByPath(String path);

    /**
     * [태그 + 관리자 로딩] tags + managers
     * <p>
     * 사용처: 태그 설정 화면
     * 태그 목록 표시 + 관리자 권한 확인에만 필요하므로,
     * zones와 members는 로딩하지 않아 쿼리를 경량화합니다.
     */
    @EntityGraph(attributePaths = {"tags", "managers"})
    Study findAccountWithTagsByPath(String path);

    /**
     * [지역 + 관리자 로딩] zones + managers
     * <p>
     * 사용처: 활동 지역 설정 화면
     */
    @EntityGraph(attributePaths = {"zones", "managers"})
    Study findAccountWithZonesByPath(String path);

    /**
     * [관리자만 로딩] managers
     * <p>
     * 사용처: 스터디 상태 변경 (공개/종료/모집/경로/제목/삭제)
     * 상태 변경 시에는 관리자 권한 확인만 필요하므로, 최소한의 데이터만 로딩합니다.
     */
    @EntityGraph(attributePaths = "managers")
    Study findStudyWithManagersByPath(String path);

    /**
     * [멤버만 로딩] members
     * <p>
     * 사용처: 멤버 가입/탈퇴 처리
     * members 컬렉션을 조작해야 하므로 fetch join으로 미리 로딩합니다.
     * 지연 로딩 상태에서 members에 접근하면 LazyInitializationException이 발생할 수 있습니다.
     */
    @EntityGraph(attributePaths = "members")
    Study findStudyWithMembersByPath(String path);

    /**
     * [EntityGraph 없음] Study 기본 정보만 조회
     * <p>
     * 사용처: 단순 존재 확인, 모임(Event) 등록 시 스터디 조회
     * 연관 엔티티가 필요 없는 경우 불필요한 JOIN을 피하여 최적의 성능을 제공합니다.
     */
    Study findStudyOnlyByPath(String path);

    // ============================
    // ID 기반 조회 (이벤트 리스너용)
    // ============================

    /**
     * [태그 + 지역 로딩] tags + zones (ID 기반)
     * <p>
     * 사용처: StudyEventListener.handleStudyCreatedEvent()
     * 스터디 생성 이벤트 처리 시, 관심사(태그/지역) 매칭을 위해
     * tags와 zones 정보가 필요합니다.
     */
    @EntityGraph(attributePaths = {"zones", "tags"})
    Study findStudyWithTagsAndZonesById(Long id);

    /**
     * [관리자 + 멤버 로딩] managers + members (ID 기반)
     * <p>
     * 사용처: StudyEventListener.handleStudyUpdateEvent()
     * 스터디 수정 이벤트 처리 시, 알림 대상(관리자 + 멤버)을 파악하기 위해
     * managers와 members 정보가 필요합니다.
     */
    @EntityGraph(attributePaths = {"members", "managers"})
    Study findStudyWithManagersAndMembersById(Long id);

    // ============================
    // 목록 조회 (대시보드 / 메인 페이지용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여하고 있는 활동 중인 스터디 5개를 최신순으로 조회합니다.
     * <p>
     * 메서드 이름 규칙 해석:
     * - findFirst5: 상위 5개만 조회
     * - ByManagersContaining: managers 컬렉션에 account가 포함된 것
     * - AndClosed: closed 필드가 주어진 값과 일치
     * - OrderByPublishedDateTimeDesc: 공개일 기준 내림차순 정렬
     * <p>
     * 사용처: 대시보드 - "내가 운영 중인 스터디" 섹션
     */
    List<Study> findFirst5ByManagersContainingAndClosedOrderByPublishedDateTimeDesc(Account account, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여하고 있는 활동 중인 스터디 5개를 최신순으로 조회합니다.
     * <p>
     * 사용처: 대시보드 - "내가 참여 중인 스터디" 섹션
     */
    List<Study> findFirst5ByMembersContainingAndClosedOrderByPublishedDateTimeDesc(Account account, boolean closed);

    /**
     * 공개 상태이며 마감되지 않은 최신 스터디 9개를 조회합니다.
     * tags와 zones를 함께 fetch join하여 카드형 목록에서 태그/지역 정보를 표시합니다.
     * <p>
     * 사용처: 메인 페이지 - 최신 스터디 목록
     */
    @EntityGraph(attributePaths = {"zones", "tags"})
    List<Study> findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(boolean published, boolean closed);

    // ============================
    // 목록 조회 (프로필 페이지용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여하고 있는 활동 중인 스터디를 최신순으로 조회합니다.
     * tags, zones를 함께 fetch join하여 카드형 목록에서 태그/지역 정보를 표시합니다.
     *
     * 기존 findFirst5ByManagersContaining...과의 차이:
     * - 개수 제한 없음 (대시보드는 5개, 프로필은 전체)
     * - @EntityGraph로 tags/zones 즉시 로딩 (study-list fragment에서 접근 필요)
     *
     * 사용처: 프로필 페이지 - "관리 중인 스터디" 섹션
     */
    @EntityGraph(attributePaths = {"tags", "zones"})
    List<Study> findByManagersContainingAndClosedOrderByPublishedDateTimeDesc(Account account, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여하고 있는 활동 중인 스터디를 최신순으로 조회합니다.
     * tags, zones를 함께 fetch join하여 카드형 목록에서 태그/지역 정보를 표시합니다.
     *
     * 사용처: 프로필 페이지 - "참여 중인 스터디" 섹션
     */
    @EntityGraph(attributePaths = {"zones", "tags"})
    List<Study> findByMembersContainingAndClosedOrderByPublishedDateTimeDesc(Account account, boolean closed);
}