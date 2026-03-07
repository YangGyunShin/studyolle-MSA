package com.studyolle.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminDashboardStats {
    private long totalMembers;
    private long totalStudies;
    private long totalEvents;
    private long todaySignups;
}