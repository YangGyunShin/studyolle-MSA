package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**
 * /internal/** 경로 전용 응답 DTO
 *
 * event-service 가 모임 생성/수정 시 스터디 존재 여부와 관리자 권한을 확인하기 위해 사용한다.
 *
 * [포함 이유]
 * - managerIds: event-service 가 "이 사람이 스터디 관리자인가?" 확인 시 필요
 * - closed: 종료된 스터디에서는 모임 생성 불가 처리 시 필요
 *
 * [제외 이유]
 * - fullDescription, image: 내부 서비스에서는 불필요
 * - memberIds: event-service 에서 멤버 권한은 Enrollment 로 관리
 */
@Getter
@Builder
public class StudyInternalResponse {

    private Long id;
    private String path;
    private String title;
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private Set<Long> managerIds;

    public static StudyInternalResponse from(Study study) {
        return StudyInternalResponse.builder()
                .id(study.getId())
                .path(study.getPath())
                .title(study.getTitle())
                .published(study.isPublished())
                .closed(study.isClosed())
                .recruiting(study.isRecruiting())
                .managerIds(study.getManagerIds())
                .build();
    }
}