package com.studyolle.account.dto.response;

import com.studyolle.account.entity.Account;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AccountResponse {

    private Long id;
    private String email;
    private String nickname;
    private String role;
    private boolean emailVerified;
    private LocalDateTime joinedAt;
    private String bio;
    private String url;
    private String occupation;
    private String location;
    private String profileImage;
    private boolean studyCreatedByEmail;
    private boolean studyCreatedByWeb;
    private boolean studyEnrollmentResultByEmail;
    private boolean studyEnrollmentResultByWeb;
    private boolean studyUpdatedByEmail;
    private boolean studyUpdatedByWeb;

    // 정적 팩터리 메서드: Account 엔티티 → AccountResponse DTO 변환
    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .nickname(account.getNickname())
                .role(account.getRole())
                .emailVerified(account.isEmailVerified())
                .joinedAt(account.getJoinedAt())
                .bio(account.getBio())
                .url(account.getUrl())
                .occupation(account.getOccupation())
                .location(account.getLocation())
                .profileImage(account.getProfileImage())
                .studyCreatedByEmail(account.isStudyCreatedByEmail())
                .studyCreatedByWeb(account.isStudyCreatedByWeb())
                .studyEnrollmentResultByEmail(account.isStudyEnrollmentResultByEmail())
                .studyEnrollmentResultByWeb(account.isStudyEnrollmentResultByWeb())
                .studyUpdatedByEmail(account.isStudyUpdatedByEmail())
                .studyUpdatedByWeb(account.isStudyUpdatedByWeb())
                .build();
    }
}