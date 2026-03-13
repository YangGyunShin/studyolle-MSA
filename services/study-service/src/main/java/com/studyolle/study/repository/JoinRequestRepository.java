package com.studyolle.study.repository;

import com.studyolle.study.entity.JoinRequest;
import com.studyolle.study.entity.JoinRequestStatus;
import com.studyolle.study.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JoinRequestRepository
 *
 * =============================================
 * [모노리틱과의 변경사항]
 * =============================================
 *
 * [제거]
 * existsByStudyAndAccountAndStatus(Study, Account, JoinRequestStatus)
 * → existsByStudyAndAccountIdAndStatus(Study, Long, JoinRequestStatus)
 *
 * [제거]
 * @EntityGraph(attributePaths = "account")
 * Account 엔티티가 없으므로 fetch join 이 불필요하다.
 * accountNickname 을 JoinRequest 에 비정규화했으므로
 * 신청자 닉네임을 표시할 때 추가 쿼리가 발생하지 않는다.
 */
@Transactional(readOnly = true)
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    /**
     * 특정 스터디에 대한 특정 사용자의 대기 중인 신청이 있는지 확인한다.
     *
     * [모노리틱 변경]
     * existsByStudyAndAccountAndStatus(Study, Account, JoinRequestStatus)
     * → existsByStudyAndAccountIdAndStatus(Study, Long, JoinRequestStatus)
     *
     * [사용처]
     * - StudyService.createJoinRequest(): 중복 신청 방지
     * - StudyService.hasPendingJoinRequest(): 뷰에서 "신청 중" 버튼 표시 여부
     */
    boolean existsByStudyAndAccountIdAndStatus(Study study, Long accountId,
                                               JoinRequestStatus status);

    /**
     * 특정 스터디의 대기 중인 신청 목록을 신청 시각 오름차순으로 조회한다.
     *
     * [모노리틱 변경]
     * @EntityGraph(attributePaths = "account") 제거
     * account 대신 accountNickname 비정규화 필드를 사용하므로 추가 JOIN 불필요.
     */
    List<JoinRequest> findByStudyAndStatusOrderByRequestedAtAsc(Study study,
                                                                JoinRequestStatus status);
}