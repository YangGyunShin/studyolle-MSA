package com.studyolle.modules.account.validator;

import com.studyolle.modules.account.dto.NicknameForm;
import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
@RequiredArgsConstructor
public class NicknameValidator implements Validator {

    private final AccountRepository accountRepository;

    @Override
    public boolean supports(Class<?> clazz) {
        // 이 Validator는 NicknameForm만 지원
        return NicknameForm.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        NicknameForm nicknameForm = (NicknameForm) target;

        // 새로 입력된 닉네임이 이미 DB에 존재하는지 검사
        Account byNickname = accountRepository.findByNickname(nicknameForm.getNickname());

        // 이미 존재하는 경우 오류 추가 (rejectValue는 필드 단위 오류를 추가하는 메서드)
        if (byNickname != null) {
            errors.rejectValue("nickname", "wrong.value", "입력하신 닉네임을 사용할 수 없습니다.");
        }
    }
}
