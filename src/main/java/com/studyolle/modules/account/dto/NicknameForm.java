package com.studyolle.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class NicknameForm {

    // 공백 불가 (null 또는 빈 문자열 거부)
    @NotBlank
    // 최소 3자, 최대 20자 제한
    @Length(min = 3, max = 20)
    // 한글, 영어 소문자, 숫자, 밑줄(_), 하이픈(-)만 허용
    // 정규식은 ^[ㄱ-ㅎ가-힣a-z0-9_-]{3,20}$
    @Pattern(regexp = "^[ㄱ-ㅎ가-힣a-z0-9_-]{3,20}$")
    private String nickname;
}