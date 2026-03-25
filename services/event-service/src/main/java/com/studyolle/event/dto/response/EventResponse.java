package com.studyolle.event.dto.response;

import com.studyolle.event.entity.Event;
import com.studyolle.event.entity.EventType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private EventType eventType;
    private int limitOfEnrollments;
    private LocalDateTime createdDateTime;
    private LocalDateTime endEnrollmentDateTime;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String studyPath;
    private Long createdByAccountId;

    // 참가 신청 요약
    private long acceptedCount;
    private int remainSpots;
    private List<EnrollmentResponse> enrollments;

    public static EventResponse from(Event event) {
        EventResponse r = new EventResponse();
        r.setId(event.getId());
        r.setTitle(event.getTitle());
        r.setDescription(event.getDescription());
        r.setEventType(event.getEventType());
        r.setLimitOfEnrollments(event.getLimitOfEnrollments());
        r.setCreatedDateTime(event.getCreatedDateTime());
        r.setEndEnrollmentDateTime(event.getEndEnrollmentDateTime());
        r.setStartDateTime(event.getStartDateTime());
        r.setEndDateTime(event.getEndDateTime());
        r.setStudyPath(event.getStudyPath());
        r.setCreatedByAccountId(event.getCreatedByAccountId());
        r.setAcceptedCount(event.getNumberOfAcceptedEnrollments());
        r.setRemainSpots(event.numberOfRemainSpots());
        r.setEnrollments(event.getEnrollments().stream()
                .map(EnrollmentResponse::from).toList());
        return r;
    }
}