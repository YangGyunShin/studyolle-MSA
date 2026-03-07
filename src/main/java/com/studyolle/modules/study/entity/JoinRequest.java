package com.studyolle.modules.study.entity;

import com.studyolle.modules.account.entity.Account;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 스터디 가입 신청 엔티티
 *
 * 승인제(APPROVAL_REQUIRED) 스터디에서 사용자가 가입을 신청하면 생성됩니다.
 * 관리자가 승인(APPROVED) 또는 거절(REJECTED)할 때까지 PENDING 상태를 유지합니다.
 *
 * =============================================
 * Enrollment 엔티티와의 대응 관계
 * =============================================
 *
 * Enrollment (모임 참가 신청)과 구조적으로 유사하지만, 비즈니스 목적이 다릅니다:
 *
 *   Enrollment 필드     -> JoinRequest 대응     -> 역할
 *   event               -> study                -> 어디에 신청했는지
 *   account             -> account              -> 누가 신청했는지
 *   enrolledAt          -> requestedAt          -> 언제 신청했는지
 *   accepted (boolean)  -> status (Enum)        -> 현재 상태
 *   attended            -> (없음)               -> 모임 전용 (출석 체크)
 *   (없음)              -> processedAt          -> 승인/거절 처리 시각
 *
 * =============================================
 * 상태 전이
 * =============================================
 *
 * PENDING -> APPROVED (관리자 승인 -> Study.addMember() 호출)
 * PENDING -> REJECTED (관리자 거절 -> 멤버 추가 안 함)
 *
 * 상태 전이 메서드(approve, reject)를 엔티티에 두는 이유:
 * - Study.publish(), Study.close(), Study.startRecruit()과 같은 패턴
 * - "내 상태를 바꾸는 것"은 엔티티의 책임
 * - 서비스에서 setter를 나열하면 상태 전이 로직이 흩어짐
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class JoinRequest {

    @Id
    @GeneratedValue
    private Long id;

    /** 가입을 신청한 사용자 - JoinRequest : Account = N : 1 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    /** 가입 대상 스터디 - JoinRequest : Study = N : 1 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Study study;

    /**
     * 신청 상태 (PENDING / APPROVED / REJECTED)
     *
     * @Enumerated(EnumType.STRING): DB에 문자열("PENDING")로 저장
     * EnumType.ORDINAL(기본값)은 순서 번호(0, 1, 2)로 저장되어
     * Enum 순서 변경 시 기존 데이터와 불일치 위험이 있음
     * (Notification.notificationType과 동일한 이유)
     */
    @Enumerated(EnumType.STRING)
    private JoinRequestStatus status;

    /** 신청 일시 */
    private LocalDateTime requestedAt;

    /** 처리(승인/거절) 일시 - PENDING 상태에서는 null */
    private LocalDateTime processedAt;

    // ============================
    // 팩토리 메서드
    // ============================

    /**
     * 새 가입 신청을 생성합니다.
     * 초기 상태는 PENDING이며, 신청 시각이 자동으로 기록됩니다.
     *
     * 생성자 대신 팩토리 메서드를 사용하는 이유:
     * - 초기 상태(PENDING)와 신청 시각(now)이 항상 함께 설정되어야 하는데,
     *   이 조합이 빠지면 안 되므로 메서드로 강제합니다.
     */
    public static JoinRequest createRequest(Account account, Study study) {
        JoinRequest request = new JoinRequest();
        request.setAccount(account);
        request.setStudy(study);
        request.setStatus(JoinRequestStatus.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        return request;
    }

    // ============================
    // 상태 전이 메서드
    // ============================

    /** 승인 처리: 상태를 APPROVED로 변경하고 처리 시각을 기록 */
    public void approve() {
        this.status = JoinRequestStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    /** 거절 처리: 상태를 REJECTED로 변경하고 처리 시각을 기록 */
    public void reject() {
        this.status = JoinRequestStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }

    /** 현재 대기 중인 상태인지 확인 (이미 처리된 신청의 중복 처리 방지용) */
    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }
}