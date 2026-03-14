package com.studyolle.frontend.study.dto;

/**
 * 대시보드 카드용 스터디 요약 DTO (목록 표시에 필요한 최소 필드).
 */
public class StudySummaryDto {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String image;
    private int memberCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
}