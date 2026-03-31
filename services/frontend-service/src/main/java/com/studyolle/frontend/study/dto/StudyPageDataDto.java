package com.studyolle.frontend.study.dto;

import lombok.Data;

import java.util.List;

/**
 * 스터디 페이지 렌더링에 필요한 모든 데이터를 하나로 묶은 DTO.
 *
 * study-service 의 GET /internal/studies/{path}/page-data?accountId={id} 가 반환한다.
 *
 * 설계 의도:
 *   view.html, members.html, settings/* 등 모든 스터디 하위 페이지는
 *   공통적으로 study 정보 + 권한 플래그를 필요로 한다.
 *   각각 별도 API 를 호출하면 N번의 라운드트립이 발생하므로,
 *   하나의 집계 엔드포인트로 묶어 한 번에 받아온다 (BFF 패턴).
 */
@Data
public class StudyPageDataDto {

    // ---- 스터디 기본 정보 ----
    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String image;               // 배너 이미지 Base64 또는 URL
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private boolean useBanner;
    private String joinType;            // "OPEN" | "APPROVAL_REQUIRED"
    private boolean removable;          // 스터디 삭제 가능 여부 (멤버/모임 없을 때)
    private int memberCount;

    // ---- 멤버 목록 ----
    private List<MemberDto> managers;
    private List<MemberDto> members;

    // ---- 현재 사용자의 권한 플래그 ----
    // study-service 가 X-Account-Id 를 보고 계산해서 내려준다.
    // 비로그인(accountId == null)이면 모두 false.
    private boolean manager;            // 현재 사용자가 관리자인가
    private boolean member;             // 현재 사용자가 멤버인가
    private boolean hasPendingRequest;  // 현재 사용자의 대기 중 가입 신청 존재 여부
}