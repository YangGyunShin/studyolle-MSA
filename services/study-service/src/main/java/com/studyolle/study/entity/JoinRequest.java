package com.studyolle.study.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 스터디 가입 신청 엔티티
 *
 * =============================================
 * [모노리틱과의 변경사항]
 * =============================================
 *
 * [제거]
 * @ManyToOne Account account  →  Long accountId
 *
 * Account 엔티티를 직접 참조하면 account-service DB 에 대한 FK 가 생겨
 * 서비스 간 DB 분리 원칙에 위배된다.
 * account-service 의 Account.id 를 Long 으로만 저장한다.
 *
 * [추가]
 * String accountNickname  →  비정규화 필드
 * 알림 메시지 생성 시 (예: "홍길동님의 가입 신청") account-service 를
 * 매번 호출하지 않아도 되도록 신청 시점의 닉네임을 함께 저장한다.
 *
 * =============================================
 * Enrollment 엔티티와의 대응 관계 (모노리틱 참조)
 * =============================================
 *
 * event-service 의 Enrollment 와 구조적으로 유사하지만 목적이 다르다.
 * Enrollment 는 "모임 참가 신청", JoinRequest 는 "스터디 가입 신청"이다.
 *
 * =============================================
 * 상태 전이 — 모노리틱과 동일
 * =============================================
 *
 * PENDING → APPROVED (관리자 승인 → Study.addMember() 호출)
 * PENDING → REJECTED (관리자 거절 → 멤버 추가 안 함)
 *
 * 상태 전이를 엔티티 메서드로 캡슐화하는 이유:
 * Study.publish(), Study.close() 와 동일한 패턴.
 * "내 상태를 바꾸는 것"은 엔티티의 책임이다.
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

    /**
     * 가입을 신청한 사용자 ID (account-service 의 Account.id)
     *
     * [모노리틱 변경]
     * @ManyToOne Account account → Long accountId
     */
    private Long accountId;

    /**
     * 신청자 닉네임 (비정규화).
     *
     * [추가 이유]
     * Phase 5 알림 기능에서 "홍길동님의 가입 신청" 메시지를 만들 때
     * account-service 를 호출하지 않아도 된다.
     * 닉네임은 변경될 수 있지만, "신청 시점의 닉네임"이므로 허용한다.
     */
    private String accountNickname;

    /** 가입 대상 스터디 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Study study;

    /**
     * 신청 상태 (PENDING / APPROVED / REJECTED)
     *
     * @Enumerated(EnumType.STRING): DB 에 문자열("PENDING")로 저장.
     * ORDINAL 은 순서 변경 시 기존 데이터와 불일치 위험이 있으므로 STRING 사용.
     */
    @Enumerated(EnumType.STRING)
    private JoinRequestStatus status;

    /** 신청 일시 */
    private LocalDateTime requestedAt;

    /** 처리(승인/거절) 일시 — PENDING 상태에서는 null */
    private LocalDateTime processedAt;

    // ============================
    // 팩토리 메서드 — 모노리틱과 동일한 패턴, 파라미터 변경
    // ============================

    /**
     * 새 가입 신청을 생성한다.
     *
     * [모노리틱 변경]
     * createRequest(Account account, Study study)
     * → createRequest(Long accountId, String accountNickname, Study study)
     *
     * 생성자 대신 팩토리 메서드를 사용하는 이유:
     * 초기 상태(PENDING)와 신청 시각(now)이 항상 함께 설정되어야 하는데,
     * 이 조합이 빠지면 안 되므로 메서드로 강제한다.
     */
    public static JoinRequest createRequest(Long accountId, String accountNickname, Study study) {
        JoinRequest request = new JoinRequest();
        request.setAccountId(accountId);
        request.setAccountNickname(accountNickname);
        request.setStudy(study);
        request.setStatus(JoinRequestStatus.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        return request;
    }

    // ============================
    // 상태 전이 메서드 — 모노리틱과 완전히 동일
    // ============================

    /** 승인 처리: 상태를 APPROVED 로 변경하고 처리 시각을 기록한다. */
    public void approve() {
        this.status = JoinRequestStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    /** 거절 처리: 상태를 REJECTED 로 변경하고 처리 시각을 기록한다. */
    public void reject() {
        this.status = JoinRequestStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }

    /** 현재 대기 중인 상태인지 확인한다 (이미 처리된 신청의 중복 처리 방지용). */
    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }
}