package com.studyolle.notification.rabbitmq;

import com.studyolle.notification.config.RabbitMQConfig;
import com.studyolle.notification.entity.NotificationType;
import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ "enrollment.queue" 를 구독하는 Consumer.
 *
 * =============================================
 * 전체 흐름
 * =============================================
 *
 * event-service (Producer)
 *      │ rabbitTemplate.convertAndSend(
 *      │     "event.notification", "enrollment.accepted", dto)
 *      ▼
 * RabbitMQ [event.notification Exchange]
 *      │ Routing Key "enrollment.accepted" → "enrollment.#" 패턴 매칭
 *      ▼
 * [enrollment.queue]
 *      │ @RabbitListener 가 자동으로 메시지 수신
 *      ▼
 * EnrollmentEventConsumer.consume()
 *      │ 중복 방지 체크 → 알림 생성
 *      ▼
 * NotificationService.createNotification()
 *      │ PostgreSQL 저장 + Redis 카운터 +1
 *
 * =============================================
 * @RabbitListener vs @KafkaListener 차이
 * =============================================
 *
 * @RabbitListener(queues = RabbitMQConfig.QUEUE)
 *   RabbitMQ 는 Push 방식이다. 브로커가 Consumer 에게 메시지를 밀어넣는다.
 *   메시지가 Queue 에 도착하면 브로커가 즉시 이 메서드를 호출한다.
 *   ACK 를 별도로 받지 않는다 (기본 AUTO ACK 모드).
 *   메서드가 정상 리턴하면 자동으로 ACK, 예외가 발생하면 자동으로 NACK 한다.
 *
 * @KafkaListener(topics = ...)
 *   Kafka 는 Pull 방식이다. Consumer 가 브로커에서 직접 메시지를 가져간다.
 *   Acknowledgment.acknowledge() 를 수동으로 호출해 Offset 을 커밋해야 한다.
 *
 * RabbitMQ 의 AUTO ACK 특성 덕분에 Kafka 보다 코드가 단순하다.
 * 메서드가 예외 없이 완료되면 메시지가 자동으로 Queue 에서 제거된다.
 *
 * =============================================
 * 중복 이벤트 방지 설계
 * =============================================
 *
 * RabbitMQ 도 네트워크 오류 등으로 같은 메시지를 두 번 전달할 수 있다.
 * 방지 방법: Redis SETNX(Set if Not eXists)
 *
 * 중복 방지 키: "enrollment:{eventId}:{eventType}"
 *   예: "enrollment:1:enrollment.accepted"
 *   eventId 와 eventType 조합이 같으면 동일한 이벤트로 판단한다.
 *   TTL 1일: 하루 후 키 자동 삭제 → 재처리 가능.
 *
 * StudyEventConsumer 와의 차이:
 *   Kafka 중복 방지 키: "study:{studyPath}:{occurredAt}"
 *   RabbitMQ 중복 방지 키: "enrollment:{eventId}:{eventType}"
 *   각 메시지 특성에 맞는 유니크 키를 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventConsumer {

    private final NotificationService notificationService;

    /**
     * enrollment.queue 에서 메시지를 수신하고 알림을 생성한다.
     *
     * @RabbitListener 는 RabbitMQConfig.QUEUE("enrollment.queue") 를 감시한다.
     * 메시지가 도착하면 Jackson2JsonMessageConverter 가 JSON → EnrollmentEventDto 로 자동 역직렬화한다.
     *
     * @param event JSON 에서 역직렬화된 EnrollmentEventDto
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consume(EnrollmentEventDto event) {

        log.info("[RabbitMQ] 수신: eventType={}, enrollmentAccountId={}",
                event.getEventType(), event.getEnrollmentAccountId());

        // 중복 이벤트 방지 — eventId + eventType 조합으로 유니크 키 생성
        // 같은 모임에 같은 타입의 이벤트가 두 번 오면 두 번째는 무시한다.
        String dedupKey = "enrollment:" + event.getEventId() + ":" + event.getEventType();
        if (!notificationService.isFirstProcessing(dedupKey)) {
            log.warn("[RabbitMQ] 중복 이벤트 무시: {}", dedupKey);
            return;  // RabbitMQ 는 정상 리턴이면 자동 ACK 이므로 메시지가 삭제된다.
        }

        // eventType 에 따라 알림 메시지 내용 결정
        String message = switch (event.getEventType()) {
            case "enrollment.accepted"   -> "[" + event.getEventTitle() + "] 모임 신청이 수락됐습니다.";
            case "enrollment.rejected"   -> "[" + event.getEventTitle() + "] 모임 신청이 거절됐습니다.";
            case "enrollment.applied"    -> "[" + event.getEventTitle() + "] 모임 신청이 완료됐습니다.";
            case "enrollment.attendance" -> "[" + event.getEventTitle() + "] 출석이 확인됐습니다.";
            default -> null;  // 알 수 없는 이벤트 타입은 알림 생성 안 함
        };

        if (message != null) {
            notificationService.createNotification(
                    event.getEnrollmentAccountId(),  // 신청자에게 알림
                    message,
                    "/study/" + event.getStudyPath() + "/events/" + event.getEventId(),
                    NotificationType.ENROLLMENT
            );
        }
        // 메서드가 정상 리턴되면 RabbitMQ 가 자동으로 ACK 하고 메시지를 Queue 에서 제거한다.
    }
}
