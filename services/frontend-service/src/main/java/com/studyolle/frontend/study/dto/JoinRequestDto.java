package com.studyolle.frontend.study.dto;

import lombok.Data;

/**
 * 가입 신청 목록 DTO.
 * study/settings/join-requests.html 의 th:each 에서 사용.
 */
@Data
public class JoinRequestDto {

    private Long id;
    private MemberDto account;      // 신청자 정보
    private String requestedAt;     // 포맷된 날짜 문자열 ("11월 15일 14:30")
}