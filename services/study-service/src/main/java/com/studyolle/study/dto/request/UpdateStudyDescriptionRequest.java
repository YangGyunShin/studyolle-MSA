package com.studyolle.study.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * 스터디 소개 수정 요청 DTO
 *
 * [모노리틱 참조: StudyDescriptionForm.java]
 * path, title 없이 shortDescription, fullDescription 만 수정한다.
 */
@Getter
@NoArgsConstructor
public class UpdateStudyDescriptionRequest {

    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    @NotBlank
    private String fullDescription;
}