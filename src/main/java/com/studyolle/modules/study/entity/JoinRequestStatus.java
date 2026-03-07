package com.studyolle.modules.study.entity;

/**
 * 가입 신청 상태를 정의하는 Enum
 *
 * =============================================
 * Enrollment.accepted (boolean)와의 차이
 * =============================================
 *
 * Enrollment는 accepted가 boolean이다.
 * 모임 참가는 "승인됨 / 아직 안됨" 두 가지면 충분하기 때문이다.
 * 거절 개념이 없고, 대기 중이다가 자리가 나면 자동 승인되는 구조이기 때문.
 *
 * 반면 JoinRequest는 3가지 상태가 필요하다:
 * - PENDING: 관리자 확인 대기 중
 * - APPROVED: 관리자가 승인 -> 멤버로 등록됨
 * - REJECTED: 관리자가 거절 -> 멤버 등록 안 됨
 *
 * boolean으로는 "거절"과 "아직 대기 중"을 구분할 수 없기 때문에 Enum으로 만든다.
 */
public enum JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}