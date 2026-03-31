package com.studyolle.study.client.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * event-service 의 EventResponse 를 study-service 에서 받기 위한 DTO.
 *
 * event-service 가 반환하는 JSON 필드명과 정확히 일치해야
 * Feign 의 Jackson 역직렬화가 자동으로 동작한다.
 *
 * 대시보드에서 필요한 핵심 필드만 포함한다.
 * (description, enrollments 등 무거운 필드는 제외)
 */
@Data
public class EventSummaryDto {

    private Long id;
    private String title;
    private String studyPath;           // 어느 스터디의 모임인지 식별 (studyId 매핑에 사용)
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private LocalDateTime endEnrollmentDateTime;
    private long acceptedCount;
    private int remainSpots;
}