package com.studyolle.event.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateEventRequest {

    @NotBlank
    private String title;

    private String description;

    @Min(2)
    private int limitOfEnrollments;

    @NotNull
    private LocalDateTime endEnrollmentDateTime;

    @NotNull
    private LocalDateTime startDateTime;

    @NotNull
    private LocalDateTime endDateTime;
}