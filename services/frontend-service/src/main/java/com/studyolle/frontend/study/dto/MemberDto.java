package com.studyolle.frontend.study.dto;

/**
 * 스터디 멤버/관리자 표시용 DTO.
 * study/view.html, study/members.html 의 th:each 에서 사용.
 */
public class MemberDto {

    private Long id;
    private String nickname;
    private String profileImage;    // null 이면 Jdenticon 자동 생성
    private String bio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}