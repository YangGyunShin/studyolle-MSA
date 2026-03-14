package com.studyolle.frontend.study.dto;

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

    // Lombok 대신 직접 getter/setter (프로젝트 설정에 맞게 교체 가능)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getFullDescription() { return fullDescription; }
    public void setFullDescription(String fullDescription) { this.fullDescription = fullDescription; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public boolean isRecruiting() { return recruiting; }
    public void setRecruiting(boolean recruiting) { this.recruiting = recruiting; }

    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }

    public boolean isRemovable() { return removable; }
    public void setRemovable(boolean removable) { this.removable = removable; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public List<MemberDto> getManagers() { return managers; }
    public void setManagers(List<MemberDto> managers) { this.managers = managers; }

    public List<MemberDto> getMembers() { return members; }
    public void setMembers(List<MemberDto> members) { this.members = members; }

    public boolean isManager() { return manager; }
    public void setManager(boolean manager) { this.manager = manager; }

    public boolean isMember() { return member; }
    public void setMember(boolean member) { this.member = member; }

    public boolean isHasPendingRequest() { return hasPendingRequest; }
    public void setHasPendingRequest(boolean hasPendingRequest) { this.hasPendingRequest = hasPendingRequest; }
}