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

/**
 * 인증 관련 비즈니스 로직 service.
 *
 * =============================================================
 * [Phase 8 옵션 B 변경 사항 요약]
 * =============================================================
 *
 * 1. login() 의 "이메일 인증 필수" 제약 제거
 *    기존: emailVerified=false 면 IllegalStateException 으로 로그인 자체를 막음
 *    변경: 인증 안 한 사용자도 로그인 가능.
 *         단 JWT 에 emailVerified=false 가 담겨서 나가고,
 *         각 서비스의 쓰기 엔드포인트에서 이 헤더를 보고 차단한다.
 *
 *    [왜 이렇게 바꾸는가]
 *    기존 방식은 가입 직후 둘러보기 같은 읽기 활동도 불가능하게 한다.
 *    "인증 안 한 사람도 둘러볼 수는 있고, 쓸 수는 없다" 가 더 유연한 UX 다.
 *    "페이지 접근 제한" 기능의 의미는 여기에 있다 — 로그인 자체를 막는 것은 접근 제한이 아니라 로그인 실패다.
 *
 * 2. createAccessToken() 호출부 3곳 모두 emailVerified 파라미터 추가
 *    - login()
 *    - refresh()
 *    - loginByEmailToken()
 *    각 메서드가 Account 엔티티에서 현재 emailVerified 를 읽어 전달한다.
 */
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

    /**
     * 이메일 또는 닉네임 + 비밀번호로 로그인 → JWT 발급.
     *
     * [변경점 — emailVerified 체크 제거]
     * 이전에는 "이메일 인증이 완료되지 않았습니다" 예외를 던져 로그인을 막았다.
     * 이제는 미인증 사용자도 로그인 가능.
     * JWT 의 emailVerified claim 으로 게이트웨이와 각 서비스에 상태를 전달하고, 쓰기 시점에 차단한다.
     */
    public TokenResponse login(String emailOrNickname, String rawPassword) {
        Account account = accountRepository.findByEmail(emailOrNickname);
        if (account == null) {
            account = accountRepository.findByNickname(emailOrNickname);
        }

        if (account == null || !passwordEncoder.matches(rawPassword, account.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        // [제거됨] 과거에는 여기서 emailVerified 체크로 로그인을 거부했다.
        // 이제는 인증 여부와 무관하게 로그인을 허용하고, emailVerified 를 JWT claim 에 담는다.

        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(),
                account.getNickname(),
                account.getRole(),
                account.isEmailVerified());  // ← 신규 파라미터
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());

        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * refreshToken 으로 새 accessToken 을 발급.
     *
     * [왜 DB 에서 다시 읽는가]
     * refreshToken 발급 시점과 재발급 시점 사이에 DB 상태가 바뀌었을 수 있다.
     * 예: 관리자가 이 사용자의 role 을 바꿨거나,
     *     사용자가 이메일 인증을 완료했거나.
     *     항상 최신 DB 상태를 JWT 에 반영해야 claim 이 신뢰할 수 있다.
     *
     * 특히 emailVerified 는 이메일 인증 완료 후 refresh 로도 새 상태를 반영받을 수 있는 경로가 된다
     * (비록 checkEmailToken 에서 즉시 재발급하지만, 사용자가 다른 탭/기기에서 로그인해 둔 세션도 refresh 를 통해 자연스럽게 최신화된다).
     */
    public TokenResponse refresh(String refreshToken) {
        Long accountId = jwtTokenProvider.getAccountId(refreshToken);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.createAccessToken(
                account.getId(),
                account.getNickname(),
                account.getRole(),
                account.isEmailVerified());  // ← 신규 파라미터

        return TokenResponse.of(newAccessToken, refreshToken);
    }

    /**
     * 이메일 기반 로그인 링크 발송 (비밀번호 없는 로그인).
     * 이 메서드 자체는 토큰을 발급하지 않으므로 emailVerified 관련 변경 불필요.
     */
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

    /**
     * 이메일 로그인 링크 클릭 시 비밀번호 검증 없이 JWT 발급.
     *
     * 이 경로로 들어오는 경우는 이메일의 링크를 클릭했다는 의미이므로 emailVerified 는 당연히 true 여야 한다.
     * 다만 방어적으로 account.isEmailVerified() 값을 그대로 담는다.
     * (정상 흐름에서는 true 가 나온다.)
     */
    public TokenResponse loginByEmailToken(Account account) {
        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(),
                account.getNickname(),
                account.getRole(),
                account.isEmailVerified());  // ← 신규 파라미터
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());
        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * 이메일 인증 완료 직후 새 JWT 를 재발급한다.
     *
     * [언제 호출되는가]
     * AuthController.checkEmailToken() 이 이메일 토큰 검증 + completeSignUp(emailVerified=true) 처리를 마친 직후 이 메서드를 호출한다.
     * 결과로 받은 새 JWT 에는 emailVerified=true 가 담겨 있으므로,
     * 프론트는 기존 토큰을 이것으로 교체하면 즉시 모든 기능에 접근할 수 있다.
     *
     * [왜 loginByEmailToken 과 별도 메서드로 만드는가]
     * loginByEmailToken 은 "비밀번호 없는 로그인 링크 클릭" 용도이고,
     * 이 메서드는 "이메일 인증 완료 후 토큰 갱신" 용도다.
     * 실제 코드는 동일하지만 의도가 다르므로 호출 지점에서 의미를 분명히 하기 위해 분리했다.
     * 나중에 각자 다른 확장이 필요할 때 (예: 인증 완료 이벤트 발행) 두 지점을 구분해서 처리할 수 있다.
     *
     * @param account emailVerified 가 true 로 변경된 영속 상태 Account 엔티티
     * @return 새 accessToken + 새 refreshToken
     */
    public TokenResponse reissueTokensAfterEmailVerification(Account account) {
        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(),
                account.getNickname(),
                account.getRole(),
                account.isEmailVerified());
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getId());
        return TokenResponse.of(accessToken, refreshToken);
    }
}