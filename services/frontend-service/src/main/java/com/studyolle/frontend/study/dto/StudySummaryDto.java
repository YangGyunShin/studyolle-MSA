package com.studyolle.frontend.study.dto;

import lombok.Data;

/**
 * 대시보드 카드용 스터디 요약 DTO (목록 표시에 필요한 최소 필드).
 */
@Data
public class StudySummaryDto {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String image;
    private int memberCount;
}