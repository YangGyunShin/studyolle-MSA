package com.studyolle.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    // 이메일 또는 닉네임 모두 허용 (AccountAuthService에서 두 가지 모두 조회)
    @NotBlank
    private String emailOrNickname;

    @NotBlank
    private String password;
}