package com.studyolle.modules.study.validator;

import com.studyolle.modules.study.dto.StudyForm;
import com.studyolle.modules.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * StudyFormValidator - 스터디 생성 폼의 커스텀 유효성 검증기
 *
 * =============================================
 * Bean Validation과의 역할 분담
 * =============================================
 *
 * StudyForm에 선언된 Bean Validation 어노테이션(@NotBlank, @Length, @Pattern)은
 * 입력값의 형식, 길이, 패턴 등 "형식적 검증"을 담당합니다.
 *
 * 반면, 이 커스텀 Validator는 "비즈니스 규칙 검증"을 담당합니다:
 * - path 값이 DB에 이미 존재하는지 중복 확인
 * - Bean Validation만으로는 DB 조회가 필요한 검증을 수행할 수 없음
 *
 * 실행 순서:
 * 1. Bean Validation (@Valid)이 먼저 실행
 * 2. 이 커스텀 Validator가 추가로 실행 (WebDataBinder에 등록됨)
 * 3. 두 검증 결과가 Errors 객체에 합산됨
 *
 * =============================================
 * @InitBinder를 통한 등록
 * =============================================
 *
 * StudyController에서 다음과 같이 등록됩니다:
 *
 * @InitBinder("studyForm")
 * public void studyFormInitBinder(WebDataBinder webDataBinder) {
 *     webDataBinder.addValidators(studyFormValidator);
 * }
 *
 * "studyForm"이라는 모델 속성이 바인딩될 때만 이 Validator가 적용됩니다.
 * 이렇게 범위를 제한하면, 다른 폼 객체에는 영향을 주지 않습니다.
 *
 * =============================================
 * Repository 직접 접근에 대한 아키텍처 설명
 * =============================================
 *
 * 이 Validator는 StudyRepository에 직접 접근합니다.
 * 일반적으로는 Controller -> Service -> Repository 원칙을 따르지만,
 * Validator는 Controller가 아닌 "Spring Validation 인프라" 컴포넌트이며,
 * 단순한 existsByPath() 호출(읽기 전용, 부수 효과 없음)이므로
 * Repository 직접 접근이 정당화됩니다.
 *
 * 이는 프로젝트 내 다른 Validator(SignUpFormValidator 등)에서도
 * 동일하게 적용되는 패턴입니다.
 */
@Component
@RequiredArgsConstructor
public class StudyFormValidator implements Validator {

    private final StudyRepository studyRepository;

    /**
     * 이 Validator가 어떤 클래스를 검증할 수 있는지 반환합니다.
     *
     * isAssignableFrom()을 사용하여 StudyForm 클래스 및 그 하위 클래스에 대해
     * 유효성 검증이 가능함을 선언합니다.
     *
     * @param clazz 검증 대상 클래스
     * @return StudyForm이거나 그 하위 클래스이면 true
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyForm.class.isAssignableFrom(clazz);
    }

    /**
     * 실제 유효성 검증 로직을 수행합니다.
     *
     * 검증 내용:
     * - StudyForm의 path 값으로 DB에서 동일한 path가 존재하는지 확인
     * - 이미 존재하면 "path" 필드에 "wrong.path" 에러 코드를 추가
     *
     * errors.rejectValue() 파라미터:
     * - "path": 에러가 발생한 필드명
     * - "wrong.path": 에러 코드 (메시지 소스에서 메시지 조회에 사용)
     * - "스터디 경로를 사용할 수 없습니다.": 기본 에러 메시지
     *
     * @param target 검증 대상 객체 (StudyForm)
     * @param errors 검증 에러를 담는 컨테이너
     */
    @Override
    public void validate(Object target, Errors errors) {
        StudyForm studyForm = (StudyForm) target;
        if (studyRepository.existsByPath(studyForm.getPath())) {
            errors.rejectValue("path", "wrong.path", "스터디 경로를 사용할 수 없습니다.");
        }
    }
}