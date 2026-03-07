package com.studyolle.modules.study.entity;

/**
 * 스터디 가입 방식을 정의하는 Enum
 *
 * =============================================
 * EventType과의 차이
 * =============================================
 *
 * EventType (FCFS / CONFIRMATIVE):
 * - 모임(Event)의 참가 방식 -> "정원 관리"가 핵심
 * - FCFS는 정원 내 자동 승인, 초과 시 대기
 * - CONFIRMATIVE는 관리자가 정원 내에서 선택
 *
 * JoinType (OPEN / APPROVAL_REQUIRED):
 * - 스터디(Study)의 가입 방식 -> "사람 선별"이 핵심
 * - OPEN은 승인 과정 자체가 없음 (즉시 가입, JoinRequest 생성 안 함)
 * - APPROVAL_REQUIRED는 JoinRequest를 통한 관리자 승인 필요
 *
 * Study 생성 시 기본값은 OPEN으로 설정되어 기존 동작과의 호환성을 유지합니다.
 */
public enum JoinType {
    OPEN, APPROVAL_REQUIRED
}