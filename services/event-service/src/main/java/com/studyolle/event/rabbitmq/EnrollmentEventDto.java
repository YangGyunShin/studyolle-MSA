package com.studyolle.event.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RabbitMQ 로 발행하는 모임 신청 이벤트 DTO.
 *
 * event-service → RabbitMQ "event.notification" Exchange
 *              → "enrollment.queue" → notification-service
 *
 * Routing Key 가 곧 eventType:
 *   enrollment.accepted   신청 수락
 *   enrollment.rejected   신청 거절
 *   enrollment.applied    선착순 신청 완료
 *   enrollment.attendance 출석 체크
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentEventDto {

    private String eventType;            // Routing Key 와 동일한 값
    private Long   eventId;              // 모임 ID
    private String eventTitle;           // 모임 제목
    private String studyPath;            // 스터디 경로 (알림 링크 생성용)
    private Long   enrollmentAccountId;  // 신청자 ID (알림 수신 대상)
    private Long   managedByAccountId;   // 수락/거절 처리한 관리자 ID
    private LocalDateTime occurredAt;
}