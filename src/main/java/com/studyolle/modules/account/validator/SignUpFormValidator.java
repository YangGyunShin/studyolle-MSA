package com.studyolle.modules.account.validator;


import com.studyolle.modules.account.dto.SignUpForm;
import com.studyolle.modules.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * ✅ 회원 가입 폼(SignUpForm)에 대한 커스텀 유효성 검증 클래스
 *
 * 📌 역할:
 *  - 단순한 형식 검증(예: @Email, @NotBlank)만으로는 처리할 수 없는 **도메인 수준의 유효성 검사** 수행
 *  - 이메일 및 닉네임의 **중복 여부를 DB에서 조회**하여 확인함
 *
 * 📌 도메인 유효성 검증이 필요한 이유:
 *  - 사용자가 입력한 이메일/닉네임이 형식적으로는 올바르더라도,
 *    이미 사용 중이라면 가입을 막아야 함 → 이건 DB를 조회해야 알 수 있음
 *  - 이런 검증은 일반적인 Bean Validation(JSR-303)의 범위를 벗어남
 *    → @Email, @NotBlank 등은 **입력 형식 검증**에만 초점이 있음
 *
 * 📌 왜 서비스(Service)가 아니라 Validator에서 repository를 호출하는가?
 *  - Spring MVC에서 Validator는 단순한 유효성 검사를 위한 컴포넌트로 설계됨
 *  - Repository를 호출해 **간단한 존재 여부 조회** 정도까지는 허용되는 것이 일반적 패턴
 *    → 단, 복잡한 로직(여러 엔티티 조합, 트랜잭션 등)은 Service 계층에 위임해야 함
 *  - 즉, "중복 확인" 같은 정적 쿼리 기반 검증은 Validator 수준에서 책임질 수 있음
 *
 * 📌 유의사항:
 *  - 너무 복잡한 검증 로직을 validator에서 처리하면 **테스트하기 어려워지고 재사용성이 떨어짐**
 *    → 이 경우엔 service 단으로 위임하는 것이 더 좋은 설계
 *
 * 📌 사용 방식:
 *  - 컨트롤러에 `@InitBinder("signUpForm")`으로 등록됨
 *  - 회원가입 요청 시 WebDataBinder가 `validate(...)`를 자동 호출하여 검증 수행
 */
@Component
@RequiredArgsConstructor
public class SignUpFormValidator implements Validator {

    // ✅ 사용자 정보 중복 체크를 위해 Repository 주입
    private final AccountRepository accountRepository;

    /**
     * ✅ 현재 validator가 지원하는 클래스인지 여부를 반환
     *
     * @param clazz 현재 바인딩된 객체의 클래스
     * @return SignUpForm이거나 그 하위 클래스이면 true 반환
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return SignUpForm.class.isAssignableFrom(clazz);
    }

    /**
     * ✅ 실제 유효성 검사를 수행하는 메서드
     *
     * @param object 사용자 입력을 바인딩한 폼 객체 (SignUpForm)
     * @param errors 오류 결과를 저장할 객체 (BindingResult와 동일한 역할)
     */
    @Override
    public void validate(Object object, Errors errors) {
        SignUpForm signUpForm = (SignUpForm) object;

        // ✅ 이메일 중복 검사
        if (accountRepository.existsByEmail(signUpForm.getEmail())) {
            // 📌 이메일 중복인 경우: errors 객체에 필드 오류 등록
            errors.rejectValue(
                    "email",                   // 오류 필드명
                    "invalid.email",           // 메시지 프로퍼티 키
                    new Object[]{signUpForm.getEmail()}, // 메시지 템플릿에 전달할 인자
                    "이미 사용중인 이메일 입니다."     // 기본 오류 메시지
            );
        }

        // ✅ 닉네임 중복 검사
        if (accountRepository.existsByNickname(signUpForm.getNickname())) {
            errors.rejectValue("nickname", "invalid.nickname", new Object[]{signUpForm.getNickname()}, "이미 사용중인 닉네임 입니다.");
        }
    }
}