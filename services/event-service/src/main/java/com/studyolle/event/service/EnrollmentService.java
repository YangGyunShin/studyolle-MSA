package com.studyolle.event.service;

import com.studyolle.event.entity.Enrollment;
import com.studyolle.event.entity.Event;
import com.studyolle.event.repository.EnrollmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EventService eventService;

    public void enroll(Long eventId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);

        if (!enrollmentRepository.existsByEventAndAccountId(event, accountId)) {
            Enrollment enrollment = Enrollment.builder()
                    .accountId(accountId)
                    .enrolledAt(LocalDateTime.now())
                    .accepted(event.isAbleToAcceptWaitingEnrollment())
                    .build();
            event.addEnrollment(enrollment);
            enrollmentRepository.save(enrollment);
        }
    }

    public void cancelEnrollment(Long eventId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);
        Enrollment enrollment = enrollmentRepository.findByEventAndAccountId(event, accountId)
                .orElseThrow(() -> new IllegalArgumentException("참가 신청 내역이 없습니다."));

        if (!enrollment.isAttended()) {
            event.removeEnrollment(enrollment);
            enrollmentRepository.delete(enrollment);
            event.acceptNextWaitingEnrollment();
        }
    }

    public void acceptEnrollment(Long eventId, Long enrollmentId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);
        eventService.validateManager(event, accountId);
        Enrollment enrollment = findEnrollment(enrollmentId);
        event.accept(enrollment);
    }

    public void rejectEnrollment(Long eventId, Long enrollmentId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);
        eventService.validateManager(event, accountId);
        Enrollment enrollment = findEnrollment(enrollmentId);
        event.reject(enrollment);
    }

    public void checkIn(Long enrollmentId, Long accountId) {
        Enrollment enrollment = findEnrollment(enrollmentId);
        eventService.validateManager(enrollment.getEvent(), accountId);
        enrollment.setAttended(true);
    }

    public void cancelCheckIn(Long enrollmentId, Long accountId) {
        Enrollment enrollment = findEnrollment(enrollmentId);
        eventService.validateManager(enrollment.getEvent(), accountId);
        enrollment.setAttended(false);
    }

    private Enrollment findEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 참가 신청입니다."));
    }
}