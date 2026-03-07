package com.studyolle.modules.study.repository;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.study.entity.JoinRequest;
import com.studyolle.modules.study.entity.JoinRequestStatus;
import com.studyolle.modules.study.entity.Study;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JoinRequest 엔티티 전용 JPA Repository
 *
 * =============================================
 * 메서드별 사용처
 * =============================================
 *
 * existsByStudyAndAccountAndStatus:
 * - StudyService.createJoinRequest() -> 중복 신청 방지
 * - StudyService.hasPendingJoinRequest() -> 뷰에서 "신청 중" 버튼 표시 여부 판단
 *
 * findByStudyAndStatusOrderByRequestedAtAsc:
 * - StudySettingsService.getPendingJoinRequests() -> 관리자용 대기 목록 조회
 *
 * =============================================
 * @EntityGraph 사용 이유
 * =============================================
 *
 * 신청 목록을 조회할 때 신청자의 닉네임, 프로필 이미지를 표시해야 합니다.
 * account가 LAZY 로딩이므로, @EntityGraph 없이 조회하면
 * 신청 5건일 때 account 조회 쿼리가 5번 추가 실행됩니다 (N+1 문제).
 * @EntityGraph(attributePaths = "account")로 한 번에 JOIN해서 가져옵니다.
 */
@Transactional(readOnly = true)
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    /**
     * 특정 스터디에 대한 특정 사용자의 대기 중인 신청이 있는지 확인합니다.
     *
     * 생성되는 쿼리:
     *   SELECT COUNT(*) > 0 FROM join_request
     *   WHERE study_id = :study AND account_id = :account AND status = :status
     */
    boolean existsByStudyAndAccountAndStatus(Study study, Account account, JoinRequestStatus status);

    /**
     * 특정 스터디의 상태별 신청 목록을 신청 시각 오름차순으로 조회합니다.
     * account를 함께 fetch join하여 N+1 문제를 방지합니다.
     */
    @EntityGraph(attributePaths = "account")
    List<JoinRequest> findByStudyAndStatusOrderByRequestedAtAsc(Study study, JoinRequestStatus status);
}