package com.studyolle.study.repository;

import com.studyolle.study.entity.JoinRequest;
import com.studyolle.study.entity.JoinRequestStatus;
import com.studyolle.study.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JoinRequest 엔티티의 데이터 접근 계층.
 *
 * =============================================
 * 비정규화(Denormalization) 설계
 * =============================================
 *
 * JoinRequest 에는 accountId(Long) 만 저장하는 대신
 * accountNickname(String) 필드도 함께 저장한다.
 * 이를 "비정규화"라고 한다 — 원칙적으로는 닉네임을 account-service 에서 조회해야 하지만,
 * 신청 목록을 보여줄 때마다 account-service 를 호출하면 네트워크 비용이 발생한다.
 *
 * 비정규화 덕분에 신청 목록 조회 시 accountNickname 을 별도 JOIN 이나
 * 외부 서비스 호출 없이 바로 읽을 수 있다.
 * (단, 사용자가 닉네임을 변경하면 JoinRequest 의 accountNickname 이 outdated 될 수 있다.
 *  이 트레이드오프는 현재 설계에서 허용한다.)
 */
@Transactional(readOnly = true)
public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    /**
     * 특정 스터디에 대한 특정 사용자의 특정 상태 신청이 존재하는지 확인한다.
     *
     * Spring Data JPA 메서드 이름 규칙에 따라 아래 쿼리가 자동 생성된다.
     *   SELECT COUNT(*) > 0 FROM join_request
     *   WHERE study_id = :study AND account_id = :accountId AND status = :status
     *
     * exists 쿼리는 조건에 맞는 행이 하나라도 있으면 즉시 true 를 반환하므로
     * 전체 카운트를 세는 것보다 효율적이다.
     *
     * 사용처:
     * - StudyService.createJoinRequest(): 같은 스터디에 중복 신청 방지
     * - StudyService.hasPendingJoinRequest(): 뷰에서 "신청 중" 버튼 표시 여부 판단
     *
     * @param study     대상 스터디
     * @param accountId 신청한 사용자의 ID
     * @param status    확인할 신청 상태 (예: PENDING)
     * @return 해당 조건의 신청이 존재하면 true
     */
    boolean existsByStudyAndAccountIdAndStatus(Study study, Long accountId,
                                               JoinRequestStatus status);

    /**
     * 특정 스터디의 특정 상태 신청 목록을 신청 시각 오름차순으로 조회한다.
     *
     * 생성되는 쿼리:
     *   SELECT * FROM join_request
     *   WHERE study_id = :study AND status = :status
     *   ORDER BY requested_at ASC
     *
     * 오름차순(ASC) 정렬 이유:
     * 먼저 신청한 사람이 목록 위에 표시되어야 공정한 처리 순서를 보장할 수 있다.
     *
     * JoinRequest 에 accountNickname 이 비정규화되어 있으므로
     * 신청자 정보를 표시하기 위한 추가 JOIN 이나 외부 서비스 호출이 불필요하다.
     *
     * 사용처: StudyService.getPendingJoinRequests() — 관리자용 가입 대기 목록 조회.
     *
     * @param study  대상 스터디
     * @param status 조회할 신청 상태 (예: PENDING)
     * @return 신청 시각 오름차순으로 정렬된 JoinRequest 목록
     */
    List<JoinRequest> findByStudyAndStatusOrderByRequestedAtAsc(Study study,
                                                                JoinRequestStatus status);
}