package com.studyolle.frontend.study.dto;

/**
 * 가입 신청 목록 DTO.
 * study/settings/join-requests.html 의 th:each 에서 사용.
 */
public class JoinRequestDto {

    private Long id;
    private MemberDto account;      // 신청자 정보
    private String requestedAt;     // 포맷된 날짜 문자열 ("11월 15일 14:30")

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MemberDto getAccount() { return account; }
    public void setAccount(MemberDto account) { this.account = account; }

    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
}