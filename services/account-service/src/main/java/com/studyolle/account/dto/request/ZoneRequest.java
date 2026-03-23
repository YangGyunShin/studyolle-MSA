package com.studyolle.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZoneRequest {

    @NotBlank
    private String zoneName;
}