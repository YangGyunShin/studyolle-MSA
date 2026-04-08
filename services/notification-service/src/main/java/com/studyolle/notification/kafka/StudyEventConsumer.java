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

/**
 * Kafka "study-events" Topic 을 구독하는 Consumer.
 *
 * =============================================
 * 전체 흐름
 * =============================================
 *
 * study-service (Producer)
 *      │ KafkaTemplate.send("study-events", studyPath, event)
 *      ▼
 * Kafka "study-events" Topic
 *      │ groupId="notification-service" 로 메시지 수신
 *      ▼
 * StudyEventConsumer.consume()
 *      │ 중복 방지 체크 → 알림 생성
 *      ▼
 * NotificationService.createNotification()
 *      │ PostgreSQL 저장 + Redis 카운터 +1
 *
 * =============================================
 * @KafkaListener 동작 방식
 * =============================================
 *
 * @KafkaListener(topics = "study-events", groupId = "notification-service")
 *
 * topics    : 구독할 Topic 이름. 여러 개면 {"topic1", "topic2"} 로 지정.
 * groupId   : Consumer Group ID. 같은 groupId 를 가진 Consumer 들은
 *             Topic 의 Partition 을 나눠서 처리한다.
 *             다른 groupId 를 가진 Consumer 는 같은 Topic 을 독립적으로 읽는다.
 *             예: analytics-service 가 같은 "study-events" Topic 을 다른 groupId 로 읽으면
 *                 notification-service 와 무관하게 독립적으로 모든 메시지를 수신한다.
 *
 * =============================================
 * @Payload, @Header 사용 이유
 * =============================================
 *
 * @Payload StudyEventDto event
 *   메시지 본문(JSON)을 StudyEventDto 로 역직렬화한다.
 *   application.yml 의 spring.json.use.type.headers=false 설정과 함께
 *   JSON 을 지정된 클래스로 역직렬화한다.
 *
 * @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
 *   메시지가 도착한 Partition 번호. 디버깅 및 로깅용.
 *   같은 studyPath 의 메시지는 항상 같은 Partition 에서 오므로 순서가 보장된다.
 *
 * @Header(KafkaHeaders.OFFSET) long offset
 *   Partition 내 메시지의 순번. 디버깅 및 로깅용.
 *   문제 발생 시 특정 offset 의 메시지를 재처리할 때 사용한다.
 *
 * Acknowledgment ack
 *   수동 Offset 커밋을 위한 객체.
 *   ack.acknowledge() 호출 시 "이 메시지를 처리했다" 고 Kafka 에 알린다.
 *   호출하지 않으면 서비스 재시작 시 이 메시지부터 다시 읽는다.
 *
 * =============================================
 * 수동 ACK (manual_immediate) 를 사용하는 이유
 * =============================================
 *
 * 자동 커밋(enable-auto-commit: true) 의 문제:
 *   메시지를 받자마자 Offset 을 커밋한다.
 *   처리 도중 서버가 죽으면 → Offset 은 커밋됐지만 처리는 안 됨 → 알림 유실!
 *
 * 수동 커밋(enable-auto-commit: false + ack.acknowledge()) 의 장점:
 *   처리 완료 후에만 Offset 을 커밋한다.
 *   처리 도중 서버가 죽으면 → Offset 미커밋 → 재시작 시 이 메시지부터 다시 처리.
 *   at-least-once 보장: 최소 한 번은 반드시 처리된다.
 *   (중복 처리 가능성은 있으나 isFirstProcessing() 으로 방어)
 *
 * =============================================
 * 중복 이벤트 방지 설계
 * =============================================
 *
 * Kafka 는 네트워크 오류 등으로 같은 메시지를 두 번 전달할 수 있다.
 * 그러면 같은 알림이 두 번 생성된다.
 *
 * 방지 방법: Redis SETNX(Set if Not eXists)
 *   isFirstProcessing("study:{studyPath}:{occurredAt}") 호출 시
 *   Redis 에 이 키가 없으면 → 저장 + true 반환 (처음 처리)
 *   Redis 에 이 키가 있으면 → 저장 안 함 + false 반환 (중복, 무시)
 *   TTL 1일: 하루가 지나면 키가 삭제되어 재처리 가능.
 */
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

        log.info("[Kafka] 수신: partition={}, offset={}, eventType={}",
                partition, offset, event.getEventType());

        // 중복 이벤트 방지 — studyPath + occurredAt 조합으로 유니크 키 생성
        // 같은 스터디에서 같은 시각에 같은 이벤트가 두 번 오면 두 번째는 무시한다.
        String dedupKey = "study:" + event.getStudyPath() + ":" + event.getOccurredAt();
        if (!notificationService.isFirstProcessing(dedupKey)) {
            log.warn("[Kafka] 중복 이벤트 무시: {}", dedupKey);
            ack.acknowledge();  // 중복이어도 Offset 은 커밋해야 한다 (재처리 방지)
            return;
        }

        try {
            // eventType 에 따라 알림 메시지 내용 결정
            String message = switch (event.getEventType()) {
                case "STUDY_CREATED"      -> "[" + event.getStudyTitle() + "] 스터디가 생성됐습니다.";
                case "STUDY_PUBLISHED"    -> "[" + event.getStudyTitle() + "] 스터디가 공개됐습니다.";
                case "RECRUITING_STARTED" -> "[" + event.getStudyTitle() + "] 모집을 시작했습니다.";
                case "RECRUITING_STOPPED" -> "[" + event.getStudyTitle() + "] 모집이 종료됐습니다.";
                default -> null;  // 알 수 없는 이벤트 타입은 알림 생성 안 함
            };

            if (message != null) {
                notificationService.createNotification(
                        event.getTriggeredByAccountId(),  // 알림 수신 대상
                        message,
                        "/study/" + event.getStudyPath(), // 클릭 시 이동할 링크
                        NotificationType.STUDY
                );
            }

            ack.acknowledge();  // 처리 완료 → Offset 커밋

        } catch (Exception e) {
            // 처리 실패 시 ack 를 호출하지 않는다.
            // 서비스 재시작 시 이 메시지부터 다시 읽어 재처리를 시도한다.
            log.error("[Kafka] 알림 처리 실패: eventType={}, error={}",
                    event.getEventType(), e.getMessage());
        }
    }
}
