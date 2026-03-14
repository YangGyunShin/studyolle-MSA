package com.studyolle.frontend.study.dto;

/**
 * 대시보드 모임 일정용 DTO.
 */
public class EventSummaryDto {

    private Long id;
    private String title;
    /** 포맷된 날짜 문자열 또는 ISO-8601. index.html 의 JS 가 ISO 면 자동 변환함. */
    private String startDateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStartDateTime() { return startDateTime; }
    public void setStartDateTime(String startDateTime) { this.startDateTime = startDateTime; }
}