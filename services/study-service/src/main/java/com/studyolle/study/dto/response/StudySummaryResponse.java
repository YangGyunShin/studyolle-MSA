package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 스터디 목록/카드 응답 DTO
 *
 * fullDescription, image 등 무거운 필드를 제외한 경량 버전.
 * 대시보드, 검색 결과, 추천 목록 등 카드형 목록에서 사용한다.
 *
 * [모노리틱 참조]
 * MainController, CalendarService 등에서 스터디 목록을 뷰에 전달하던 것을
 * API 응답 DTO 로 전환한 형태.
 */
@Getter
@Builder
public class StudySummaryResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private int memberCount;
    private LocalDateTime publishedDateTime;
    private Set<Long> tagIds;
    private Set<Long> zoneIds;

    public static StudySummaryResponse from(Study study) {
        return StudySummaryResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .memberCount(study.getMemberCount())
                .publishedDateTime(study.getPublishedDateTime())
                .tagIds(study.getTagIds())
                .zoneIds(study.getZoneIds())
                .build();
    }
}