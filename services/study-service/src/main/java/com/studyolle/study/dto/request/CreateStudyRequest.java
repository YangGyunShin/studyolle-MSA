package com.studyolle.study.dto.request;

import com.studyolle.study.entity.JoinType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

/**
 * 스터디 생성 요청 DTO
 *
 * [모노리틱 참조: StudyForm.java]
 * 모노리틱의 StudyForm 을 MSA 의 Request DTO 패턴으로 변환.
 * 필드와 검증 어노테이션은 동일하게 유지한다.
 *
 * [모노리틱과의 차이]
 * - @Data → @Getter (불변 요청 객체 권장)
 * - path 중복 검증은 StudyService.createNewStudy() 내부에서 수행
 *   (모노리틱에서는 StudyFormValidator 가 @InitBinder 로 등록되어 처리)
 */
@Getter
public class CreateStudyRequest {

    /**
     * 스터디 URL 경로 (고유 식별자)
     *
     * [모노리틱 참조: StudyForm.VALID_PATH_PATTERN]
     * ^[ㄱ-ㅎ가-힣a-z0-9_-]{2,20}$
     * - 한글 자음, 완성형 한글, 영문 소문자, 숫자, 언더스코어, 하이픈
     * - 2~20자 제한
     */
    public static final String VALID_PATH_PATTERN = "^[ㄱ-ㅎ가-힣a-z0-9_-]{2,20}$";

    @NotBlank
    @Length(min = 2, max = 20)
    @Pattern(regexp = VALID_PATH_PATTERN)
    private String path;

    @NotBlank
    @Length(max = 50)
    private String title;

    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    @NotBlank
    private String fullDescription;

    /** 생성 시 가입 방식 선택. 기본값 OPEN. */
    private JoinType joinType = JoinType.OPEN;
}