package com.studyolle.frontend.study.dto;

import lombok.Data;

/**
 * 스터디 멤버/관리자 표시용 DTO.
 * study/view.html, study/members.html 의 th:each 에서 사용.
 */
@Data
public class MemberDto {

    private Long id;
    private String nickname;
    private String profileImage;    // null 이면 Jdenticon 자동 생성
    private String bio;
}