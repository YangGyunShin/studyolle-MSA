package com.studyolle.frontend.account.dto;

import lombok.Data;

@Data
public class AccountSettingsDto {

    private Long id;
    private String email;
    private String nickname;
    private boolean emailVerified;
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
}