package com.studyolle.modules.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class SignUpForm {

    @NotBlank
    @Length(min = 3, max = 20)
    @Pattern(regexp = "^[0-9ㄱ-ㅎ가-힣a-z_-]{3,20}$")
    private String nickname;

    @NotBlank
    @Length(min = 8, max = 50)
    private String password;

    @Email
    @NotBlank
    private String email;
}
