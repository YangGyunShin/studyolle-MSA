package com.studyolle.frontend.event.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EnrollmentDto {

    private Long id;
    private Long accountId;
    private LocalDateTime enrolledAt;
    private boolean accepted;
    private boolean attended;
}