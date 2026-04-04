package com.studyolle.event.service;

import com.studyolle.event.entity.Enrollment;
import com.studyolle.event.entity.Event;
import com.studyolle.event.rabbitmq.EnrollmentEventDto;
import com.studyolle.event.rabbitmq.EnrollmentRabbitProducer;
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
    private final EnrollmentRabbitProducer enrollmentRabbitProducer;

    public void enroll(Long eventId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);

        if (!enrollmentRepository.existsByEventAndAccountId(event, accountId)) {
            Enrollment enrollment = Enrollment.builder()
                    .accountId(accountId)
                    .enrolledAt(LocalDateTime.now())
                    .accepted(event.isAbleToAcceptWaitingEnrollment())
                    .build();
            event.addEnrollment(enrollment);
            Enrollment saved = enrollmentRepository.save(enrollment);

            // 선착순 즉시 수락된 경우에만 알림 발송
            if (saved.isAccepted()) {
                enrollmentRabbitProducer.send(EnrollmentEventDto.builder()
                        .eventType("enrollment.applied")
                        .eventId(eventId)
                        .eventTitle(event.getTitle())
                        .studyPath(event.getStudyPath())
                        .enrollmentAccountId(accountId)
                        .occurredAt(LocalDateTime.now())
                        .build());
            }
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

        enrollmentRabbitProducer.send(EnrollmentEventDto.builder()
                .eventType("enrollment.accepted")
                .eventId(eventId)
                .eventTitle(event.getTitle())
                .studyPath(event.getStudyPath())
                .enrollmentAccountId(enrollment.getAccountId())  // 신청자에게 알림
                .managedByAccountId(accountId)                   // 처리한 관리자
                .occurredAt(LocalDateTime.now())
                .build());
    }

    public void rejectEnrollment(Long eventId, Long enrollmentId, Long accountId) {
        Event event = eventService.getEventWithEnrollments(eventId);
        eventService.validateManager(event, accountId);
        Enrollment enrollment = findEnrollment(enrollmentId);
        event.reject(enrollment);

        enrollmentRabbitProducer.send(EnrollmentEventDto.builder()
                .eventType("enrollment.rejected")
                .eventId(eventId)
                .eventTitle(event.getTitle())
                .studyPath(event.getStudyPath())
                .enrollmentAccountId(enrollment.getAccountId())  // 신청자에게 알림
                .managedByAccountId(accountId)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    public void checkIn(Long enrollmentId, Long accountId) {
        Enrollment enrollment = findEnrollment(enrollmentId);
        eventService.validateManager(enrollment.getEvent(), accountId);
        enrollment.setAttended(true);

        enrollmentRabbitProducer.send(EnrollmentEventDto.builder()
                .eventType("enrollment.attendance")
                .eventId(enrollment.getEvent().getId())
                .eventTitle(enrollment.getEvent().getTitle())
                .studyPath(enrollment.getEvent().getStudyPath())
                .enrollmentAccountId(enrollment.getAccountId())  // 출석 처리된 참가자에게 알림
                .managedByAccountId(accountId)
                .occurredAt(LocalDateTime.now())
                .build());
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