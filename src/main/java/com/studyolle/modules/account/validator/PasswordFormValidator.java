package com.studyolle.modules.account.validator;

import com.studyolle.modules.account.dto.PasswordForm;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;


/**
 * 비밀번호 변경 폼의 유효성 검사 수행
 *
 * - supports(): 해당 Validator가 어떤 클래스에 적용될 수 있는지를 명시
 * - validate():
 * - 두 비밀번호 필드가 일치하지 않으면 errors.rejectValue()로 에러 등록
 * - 에러 등록된 필드("newPassword")에 메시지를 표시할 수 있음
 */
public class PasswordFormValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return PasswordForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        PasswordForm passwordForm = (PasswordForm) target;
        if (!passwordForm.getNewPassword().equals(passwordForm.getNewPasswordConfirm())) {
            errors.rejectValue("newPassword", "wrong.value", "입력한 새 패스워드가 일치하지 않습니다.");
        }
    }
}