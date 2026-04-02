package com.studyolle.study.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 스터디 이벤트를 Kafka 로 발행하는 Producer.
 *
 * KafkaTemplate.send(topic, key, value) 에서
 *   topic : "study-events"
 *   key   : studyPath  (같은 스터디 → 같은 Partition → 순서 보장)
 *   value : StudyEventDto (JSON 직렬화)
 *
 * whenComplete 로 비동기 결과를 처리한다.
 * 전송 실패 시 로그만 남기고 예외를 던지지 않는다.
 * 알림은 부가 기능이므로 실패해도 스터디 핵심 로직에 영향이 없어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyKafkaProducer {

    private static final String TOPIC = "study-events";

    private final KafkaTemplate<String, StudyEventDto> kafkaTemplate;

    public void sendStudyEvent(StudyEventDto event) {
        kafkaTemplate.send(TOPIC, event.getStudyPath(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 전송 실패: eventType={}, studyPath={}, error={}",
                                event.getEventType(),
                                event.getStudyPath(),
                                ex.getMessage());
                    } else {
                        log.info("[Kafka] 전송 성공: eventType={}, partition={}, offset={}",
                                event.getEventType(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
