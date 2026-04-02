package com.studyolle.study.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka 로 발행하는 스터디 이벤트 DTO.
 *
 * study-service → Kafka "study-events" Topic → notification-service
 *
 * studyPath 를 Kafka 메시지 Key 로 사용하는 이유:
 * 같은 스터디의 이벤트는 항상 같은 Partition 으로 라우팅된다.
 * Partition 안에서는 순서가 보장되므로 스터디별 이벤트 순서가 유지된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyEventDto {

    // STUDY_CREATED, STUDY_PUBLISHED, RECRUITING_STARTED, RECRUITING_STOPPED
    private String eventType;

    private String studyPath; // Kafka Key 로 사용
    private String studyTitle;
    private Long triggeredByAccountId; // 이벤트를 발생시킨 사용자 ID
    private LocalDateTime occurredAt;
}