package com.studyolle.study.entity;

/**
 * 스터디 가입 신청 상태
 *
 * [모노리틱과 동일, 100% 복사]
 *
 * 상태 전이:
 * PENDING → APPROVED (관리자 승인 → Study.addMember() 호출)
 * PENDING → REJECTED (관리자 거절 → 멤버 추가 안 함)
 */
public enum JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}