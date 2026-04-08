package com.studyolle.notification.rabbitmq;

import com.studyolle.notification.config.RabbitMQConfig;
import com.studyolle.notification.entity.NotificationType;
import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consume(EnrollmentEventDto event) {

        log.info("[RabbitMQ] 수신: eventType={}, enrollmentAccountId={}", event.getEventType(), event.getEnrollmentAccountId());

        // 중복 이벤트 방지 — eventId + eventType 조합으로 유니크 키 생성
        String dedupKey = "enrollment:" + event.getEventId() + ":" + event.getEventType();
        if (!notificationService.isFirstProcessing(dedupKey)) {
            log.warn("[RabbitMQ] 중복 이벤트 무시: {}", dedupKey);
            return;
        }

        String message = switch (event.getEventType()) {
            case "enrollment.accepted" -> "[" + event.getEventTitle() + "] 모임 신청이 수락됐습니다.";
            case "enrollment.rejected" -> "[" + event.getEventTitle() + "] 모임 신청이 거절됐습니다.";
            case "enrollment.applied" -> "[" + event.getEventTitle() + "] 모임 신청이 완료됐습니다.";
            case "enrollment.attendance" -> "[" + event.getEventTitle() + "] 출석이 확인됐습니다.";
            default -> null;
        };

        if (message != null) {
            notificationService.createNotification(
                    event.getEnrollmentAccountId(),
                    message,
                    "/study/" + event.getStudyPath() + "/events/" + event.getEventId(),
                    NotificationType.ENROLLMENT
            );
        }
    }
}