package com.studyolle.account.dto.response;

import com.studyolle.account.entity.Account;
import lombok.Getter;

@Getter
public class AccountSummaryResponse {

    private Long id;
    private String nickname;
    private String email;
    private String bio;
    private String profileImage;
    private boolean emailVerified;
    private int tagCount;
    private int zoneCount;

    public static AccountSummaryResponse from(Account account) {
        AccountSummaryResponse dto = new AccountSummaryResponse();
        dto.id            = account.getId();
        dto.nickname      = account.getNickname();
        dto.email         = account.getEmail();
        dto.bio           = account.getBio();
        dto.profileImage  = account.getProfileImage();
        dto.emailVerified = account.isEmailVerified();
        dto.tagCount      = 0; // Account 엔티티에 tags 컬렉션 없음
        dto.zoneCount     = 0; // Account 엔티티에 zones 컬렉션 없음
        return dto;
    }
}