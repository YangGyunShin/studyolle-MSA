package com.studyolle.adminfrontend.study.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * admin-service 의 StudyAdminDto 를 admin-frontend 가 역직렬화하기 위한 미러 DTO.
 *
 * 흐름: StudyAdminResponse(study-service)
 *       → StudyAdminDto(admin-service)
 *       → AdminStudyDto(이 파일, admin-frontend)
 *
 * 동일한 구조가 세 번 복사된다.
 * 각 서비스가 자기 경계 안에서 필요한 형태로 DTO 를 소유한다는 MSA 원칙이 이유다.
 * 회원 관리의 AdminMemberDto 와 같은 맥락이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminStudyDto {

    private Long id;
    private String path;
    private String title;
    private String shortDescription;

    private boolean published;
    private boolean closed;
    private boolean recruiting;

    private int memberCount;

    private LocalDateTime publishedDateTime;
    private LocalDateTime closedDateTime;
}