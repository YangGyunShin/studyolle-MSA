package com.studyolle.event.dto.response;

import com.studyolle.event.entity.Enrollment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EnrollmentResponse {

    private Long id;
    private Long accountId;
    private LocalDateTime enrolledAt;
    private boolean accepted;
    private boolean attended;

    public static EnrollmentResponse from(Enrollment e) {
        EnrollmentResponse r = new EnrollmentResponse();
        r.setId(e.getId());
        r.setAccountId(e.getAccountId());
        r.setEnrolledAt(e.getEnrolledAt());
        r.setAccepted(e.isAccepted());
        r.setAttended(e.isAttended());
        return r;
    }
}