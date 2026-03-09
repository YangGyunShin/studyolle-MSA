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

    // 이메일 인증 링크 클릭 시 호출 (이메일 본문의 링크 URL)
    @GetMapping("/check-email-token")
    public ResponseEntity<CommonApiResponse<Void>> checkEmailToken(
            @RequestParam String token,
            @RequestParam String email) {

        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            throw new IllegalArgumentException("존재하지 않는 이메일입니다.");
        }
        if (!account.isValidToken(token)) {
            throw new IllegalArgumentException("유효하지 않은 인증 토큰입니다.");
        }
        signUpService.completeSignUp(account);
        return ResponseEntity.ok(CommonApiResponse.ok("이메일 인증이 완료되었습니다. 로그인해주세요."));
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