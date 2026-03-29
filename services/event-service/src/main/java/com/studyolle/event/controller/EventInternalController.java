package com.studyolle.event.controller;

import com.studyolle.event.dto.response.EventResponse;
import com.studyolle.event.entity.Enrollment;
import com.studyolle.event.repository.EnrollmentRepository;
import com.studyolle.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventInternalController {

    private final EventService eventService;
    private final EnrollmentRepository enrollmentRepository;

    @GetMapping("/internal/events/by-study/{studyPath}")
    public ResponseEntity<List<EventResponse>> getEventsByStudy(@PathVariable String studyPath) {
        return ResponseEntity.ok(eventService.getEventsByStudy(studyPath)
                .stream()
                .map(EventResponse::from)
                .toList()
        );
    }

    @GetMapping("/internal/events/calendar")
    public ResponseEntity<List<EventResponse>> getCalendarEvents(@RequestParam Long accountId) {
        return ResponseEntity.ok(
                enrollmentRepository.findByAccountId(accountId)
                        .stream()
                        .map(Enrollment::getEvent)
                        .map(EventResponse::from)
                        .toList()
        );
    }
}