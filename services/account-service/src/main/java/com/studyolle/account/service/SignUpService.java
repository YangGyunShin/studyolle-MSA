package com.studyolle.account.service;

import com.studyolle.account.dto.request.SignUpRequest;
import com.studyolle.account.entity.Account;
import com.studyolle.account.infra.mail.EmailMessage;
import com.studyolle.account.infra.mail.EmailService;
import com.studyolle.account.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

// [모노리틱과의 차이]
// - SignUpForm → SignUpRequest (REST API 관례)
// - ModelMapper 제거: Account.builder()로 직접 생성 (Tag/Zone 필드가 없어져서 매핑 단순해짐)
// - completeSignUp() 내부에서 login() 호출 제거: 이제 인증 완료 후 클라이언트가 직접 로그인 API를 호출
@Service
@Transactional
@RequiredArgsConstructor
public class SignUpService {

    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TemplateEngine templateEngine;

    @Value("${app.host}")
    private String appHost;

    public Account processNewAccount(@Valid SignUpRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        if (accountRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }
        Account newAccount = saveNewAccount(request);
        sendSignUpConfirmEmail(newAccount);
        return newAccount;
    }

    private Account saveNewAccount(SignUpRequest request) {
        Account account = Account.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        account.generateEmailCheckToken();
        return accountRepository.save(account);
    }

    public void sendSignUpConfirmEmail(Account newAccount) {
        Context context = new Context();
        context.setVariable("link", "/check-email-token?token=" + newAccount.getEmailCheckToken() + "&email=" + newAccount.getEmail());
        context.setVariable("nickname", newAccount.getNickname());
        context.setVariable("linkName", "이메일 인증하기");
        context.setVariable("message", "스터디올래 서비스를 이용하려면 링크를 클릭하세요.");
        context.setVariable("host", appHost);

        String message = templateEngine.process("mail/simple-link", context);

        emailService.sendEmail(EmailMessage.builder()
                .to(newAccount.getEmail())
                .subject("스터디올래, 회원 인증")
                .message(message)
                .build());
    }

    // 인증 완료 처리 - 모노리틱과 달리 자동 로그인(login())을 하지 않는다.
    // 이유: MSA에서는 "이메일 인증 → 완료 메시지 응답 → 클라이언트가 POST /api/auth/login 호출"이 올바른 흐름.
    public void completeSignUp(Account account) {
        account.completeSignUp();
        // @Transactional 범위 내이므로 Dirty Checking으로 DB에 자동 반영됨
    }
}