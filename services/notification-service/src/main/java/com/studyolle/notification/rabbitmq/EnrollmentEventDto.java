package com.studyolle.notification.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * event-service 가 RabbitMQ 로 발행하는 모임 신청 이벤트 DTO.
 * event-service 의 EnrollmentEventDto 와 필드 구조가 동일해야 JSON 역직렬화가 성공한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentEventDto {

    private String eventType;            // enrollment.accepted / rejected / applied / attendance
    private Long eventId;
    private String eventTitle;
    private String studyPath;
    private Long enrollmentAccountId;  // 신청자 ID (알림 수신 대상)
    private Long managedByAccountId;
    private LocalDateTime occurredAt;
}