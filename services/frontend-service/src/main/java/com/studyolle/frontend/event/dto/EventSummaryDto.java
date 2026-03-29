package com.studyolle.frontend.event.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * event-service 의 EventResponse JSON 을 역직렬화하는 프론트엔드 DTO.
 * EventResponse 필드명과 정확히 일치해야 Jackson 이 자동 매핑한다.
 *
 * 사용처:
 *  - study/view.html  newEvents / oldEvents 리스트 카드
 *  - event/view.html  모임 상세 페이지 전체
 *  - event/form.html  수정 폼 기본값 바인딩
 */
@Data
public class EventSummaryDto {

    private Long id;
    private String title;
    private String description;
    private String eventType;           // "FCFS" | "CONFIRMATIVE"
    private int limitOfEnrollments;
    private long acceptedCount;
    private int remainSpots;
    private LocalDateTime createdDateTime;
    private LocalDateTime endEnrollmentDateTime;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String studyPath;
    private Long createdByAccountId;
    private List<EnrollmentDto> enrollments;
}