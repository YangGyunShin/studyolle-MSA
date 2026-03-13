package com.studyolle.study.repository;

import com.studyolle.study.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * StudyRepository
 *
 * =============================================
 * [모노리틱과의 변경사항]
 * =============================================
 *
 * [제거]
 * - @EntityGraph(attributePaths = {"managers", "members"}) 관련 메서드
 *   managers/members 가 @ElementCollection 이 되어 EntityGraph 대신
 *   application.yml 의 default_batch_fetch_size 로 N+1 을 처리한다.
 * - Account 파라미터를 받는 메서드 전체
 *   (findByManagersContaining... → findByManagerIdsContaining...)
 * - 이벤트 리스너용 ID 기반 조회 메서드
 *   (findStudyWithTagsAndZonesById, findStudyWithManagersAndMembersById)
 *   StudyEventListener 를 제거했으므로 필요 없다.
 *
 * [유지]
 * - @Transactional(readOnly = true) 전략 — 모노리틱과 동일
 * - existsByPath — 경로 중복 검증
 * - findByPath, findStudyOnlyByPath 등 단건 조회
 *
 * =============================================
 * @Transactional(readOnly = true) 전략 — 모노리틱과 동일
 * =============================================
 *
 * 인터페이스 레벨에 선언하여 모든 쿼리 메서드가 읽기 전용 트랜잭션으로 실행된다.
 * 이점: Hibernate 의 Dirty Checking 비활성화 → 성능 향상
 */
@Transactional(readOnly = true)
public interface StudyRepository extends JpaRepository<Study, Long>, StudyRepositoryExtension {

    // ============================
    // 단순 존재 확인
    // ============================

    /**
     * 해당 path 가 이미 사용 중인지 확인한다.
     *
     * [모노리틱 참조]
     * - StudyFormValidator: 스터디 생성 시 path 중복 검증
     * - StudySettingsService.isValidPath(): 경로 변경 시 중복 검증
     */
    boolean existsByPath(String path);

    // ============================
    // 단건 조회
    // ============================

    /**
     * path 로 스터디 조회. @ElementCollection 은 batch fetch 로 로딩.
     *
     * [모노리틱과의 차이]
     * 모노리틱에서는 @EntityGraph(attributePaths = {"tags","zones","managers","members"}) 로
     * fetch join 을 명시했으나, MSA 에서는 @ElementCollection 이므로
     * application.yml 의 default_batch_fetch_size 가 N+1 을 방지한다.
     */
    Study findByPath(String path);

    /**
     * [EntityGraph 없음] Study 기본 정보만 조회.
     *
     * [모노리틱 참조]
     * 단순 존재 확인, 모임(Event) 등록 시 스터디 조회.
     * 연관 컬렉션이 필요 없는 경우 불필요한 쿼리를 피하여 성능 최적화.
     */
    Study findStudyOnlyByPath(String path);

    // ============================
    // 목록 조회 (대시보드용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여 중인 활동 중인 스터디 5개를 최신순으로 조회한다.
     *
     * [모노리틱 변경]
     * findFirst5ByManagersContainingAndClosed(Account, boolean)
     * → findFirst5ByManagerIdsContainingAndClosed(Long, boolean)
     *
     * Spring Data JPA 는 @ElementCollection 컬렉션에도 Containing 키워드를 지원한다.
     * 생성되는 쿼리: WHERE :accountId IN (SELECT account_id FROM study_manager_ids WHERE study_id = s.id) AND s.closed = false
     */
    List<Study> findFirst5ByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여 중인 활동 중인 스터디 5개를 최신순으로 조회한다.
     *
     * [모노리틱 변경]
     * findFirst5ByMembersContainingAndClosed(Account, boolean)
     * → findFirst5ByMemberIdsContainingAndClosed(Long, boolean)
     */
    List<Study> findFirst5ByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 공개 상태이며 마감되지 않은 최신 스터디 9개를 조회한다.
     * 메인 페이지 최신 스터디 목록에 사용한다.
     */
    List<Study> findFirst9ByPublishedAndClosedOrderByPublishedDateTimeDesc(
            boolean published, boolean closed);

    // ============================
    // 목록 조회 (프로필 페이지용)
    // ============================

    /**
     * 특정 사용자가 관리자로 참여 중인 활동 중인 스터디 전체를 최신순으로 조회한다.
     * 대시보드(5개 제한)와 달리 프로필 페이지에서는 전체를 보여준다.
     */
    List<Study> findByManagerIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);

    /**
     * 특정 사용자가 멤버로 참여 중인 활동 중인 스터디 전체를 최신순으로 조회한다.
     */
    List<Study> findByMemberIdsContainingAndClosedOrderByPublishedDateTimeDesc(
            Long accountId, boolean closed);
}