package com.studyolle.notification.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * study-service 가 Kafka 로 발행하는 스터디 이벤트 DTO.
 * study-service 의 StudyEventDto 와 필드 구조가 동일해야 JSON 역직렬화가 성공한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyEventDto {

    private String eventType;            // STUDY_CREATED, STUDY_PUBLISHED, RECRUITING_STARTED, RECRUITING_STOPPED
    private String studyPath;
    private String studyTitle;
    private Long triggeredByAccountId;
    private LocalDateTime occurredAt;
}