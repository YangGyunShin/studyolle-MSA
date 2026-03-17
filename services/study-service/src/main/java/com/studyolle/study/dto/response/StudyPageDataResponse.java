package com.studyolle.study.dto.response;

import com.studyolle.study.entity.Study;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// /internal/studies/{path}/page-data 응답 DTO.
// frontend-service 의 StudyPageDataDto 와 필드 구조가 1:1 대응된다.
// 스터디 페이지(view.html, settings/*)에 필요한 모든 데이터를 한 번에 반환한다.
@Getter
@Builder
public class StudyPageDataResponse {

    // 스터디 기본 정보
    private Long id;
    private String path;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private String image;
    private boolean published;
    private boolean closed;
    private boolean recruiting;
    private String joinType;        // JoinType enum -> 문자열("OPEN", "APPROVAL_REQUIRED")
    private boolean removable;
    private int memberCount;

    // 멤버 목록 — 현재는 accountId 만 채움. nickname 은 Phase 5 에서 account-service batch 조회 후 추가
    private List<MemberInfo> managers;
    private List<MemberInfo> members;

    // 현재 사용자(accountId) 기준 권한 플래그
    private boolean manager;
    private boolean member;
    private boolean hasPendingRequest;

    // -----------------------------------------------------------------
    // 정적 내부 클래스 — 멤버 정보 (accountId 기준)
    // -----------------------------------------------------------------

    // MemberDto 와 동일한 구조. nickname/profileImage/bio 는 account-service 를 통해
    // 채워야 하지만 현재 batch endpoint 가 없으므로 null 로 반환한다.
    // Jdenticon 은 id 값만 있으면 동작하므로 화면 렌더링에는 문제없다.
    @Getter
    @Builder
    public static class MemberInfo {
        private Long id;
        private String nickname;    // Phase 5 전까지 null
        private String profileImage;
        private String bio;
    }
}