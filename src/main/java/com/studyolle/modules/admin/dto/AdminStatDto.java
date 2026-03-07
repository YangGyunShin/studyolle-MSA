package com.studyolle.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminStatDto {
    private String label;   // "2026-01", "2026-02" 등
    private long count;
}