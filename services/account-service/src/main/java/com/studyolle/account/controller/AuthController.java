package com.studyolle.account.controller;

import com.studyolle.account.dto.request.LoginRequest;
import com.studyolle.account.dto.request.RefreshRequest;
import com.studyolle.account.dto.request.SignUpRequest;
import com.studyolle.account.dto.response.CommonApiResponse;
import com.studyolle.account.dto.response.TokenResponse;
import com.studyolle.account.entity.Account;
import com.studyolle.account.repository.AccountRepository;
import com.studyolle.account.service.AccountAuthService;
import com.studyolle.account.service.SignUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// api-gateway 화이트리스트: /api/auth/** 는 JWT 없이 접근 가능
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignUpService signUpService;
    private final AccountAuthService accountAuthService;
    private final AccountRepository accountRepository;

    // 회원가입 → 인증 이메일 발송 → 완료 메시지
    @PostMapping("/signup")
    public ResponseEntity<CommonApiResponse<Void>> signUp(
            @Valid @RequestBody SignUpRequest request) {
        signUpService.processNewAccount(request);
        return ResponseEntity.ok(CommonApiResponse.ok("회원가입이 완료되었습니다. 이메일을 확인해주세요."));
    }

    // 로그인 → accessToken + refreshToken 발급
    @PostMapping("/login")
    public ResponseEntity<CommonApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse token = accountAuthService.login(request.getEmailOrNickname(), request.getPassword());
        return ResponseEntity.ok(CommonApiResponse.ok(token));
    }

    // refreshToken으로 새 accessToken 발급
    @PostMapping("/refresh")
    public ResponseEntity<CommonApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        TokenResponse token = accountAuthService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(CommonApiResponse.ok(token));
    }

    /**
     * 이메일 인증 링크 클릭 시 호출된다 (이메일 본문의 링크가 이 엔드포인트를 가리킴).
     *
     * [Phase 8 옵션 B 변경]
     * 반환 타입이 CommonApiResponse<Void> 에서 CommonApiResponse<TokenResponse> 로 변경되었다.
     * 이메일 인증이 완료되면 account.emailVerified 가 true 로 변경되는데,
     * 기존에 사용자가 로그인해 둔 JWT 에는 여전히 emailVerified=false 가 담겨 있어
     * 각 서비스의 쓰기 엔드포인트에서 계속 차단된다.
     *
     * 이 문제를 해결하기 위해 인증 성공 시점에 새 JWT 를 재발급해서 응답에 실어 보낸다.
     * 프론트(check-email-token.html) 의 JS 가 이 토큰을 받아 localStorage / 쿠키를 교체하면
     * 다음 요청부터 즉시 쓰기 기능에 접근할 수 있다.
     *
     * [왜 프론트에서 강제 재로그인으로 해결하지 않는가]
     * 인증 메일 클릭 → 로그인 페이지 이동 → 사용자가 다시 이메일/패스워드 입력
     * 이 흐름은 사용자에게 "방금 인증했는데 또 뭐하라는 거야" 라는 짜증을 준다.
     * 서버에서 JWT 만 재발급하면 사용자는 인증 성공 화면을 거쳐 바로 홈으로 이동하며,
     * 추가 조작 없이 모든 기능을 쓸 수 있다. UX 관점의 핵심 개선점이다.
     *
     * [보안 관점]
     * 이메일 토큰 검증이 이미 성공한 상태이므로 새 JWT 를 발급하는 것이 안전하다.
     * 공격자가 이메일 토큰을 탈취해야만 JWT 재발급이 가능한 구조이므로,
     * 이메일 인증 자체의 보안이 유지되는 한 이 흐름도 안전하다.
     */
    @GetMapping("/check-email-token")
    public ResponseEntity<CommonApiResponse<TokenResponse>> checkEmailToken(
            @RequestParam String token,
            @RequestParam String email) {

        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            throw new IllegalArgumentException("존재하지 않는 이메일입니다.");
        }
        if (!account.isValidToken(token)) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        }

        // 이메일 인증 완료 처리 (emailVerified=true, joinedAt=now)
        signUpService.completeSignUp(account);

        // JWT 재발급 — 새 토큰에는 emailVerified=true 가 담긴다
        TokenResponse tokenResponse = accountAuthService.reissueTokensAfterEmailVerification(account);

        return ResponseEntity.ok(CommonApiResponse.ok("이메일 인증이 완료되었습니다.", tokenResponse));
    }

    // 인증 이메일 재발송
    @PostMapping("/resend-confirm-email")
    public ResponseEntity<CommonApiResponse<Void>> resendConfirmEmail(
            @RequestParam String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            return ResponseEntity.badRequest().body(CommonApiResponse.ok("존재하지 않는 이메일입니다."));
        }
        if (!account.canSendConfirmEmail()) {
            return ResponseEntity.badRequest().body(CommonApiResponse.ok("인증 이메일은 1시간에 한 번만 전송할 수 있습니다."));
        }
        signUpService.sendSignUpConfirmEmail(account);
        return ResponseEntity.ok(CommonApiResponse.ok("인증 이메일을 재발송했습니다."));
    }

    // 이메일 기반 로그인 링크 발송 (비밀번호 없는 로그인)
    @PostMapping("/email-login")
    public ResponseEntity<CommonApiResponse<Void>> sendEmailLoginLink(
            @RequestParam String email) {
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            return ResponseEntity.badRequest().body(CommonApiResponse.ok("존재하지 않는 이메일입니다."));
        }
        account.generateEmailCheckToken(); // 토큰 갱신
        accountAuthService.sendLoginLink(account);
        return ResponseEntity.ok(CommonApiResponse.ok("이메일 로그인 링크를 발송했습니다."));
    }

    // 이메일 로그인 링크 클릭 시 → JWT 발급
    @GetMapping("/login-by-email")
    public ResponseEntity<CommonApiResponse<TokenResponse>> loginByEmail(
            @RequestParam String token,
            @RequestParam String email) {

        Account account = accountRepository.findByEmail(email);

        // 예외를 던지면 GlobalExceptionHandler → ErrorResponse로 응답
        // 컨트롤러는 성공 케이스만 담당하므로 반환 타입이 일관됨
        if (account == null || !account.isValidToken(token)) {
            throw new IllegalArgumentException("로그인할 수 없습니다.");
        }

        TokenResponse tokenResponse = accountAuthService.loginByEmailToken(account);
        return ResponseEntity.ok(CommonApiResponse.ok(tokenResponse));
    }
}