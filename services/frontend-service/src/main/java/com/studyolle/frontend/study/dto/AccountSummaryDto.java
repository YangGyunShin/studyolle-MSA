package com.studyolle.frontend.study.dto;

/**
 * 네비게이션 바 + 대시보드 프로필 완성도에 필요한 계정 정보 DTO.
 *
 * [모노리틱과의 차이]
 * 모노리틱: Account JPA 엔티티 전체 (tags 컬렉션 포함)
 * MSA:       tags/zones 컬렉션 대신 tagCount, zoneCount 개수만 포함.
 *            대시보드 완성도 계산에는 개수만 있으면 충분하고,
 *            컬렉션 전체를 전송하는 오버헤드를 줄인다.
 *
 * account-service 의 GET /internal/accounts/{id} 가 이 형태로 반환한다.
 */
public class AccountSummaryDto {

    private Long id;
    private String nickname;
    private String email;
    private String bio;
    private String profileImage;
    private boolean emailVerified;
    private int tagCount;       // account.tags.size() 대신 개수만
    private int zoneCount;      // account.zones.size() 대신 개수만

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public int getTagCount() { return tagCount; }
    public void setTagCount(int tagCount) { this.tagCount = tagCount; }

    public int getZoneCount() { return zoneCount; }
    public void setZoneCount(int zoneCount) { this.zoneCount = zoneCount; }
}