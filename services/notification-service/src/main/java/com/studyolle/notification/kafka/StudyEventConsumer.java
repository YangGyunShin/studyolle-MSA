package com.studyolle.notification.kafka;

import com.studyolle.notification.entity.NotificationType;
import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "study-events", groupId = "notification-service")
    public void consume(
            @Payload StudyEventDto event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka] 수신: partition={}, offset={}, eventType={}", partition, offset, event.getEventType());

        // 중복 이벤트 방지 — studyPath + occurredAt 조합으로 유니크 키 생성
        String dedupKey = "study:" + event.getStudyPath() + ":" + event.getOccurredAt();
        if (!notificationService.isFirstProcessing(dedupKey)) {
            log.warn("[Kafka] 중복 이벤트 무시: {}", dedupKey);
            ack.acknowledge();
            return;
        }

        try {
            String message = switch (event.getEventType()) {
                case "STUDY_CREATED" -> "[" + event.getStudyTitle() + "] 스터디가 생성됐습니다.";
                case "STUDY_PUBLISHED" -> "[" + event.getStudyTitle() + "] 스터디가 공개됐습니다.";
                case "RECRUITING_STARTED" -> "[" + event.getStudyTitle() + "] 모집을 시작했습니다.";
                case "RECRUITING_STOPPED" -> "[" + event.getStudyTitle() + "] 모집이 종료됐습니다.";
                default -> null;
            };

            if (message != null) {
                notificationService.createNotification(
                        event.getTriggeredByAccountId(),
                        message,
                        "/study/" + event.getStudyPath(),
                        NotificationType.STUDY
                );
            }
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka] 알림 처리 실패: eventType={}, error={}",
                    event.getEventType(), e.getMessage());
        }
    }
}