package com.studyolle.modules.notification;

/**
 * 알림 유형을 정의하는 Enum
 *
 * - 알림의 발생 원인에 따라 3가지 유형으로 분류
 * - Notification 엔티티의 notificationType 필드에 사용됨
 * - 알림 목록 화면(notification/list.html)에서 탭별 분류 기준으로 활용
 *
 * =====================================================
 * [UI에서의 활용]
 *
 * NotificationController가 이 유형을 기준으로 알림을 분류하여
 * 웹 UI에서 탭별로 나눠서 보여줌:
 *
 *   - "새 스터디" 탭      -> STUDY_CREATED 알림만 표시
 *   - "참여중인 스터디" 탭 -> STUDY_UPDATED 알림만 표시
 *   - "모임 참가 신청" 탭  -> EVENT_ENROLLMENT 알림만 표시
 */
public enum NotificationType {

    // 새 스터디 생성 알림
    // -> 사용자가 관심 있는 태그/지역에 해당하는 새 스터디가 공개될 때 발생
    STUDY_CREATED,

    // 참여 중인 스터디 업데이트 알림
    // -> 사용자가 참여하고 있는 스터디의 내용이 변경될 때 발생
    STUDY_UPDATED,

    // 모임 참가 신청 관련 알림
    // -> 모임에 참가 신청 또는 참가 확정 등의 변경이 발생할 때 발생
    EVENT_ENROLLMENT,

    STUDY_NEW_BOARD

}