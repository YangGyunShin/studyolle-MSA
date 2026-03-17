package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

// 대시보드 카드, 추천 목록, 최근 스터디 목록용 요약 DTO.
// frontend-service 의 StudySummaryDto 와 구조 동일.
@Getter
@Builder
public class StudySummaryResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String image;
    private int memberCount;

    public static StudySummaryResponse from(Study study) {
        return StudySummaryResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .image(study.getImage())
                .memberCount(study.getMemberCount())
                .build();
    }
}