package com.studyolle.modules.study.dto;

import com.studyolle.modules.study.entity.JoinType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * StudyForm - 스터디 생성 폼 데이터를 담는 DTO
 *
 * =============================================
 * 용도
 * =============================================
 *
 * 스터디 개설 페이지(study/form)에서 사용자가 입력한 데이터를
 * 컨트롤러로 전달하기 위한 Data Transfer Object입니다.
 *
 * 컨트롤러에서 ModelMapper를 통해 Study 엔티티로 변환됩니다:
 * Study newStudy = modelMapper.map(studyForm, Study.class);
 *
 * =============================================
 * 유효성 검증 전략 (2단계)
 * =============================================
 *
 * 1단계: Bean Validation (선언적 검증)
 *   - @NotBlank, @Length, @Pattern 등 어노테이션 기반 검증
 *   - 형식, 길이, 패턴 등의 기본적인 입력값 검증
 *   - 컨트롤러의 @Valid 어노테이션에 의해 자동 실행
 *
 * 2단계: StudyFormValidator (커스텀 검증)
 *   - Bean Validation만으로는 불가능한 비즈니스 규칙 검증
 *   - path 필드의 DB 중복 여부 확인
 *   - @InitBinder를 통해 WebDataBinder에 등록되어 자동 실행
 *
 * 두 단계의 검증이 모두 통과해야 스터디가 생성됩니다.
 *
 * =============================================
 * VALID_PATH_PATTERN 정규식 설명
 * =============================================
 *
 * ^[ㄱ-ㅎ가-힣a-z0-9_-]{2,20}$
 *
 * - ㄱ-ㅎ: 한글 자음
 * - 가-힣: 완성형 한글
 * - a-z: 영문 소문자 (대문자 불가)
 * - 0-9: 숫자
 * - _-: 언더스코어, 하이픈
 * - {2,20}: 최소 2자, 최대 20자
 *
 * 이 패턴은 StudySettingsService.isValidPath()에서도 재사용됩니다.
 */
@Data
public class StudyForm {

    /** path 유효성 검증에 사용되는 정규식 패턴 상수. 서비스 계층에서도 참조합니다. */
    public static final String VALID_PATH_PATTERN = "^[ㄱ-ㅎ가-힣a-z0-9_-]{2,20}$";

    /**
     * 스터디 URL 경로 (고유 식별자).
     * - @NotBlank: 비어있거나 공백만으로 구성될 수 없음
     * - @Length(min=2, max=20): 2~20자 제한
     * - @Pattern: VALID_PATH_PATTERN 정규식과 일치해야 함
     *
     * 추가로 StudyFormValidator에서 DB 중복 검증이 수행됩니다.
     */
    @NotBlank
    @Length(min = 2, max = 20)
    @Pattern(regexp = VALID_PATH_PATTERN)
    private String path;

    /** 스터디 제목 (최대 50자) */
    @NotBlank
    @Length(max = 50)
    private String title;

    /** 짧은 소개 (최대 100자, 카드형 목록에 표시) */
    @NotBlank
    @Length(max = 100)
    private String shortDescription;

    /** 상세 소개 (에디터로 작성, HTML 포함 가능, 길이 제한 없음) */
    @NotBlank
    private String fullDescription;

    /**
     * 스터디 생성 시 가입 방식 선택.
     * 기본값 OPEN으로, 생성 폼에서 선택하지 않으면 자유 가입으로 생성됩니다.
     */
    private JoinType joinType = JoinType.OPEN;
}