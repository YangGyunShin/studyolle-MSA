package com.studyolle.event.entity;

import com.studyolle.event.dto.request.UpdateEventRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter @Setter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String studyPath;

    @Column(nullable = false)
    private Long createdByAccountId;

    @Column(nullable = false)
    private String title;

    @Lob
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdDateTime;

    @Column(nullable = false)
    private LocalDateTime endEnrollmentDateTime;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    private Integer limitOfEnrollments;

    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Builder.Default
    @OneToMany(mappedBy = "event", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("enrolledAt")
    private List<Enrollment> enrollments = new ArrayList<>();

    // ====================================================================
    // 수정 도메인 메서드
    // ====================================================================

    public void update(UpdateEventRequest req) {
        this.title = req.getTitle();
        this.description = req.getDescription();
        this.eventType = req.getEventType();
        this.limitOfEnrollments = req.getLimitOfEnrollments();
        this.endEnrollmentDateTime = req.getEndEnrollmentDateTime();
        this.startDateTime = req.getStartDateTime();
        this.endDateTime = req.getEndDateTime();
    }

    // ====================================================================
    // 참가 신청 가능 여부 판단
    // ====================================================================

    public boolean isEnrollableFor(Long accountId) {
        return isNotClosed() && !isAttended(accountId) && !isAlreadyEnrolled(accountId);
    }

    public boolean isDisenrollableFor(Long accountId) {
        return isNotClosed() && !isAttended(accountId) && isAlreadyEnrolled(accountId);
    }

    private boolean isNotClosed() {
        return this.endEnrollmentDateTime.isAfter(LocalDateTime.now());
    }

    public boolean isAttended(Long accountId) {
        for (Enrollment e : this.enrollments) {
            if (e.getAccountId().equals(accountId) && e.isAttended()) {
                return true;
            }

        }
        return false;
    }

    private boolean isAlreadyEnrolled(Long accountId) {
        for (Enrollment e : this.enrollments) {
            if (e.getAccountId().equals(accountId)) {
                return true;
            }

        }
        return false;
    }

    // ====================================================================
    // 참가 인원 조회
    // ====================================================================

    public int numberOfRemainSpots() {
        if (this.limitOfEnrollments == null) {
            return 0;
        }
        return this.limitOfEnrollments - (int) this.enrollments.stream()
                .filter(Enrollment::isAccepted).count();
    }

    public long getNumberOfAcceptedEnrollments() {
        return this.enrollments.stream().filter(Enrollment::isAccepted).count();
    }

    // ====================================================================
    // 양방향 연관관계 편의 메서드
    // ====================================================================

    public void addEnrollment(Enrollment enrollment) {
        this.enrollments.add(enrollment);
        enrollment.setEvent(this);
    }

    public void removeEnrollment(Enrollment enrollment) {
        this.enrollments.remove(enrollment);
        enrollment.setEvent(null);
    }

    // ====================================================================
    // FCFS 자동 승인
    // ====================================================================

    public boolean isAbleToAcceptWaitingEnrollment() {
        return this.eventType == EventType.FCFS
                && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments();
    }

    public void acceptWaitingList() {
        if (this.isAbleToAcceptWaitingEnrollment()) {
            var waitingList = getWaitingList();
            int numberToAccept = (int) Math.min(
                    this.limitOfEnrollments - this.getNumberOfAcceptedEnrollments(),
                    waitingList.size()
            );
            waitingList.subList(0, numberToAccept).forEach(e -> e.setAccepted(true));
        }
    }

    public void acceptNextWaitingEnrollment() {
        if (this.isAbleToAcceptWaitingEnrollment()) {
            Enrollment enrollmentToAccept = getTheFirstWaitingEnrollment();
            if (enrollmentToAccept != null) {
                enrollmentToAccept.setAccepted(true);
            }
        }
    }

    private List<Enrollment> getWaitingList() {
        return this.enrollments.stream()
                .filter(e -> !e.isAccepted())
                .collect(Collectors.toList());
    }

    private Enrollment getTheFirstWaitingEnrollment() {
        for (Enrollment e : this.enrollments) {
            if (!e.isAccepted()) {
                return e;
            }

        }
        return null;
    }

    // ====================================================================
    // CONFIRMATIVE 수동 승인/거절
    // ====================================================================

    public boolean canAccept(Enrollment enrollment) {
        return this.eventType == EventType.CONFIRMATIVE
                && this.enrollments.contains(enrollment)
                && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments()
                && !enrollment.isAttended()
                && !enrollment.isAccepted();
    }

    public boolean canReject(Enrollment enrollment) {
        return this.eventType == EventType.CONFIRMATIVE
                && this.enrollments.contains(enrollment)
                && !enrollment.isAttended()
                && enrollment.isAccepted();
    }

    public void accept(Enrollment enrollment) {
        if (this.eventType == EventType.CONFIRMATIVE
                && this.limitOfEnrollments > this.getNumberOfAcceptedEnrollments()) {
            enrollment.setAccepted(true);
        }
    }

    public void reject(Enrollment enrollment) {
        if (this.eventType == EventType.CONFIRMATIVE) {
            enrollment.setAccepted(false);
        }
    }
}