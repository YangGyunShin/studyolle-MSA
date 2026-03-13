package com.studyolle.study.entity;

/**
 * 스터디 가입 방식
 *
 * - OPEN: 자유 가입 (즉시 멤버 등록, JoinRequest 생성 안 함)
 * - APPROVAL_REQUIRED: 승인제 (JoinRequest 생성 → 관리자 승인 → 멤버 등록)
 *
 * [사용처]
 * - Study.joinType 필드: 스터디별 가입 방식 저장
 * - StudyController.joinStudy(): 가입 방식에 따라 즉시가입 / 신청 분기
 */
public enum JoinType {
    OPEN,
    APPROVAL_REQUIRED
}