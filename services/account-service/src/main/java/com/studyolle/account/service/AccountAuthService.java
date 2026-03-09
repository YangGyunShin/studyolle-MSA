package com.studyolle.account.service;

import com.studyolle.account.dto.response.TokenResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.infra.mail.EmailMessage;
import com.studyolle.account.infra.mail.EmailService;
import com.studyolle.account.repository.AccountRepository;
import com.studyolle.account.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

// [모노리틱과의 차이]
// 모노리틱: login() → UsernamePasswordAuthenticationToken → SecurityContextHolder + HttpSession
// MSA:      login() → JWT accessToken + refreshToken 발급 → 클라이언트가 Bearer 토큰으로 보관
// UserDetailsService, UserAccount, 세션 관련 코드 전부 제거됨
@Service
@Transactional
@RequiredArgsConstructor
public class AccountAuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;

    @Value("${app.host}")
    private String appHost;

    // 이메일 또는 닉네임 + 비밀번호로 로그인 → JWT 발급
    public TokenResponse login(String emailOrNickname, String rawPassword) {
        Account account = accountRepository.findByEmail(emailOrNickname);
        if (account == null) {
            account = accountRepository.findByNickname(emailOrNickname);
        }

        if (account == null || !passwordEncoder.matches(rawPassword, account.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (!account.isEmailVerified()) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다.");
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(), account.getNickname(), account.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());

        return TokenResponse.of(accessToken, refreshToken);
    }

    // refreshToken으로 새 accessToken 발급
    public TokenResponse refresh(String refreshToken) {
        Long accountId = jwtTokenProvider.getAccountId(refreshToken);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(
                account.getId(), account.getNickname(), account.getRole());

        return TokenResponse.of(newAccessToken, refreshToken);
    }

    // 이메일 기반 로그인 링크 발송 (비밀번호 없는 로그인)
    public void sendLoginLink(Account account) {
        Context context = new Context();
        context.setVariable("link", "/api/auth/login-by-email?token=" + account.getEmailCheckToken() + "&email=" + account.getEmail());
        context.setVariable("nickname", account.getNickname());
        context.setVariable("linkName", "스터디올래 로그인하기");
        context.setVariable("message", "로그인 하려면 아래 링크를 클릭하세요.");
        context.setVariable("host", appHost);

        String messageBody = templateEngine.process("mail/simple-link", context);

        emailService.sendEmail(
                EmailMessage.builder()
                        .to(account.getEmail())
                        .subject("스터디올래, 로그인 링크")
                        .message(messageBody)
                        .build()
        );
    }

    // 이메일 토큰 검증이 완료된 경우 비밀번호 검증 없이 바로 JWT 발급
    public TokenResponse loginByEmailToken(Account account) {
        String accessToken = jwtTokenProvider.createAccessToken(account.getId(), account.getNickname(), account.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());
        return TokenResponse.of(accessToken, refreshToken);
    }
}