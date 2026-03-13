package com.studyolle.study.dto.response;

import com.studyolle.study.entity.JoinType;
import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 스터디 상세 응답 DTO
 *
 * [모노리틱과의 차이]
 * - managers, members: Account 객체 → accountId Set
 * - tags, zones: Tag/Zone 객체 → ID Set (표시명은 frontend 가 metadata-service 에서 별도 조회)
 *
 * [설계 결정]
 * tagIds/zoneIds 를 반환하고 frontend-service 가 metadata-service 를 직접 호출해
 * 태그/지역 이름을 조회하게 한다. study-service 가 매번 Feign 호출을 하지 않아도 된다.
 */
@Getter
@Builder
public class StudyResponse {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String image;
    private boolean useBanner;
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private JoinType joinType;
    private int memberCount;
    private LocalDateTime publishedDateTime;

    // [길 1] ID 만 반환 — 표시명은 metadata-service/account-service 에서 별도 조회
    private Set<Long> managerIds;
    private Set<Long> memberIds;
    private Set<Long> tagIds;
    private Set<Long> zoneIds;

    public static StudyResponse from(Study study) {
        return StudyResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .shortDescription(study.getShortDescription())
                .fullDescription(study.getFullDescription())
                .image(study.getImage())
                .useBanner(study.isUseBanner())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .joinType(study.getJoinType())
                .memberCount(study.getMemberCount())
                .publishedDateTime(study.getPublishedDateTime())
                .managerIds(study.getManagerIds())
                .memberIds(study.getMemberIds())
                .tagIds(study.getTagIds())
                .zoneIds(study.getZoneIds())
                .build();
    }
}