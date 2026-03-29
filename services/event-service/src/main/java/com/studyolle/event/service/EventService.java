package com.studyolle.event.service;

import com.studyolle.event.dto.request.CreateEventRequest;
import com.studyolle.event.dto.request.UpdateEventRequest;
import com.studyolle.event.entity.Event;
import com.studyolle.event.repository.EventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    public Event createEvent(String studyPath, Long accountId, CreateEventRequest req) {
        Event event = Event.builder()
                .studyPath(studyPath)
                .createdByAccountId(accountId)
                .createdDateTime(LocalDateTime.now())
                .title(req.getTitle())
                .description(req.getDescription())
                .eventType(req.getEventType())
                .limitOfEnrollments(req.getLimitOfEnrollments())
                .endEnrollmentDateTime(req.getEndEnrollmentDateTime())
                .startDateTime(req.getStartDateTime())
                .endDateTime(req.getEndDateTime())
                .build();
        return eventRepository.save(event);
    }

    public List<Event> getEventsByStudy(String studyPath) {
        return eventRepository.findByStudyPath(studyPath);
    }

    public Event getEventWithEnrollments(Long eventId) {
        return eventRepository.findWithEnrollmentsById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다."));
    }

    public void updateEvent(Long eventId, Long accountId, UpdateEventRequest req) {
        Event event = getEventWithEnrollments(eventId);
        validateManager(event, accountId);
        event.update(req);             // 도메인 메서드로 위임
        event.acceptWaitingList();     // 정원 증가 시 대기자 자동 승인
    }

    public void deleteEvent(Long eventId, Long accountId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 모임입니다."));
        validateManager(event, accountId);
        eventRepository.delete(event);
    }

    // EnrollmentService에서 접근 — package-private
    void validateManager(Event event, Long accountId) {
        if (!event.getCreatedByAccountId().equals(accountId)) {
            throw new IllegalStateException("모임 관리 권한이 없습니다.");
        }
    }
}