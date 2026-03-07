package com.studyolle.modules.study.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

/**
 * StudyDescriptionForm - 스터디 소개 수정 폼 데이터를 담는 DTO
 *
 * =============================================
 * 용도
 * =============================================
 *
 * 스터디 소개 수정 페이지(study/settings/description)에서
 * 사용자가 입력한 데이터를 컨트롤러로 전달합니다.
 *
 * StudyForm과 달리 path, title 필드가 없으며,
 * shortDescription과 fullDescription만 수정할 수 있습니다.
 *
 * =============================================
 * ModelMapper 양방향 변환
 * =============================================
 *
 * 이 DTO는 ModelMapper를 통해 양방향으로 변환됩니다:
 *
 * 1. Study -> StudyDescriptionForm (GET 요청: 기존 값을 폼에 채우기)
 *    StudyDescriptionForm form = modelMapper.map(study, StudyDescriptionForm.class);
 *
 * 2. StudyDescriptionForm -> Study (POST 요청: 수정된 값을 엔티티에 반영)
 *    modelMapper.map(studyDescriptionForm, study);
 *    이때 study는 영속 상태이므로 Dirty Checking에 의해 자동으로 DB에 반영됩니다.
 *
 * =============================================
 * @NoArgsConstructor 필요 이유
 * =============================================
 *
 * ModelMapper가 리플렉션으로 객체를 생성할 때 기본 생성자가 필요합니다.
 * @Data가 제공하는 것은 필드가 있는 경우 기본 생성자를 만들지 않으므로,
 * 명시적으로 @NoArgsConstructor를 추가합니다.
 */
@Data
@NoArgsConstructor
public class StudyDescriptionForm {

    /** 짧은 소개 (최대 100자, 카드형 목록에 표시) */
    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    /** 상세 소개 (에디터로 작성, HTML 포함 가능, 길이 제한 없음) */
    @NotBlank
    private String fullDescription;
}