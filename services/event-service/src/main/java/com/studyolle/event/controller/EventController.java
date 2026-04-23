package com.studyolle.event.controller;

import com.studyolle.event.common.EmailVerifiedGuard;
import com.studyolle.event.dto.request.CreateEventRequest;
import com.studyolle.event.dto.request.UpdateEventRequest;
import com.studyolle.event.dto.response.CommonApiResponse;
import com.studyolle.event.dto.response.EventResponse;
import com.studyolle.event.service.EnrollmentService;
import com.studyolle.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EnrollmentService enrollmentService;

    @PostMapping("/api/studies/{path}/events")
    public ResponseEntity<CommonApiResponse<EventResponse>> createEvent(
            @PathVariable String path,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody CreateEventRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        EventResponse response = EventResponse.from(eventService.createEvent(path, accountId, request));
        return ResponseEntity.ok(CommonApiResponse.ok(response));
    }

    @GetMapping("/api/studies/{path}/events")
    public ResponseEntity<CommonApiResponse<List<EventResponse>>> getEvents(
            @PathVariable String path) {

        List<EventResponse> events = eventService.getEventsByStudy(path)
                .stream().map(EventResponse::from).toList();
        return ResponseEntity.ok(CommonApiResponse.ok(events));
    }

    @GetMapping("/api/studies/{path}/events/{eventId}")
    public ResponseEntity<CommonApiResponse<EventResponse>> getEvent(
            @PathVariable Long eventId) {

        EventResponse response = EventResponse.from(eventService.getEventWithEnrollments(eventId));
        return ResponseEntity.ok(CommonApiResponse.ok(response));
    }

    @PutMapping("/api/studies/{path}/events/{eventId}")
    public ResponseEntity<CommonApiResponse<Void>> updateEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified,
            @Valid @RequestBody UpdateEventRequest request) {

        EmailVerifiedGuard.require(emailVerified);

        eventService.updateEvent(eventId, accountId, request);
        return ResponseEntity.ok(CommonApiResponse.ok("모임이 수정되었습니다."));
    }

    @DeleteMapping("/api/studies/{path}/events/{eventId}")
    public ResponseEntity<CommonApiResponse<Void>> deleteEvent(
            @PathVariable Long eventId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        eventService.deleteEvent(eventId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("모임이 삭제되었습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/enroll")
    public ResponseEntity<CommonApiResponse<Void>> enroll(
            @PathVariable Long eventId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.enroll(eventId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("참가 신청이 완료되었습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/leave")
    public ResponseEntity<CommonApiResponse<Void>> leave(
            @PathVariable Long eventId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.cancelEnrollment(eventId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("참가 신청이 취소되었습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/enrollments/{enrollmentId}/accept")
    public ResponseEntity<CommonApiResponse<Void>> acceptEnrollment(
            @PathVariable Long eventId,
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.acceptEnrollment(eventId, enrollmentId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("참가 신청을 승인했습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/enrollments/{enrollmentId}/reject")
    public ResponseEntity<CommonApiResponse<Void>> rejectEnrollment(
            @PathVariable Long eventId,
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.rejectEnrollment(eventId, enrollmentId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("참가 신청을 거절했습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/enrollments/{enrollmentId}/checkin")
    public ResponseEntity<CommonApiResponse<Void>> checkIn(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.checkIn(enrollmentId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("출석 체크가 완료되었습니다."));
    }

    @PostMapping("/api/studies/{path}/events/{eventId}/enrollments/{enrollmentId}/cancel-checkin")
    public ResponseEntity<CommonApiResponse<Void>> cancelCheckIn(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-Account-Id") Long accountId,
            @RequestHeader("X-Account-Email-Verified") Boolean emailVerified) {

        EmailVerifiedGuard.require(emailVerified);

        enrollmentService.cancelCheckIn(enrollmentId, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("출석 체크가 취소되었습니다."));
    }
}