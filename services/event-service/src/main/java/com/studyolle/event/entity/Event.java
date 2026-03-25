package com.studyolle.event.entity;

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
public class Event {

    @Id @GeneratedValue
    private Long id;

    // MSA: Study 엔티티 대신 studyPath(문자열)로 참조
    @Column(nullable = false)
    private String studyPath;

    // MSA: Account 엔티티 대신 accountId(Long)로 참조
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

    @OneToMany(mappedBy = "event", cascade = CascadeType.REMOVE, orphanRemoval = true)
    @OrderBy("enrolledAt")
    private List<Enrollment> enrollments = new ArrayList<>();

    // ====================================================================
    // 도메인 메서드 (모노리틱에서 UserAccount 의존성 제거 → accountId로 대체)
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

    public int numberOfRemainSpots() {
        return this.limitOfEnrollments - (int) this.enrollments.stream()
                .filter(Enrollment::isAccepted).count();
    }

    public long getNumberOfAcceptedEnrollments() {
        return this.enrollments.stream().filter(Enrollment::isAccepted).count();
    }

    public void addEnrollment(Enrollment enrollment) {
        this.enrollments.add(enrollment);
        enrollment.setEvent(this);
    }

    public void removeEnrollment(Enrollment enrollment) {
        this.enrollments.remove(enrollment);
        enrollment.setEvent(null);
    }

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