package com.studyolle.modules.account.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.dto.SignUpForm;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.account.service.AccountAuthService;
import com.studyolle.modules.account.service.SignUpService;
import com.studyolle.modules.account.validator.SignUpFormValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

/**
 * 회원가입 전체 흐름을 담당하는 컨트롤러
 *
 * 담당 기능:
 *   - 회원가입 폼 렌더링 (GET /sign-up)
 *   - 회원가입 처리 (POST /sign-up)
 *   - 이메일 인증 토큰 확인 (GET /check-email-token)
 *   - 이메일 확인 안내 페이지 (GET /check-email)
 *   - 인증 이메일 재전송 (GET /resend-confirm-email)
 *
 * ──────────────────────────────────────────────────────────────────
 * [변경 이력] 이메일 미인증 사용자 로그인 차단 적용
 * ──────────────────────────────────────────────────────────────────
 *
 * 기존 흐름:
 *   회원가입 → 자동 로그인 → 홈 화면 (이메일 인증 배너만 표시)
 *   → 문제: 미인증 사용자도 로그인 상태이므로 모든 기능 사용 가능
 *
 * 변경 후 흐름:
 *   회원가입 → 이메일 확인 안내 페이지 → 이메일 인증 → 로그인 가능 → 미인증 사용자는 로그인 자체가 불가능 (UserAccount.enabled=false)
 *
 * 이 변경으로 인해 다음 메서드들이 수정됨:
 *   1. signUpSubmit()         : 자동 로그인 제거, 이메일 안내 페이지로 리다이렉트
 *   2. checkEmail()           : @CurrentUser 제거 → 쿼리 파라미터(email)로 변경
 *   3. resendConfirmEmail()   : @CurrentUser 제거 → 쿼리 파라미터(email)로 변경
 *
 * SecurityConfig도 함께 수정 필요:
 *   - /check-email, /resend-confirm-email을 permitAll()에 추가
 *   (비로그인 상태에서 접근해야 하므로)
 *
 * 의존 서비스:
 *   - SignUpService: 계정 생성, 인증 이메일 발송, 인증 완료 처리
 *   - AccountAuthService: 이메일 인증 완료 후 로그인 (가입 직후 자동 로그인은 제거됨)
 */
@Controller
@RequiredArgsConstructor
public class SignUpController {

    private final SignUpFormValidator signUpFormValidator;
    private final SignUpService signUpService;
    private final AccountAuthService accountAuthService;
    private final AccountRepository accountRepository;

    /**
     * WebDataBinder를 통해 SignUpForm에 대한 커스텀 Validator를 등록
     *
     * - @InitBinder("signUpForm")는 이름이 "signUpForm"인 @ModelAttribute에만 적용됨
     * - SignUpFormValidator는 이메일/닉네임 중복 검사 등 DB 조회가 필요한 검증 로직을 포함
     * - Spring의 @Valid와 함께 사용되어, Bean Validation + 커스텀 검증이 순차적으로 실행됨
     */
    @InitBinder("signUpForm")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(signUpFormValidator);
    }

    /**
     * 회원가입 폼 페이지 렌더링
     *
     * - 빈 SignUpForm 객체를 모델에 추가하여 Thymeleaf 폼 바인딩에 사용
     * - View: resources/templates/account/sign-up.html
     */
    @GetMapping("/sign-up")
    public String signUpForm(Model model) {
        model.addAttribute("signUpForm", new SignUpForm());
        return "account/sign-up";
    }

    /**
     * 회원가입 처리 (POST)
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 사항] 자동 로그인 제거 + 이메일 확인 안내 페이지로 리다이렉트
     * ──────────────────────────────────────────────────────────────────
     *
     * 기존 코드:
     *   Account account = signUpService.processNewAccount(signUpForm);
     *   accountAuthService.login(account);  ← 자동 로그인
     *   return "redirect:/";                ← 홈으로 이동
     *
     * 변경 이유:
     *   UserAccount 생성자에서 enabled = account.isEmailVerified()로 설정했기 때문에,
     *   회원가입 직후(emailVerified=false)에는 UserAccount.isEnabled()가 false이다.
     *
     *   이 상태에서 login()을 호출하면 enabled=false인 UserAccount가 SecurityContext에
     *   저장되는데, 이후 Spring Security의 일부 필터가 isEnabled()를 체크하면서
     *   예기치 않은 동작이 발생할 수 있다.
     *
     *   더 중요한 것은 UX 관점에서, 이메일 인증을 하지 않으면 로그인이 안 된다는
     *   정책을 사용자에게 명확히 전달하는 것이다.
     *
     * 변경 후 흐름:
     *   1. 계정 생성 + 인증 이메일 발송 (기존과 동일)
     *   2. 자동 로그인 하지 않음 (삭제)
     *   3. /check-email?email=xxx로 리다이렉트 → "이메일을 확인해주세요" 안내 표시
     *   4. 사용자가 이메일 인증 완료 후 → 로그인 가능
     *
     * @param signUpForm 사용자가 입력한 가입 정보 (닉네임, 이메일, 비밀번호)
     * @param errors     Bean Validation + 커스텀 검증 결과
     */
    @PostMapping("/sign-up")
    public String signUpSubmit(@Valid @ModelAttribute("signUpForm") SignUpForm signUpForm, Errors errors) {
        if (errors.hasErrors()) {
            return "account/sign-up";
        }

        // 계정 생성: 비밀번호 암호화, 이메일 인증 토큰 생성, 인증 이메일 발송까지 포함
        Account account = signUpService.processNewAccount(signUpForm);

        // [변경] 자동 로그인 제거 — 이메일 인증 전에는 로그인 불가
        // 기존: accountAuthService.login(account);

        // [변경] 이메일 확인 안내 페이지로 리다이렉트
        // 기존: return "redirect:/";
        // → 비로그인 상태이므로 @CurrentUser 대신 쿼리 파라미터로 이메일 전달
        return "redirect:/check-email?email=" + account.getEmail();
    }

    /**
     * 이메일 인증 링크 클릭 시 호출되는 핸들러
     *
     * - 사용자가 이메일로 전달받은 인증 URL을 클릭하면 이 메서드가 실행됨
     *   예시 URL: /check-email-token?token=ABC123&email=test@example.com
     *
     * 처리 흐름:
     *   1. 이메일로 Account 조회 → 없으면 "wrong.email" 에러
     *   2. 토큰 유효성 검증 → 실패하면 "wrong.token" 에러
     *   3. 모두 통과하면 → 계정 상태를 "인증 완료"로 변경 + 자동 로그인
     *
     * - View는 성공/실패 모두 동일한 템플릿(account/checked-email)을 사용하며,
     *   Model의 "error" 속성 존재 여부에 따라 렌더링이 달라짐
     *
     * - 인증 완료 후에는 completeSignUp() 내부에서 login()이 호출되어 자동 로그인됨
     *   이 시점에서는 emailVerified=true이므로 UserAccount.isEnabled()=true → 정상 로그인
     *
     * @param token 인증 링크에 포함된 이메일 검증 토큰
     * @param email 인증 링크에 포함된 사용자 이메일
     * @param model View에 전달할 데이터
     */
    @GetMapping("/check-email-token")
    public String checkEmailToken(String token, String email, Model model) {
        // [1] 이메일로 계정 조회
        Account account = accountRepository.findByEmail(email);
        if (account == null) {
            model.addAttribute("error", "wrong.email");
            return "account/checked-email";
        }

        // [2] 토큰 유효성 확인 (Account 도메인 메서드에 위임)
        if (!account.isValidToken(token)) {
            model.addAttribute("error", "wrong.token");
            return "account/checked-email";
        }

        // [3] 인증 완료 처리 + 자동 로그인
        //     - completeSignUp() 내부에서:
        //       1) account.completeSignUp() → emailVerified=true, joinedAt=now()
        //       2) accountAuthService.login(account) → SecurityContext에 인증 정보 설정
        //     - 이 시점에서 emailVerified=true이므로 UserAccount.isEnabled()=true
        //       → 정상적인 로그인 상태가 됨
        signUpService.completeSignUp(account);

        // [4] 성공 정보를 View에 전달
        model.addAttribute("numberOfUser", accountRepository.count());
        model.addAttribute("nickname", account.getNickname());
        return "account/checked-email";
    }

    /**
     * 이메일 확인 안내 페이지
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 사항] @CurrentUser Account → @RequestParam email
     * ──────────────────────────────────────────────────────────────────
     *
     * 기존 코드:
     *   public String checkEmail(@CurrentUser Account account, Model model) {
     *       model.addAttribute("email", account.getEmail());
     *       ...
     *   }
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 이유 — @CurrentUser가 동작하려면 SecurityContext가 필요하다]
     * ──────────────────────────────────────────────────────────────────
     *
     * @CurrentUser의 동작 원리:
     *   @CurrentUser는 내부적으로 @AuthenticationPrincipal을 사용하여
     *   SecurityContext → Authentication → Principal(UserAccount) → Account를 꺼낸다.
     *   즉, SecurityContext에 인증 정보가 저장되어 있어야만 Account를 반환할 수 있다.
     *   SecurityContext가 비어있으면 → null이 반환된다.
     *
     * SecurityContext에 인증 정보는 "언제" 저장되는가:
     *   AccountAuthService.login(account) 메서드가 호출될 때 저장된다.
     *   login()은 UsernamePasswordAuthenticationToken을 생성하여
     *   SecurityContextHolder.getContext().setAuthentication(token)으로 저장한다.
     *
     * ──────────────────────────────────────────────────────────────────
     * [기존 흐름에서 @CurrentUser가 동작했던 이유]
     * ──────────────────────────────────────────────────────────────────
     *
     *   POST /sign-up (회원가입)
     *       │
     *       ▼
     *   processNewAccount()     → Account 생성
     *       │
     *       ▼
     *   login(account)          → SecurityContext에 UserAccount 저장 ★
     *       │
     *       ▼
     *   redirect:/check-email
     *       │
     *       ▼
     *   checkEmail(@CurrentUser Account account)
     *       → SecurityContext에 UserAccount가 있음
     *       → account = 실제 Account 객체 ✅
     *
     *   login()이 SecurityContext에 인증 정보를 넣어줬기 때문에
     *   /check-email 페이지에서 @CurrentUser로 Account를 꺼낼 수 있었다.
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 후 흐름에서 @CurrentUser가 동작하지 않는 이유]
     * ──────────────────────────────────────────────────────────────────
     *
     *   POST /sign-up (회원가입)
     *       │
     *       ▼
     *   processNewAccount()     → Account 생성
     *       │
     *       ▼
     *   login() 호출 안 함!     → SecurityContext가 비어있음 ★
     *       │
     *       ▼
     *   redirect:/check-email?email=test@example.com
     *       │
     *       ▼
     *   checkEmail(@CurrentUser Account account)
     *       → SecurityContext가 비어있음
     *       → account = null ❌
     *       → account.getEmail()에서 NullPointerException 발생!
     *
     *   자동 로그인(login())을 제거했으므로 SecurityContext에 아무것도 없다.
     *   @CurrentUser는 SecurityContext에서 인증 정보를 꺼내는 어노테이션이므로
     *   비어있으면 null을 반환하고, 이후 null.getEmail() 호출 시 NPE가 발생한다.
     *
     * ──────────────────────────────────────────────────────────────────
     * [해결 — @RequestParam으로 이메일을 직접 전달]
     * ──────────────────────────────────────────────────────────────────
     *
     *   @RequestParam은 SecurityContext와 무관하게 URL 쿼리 파라미터에서 값을 직접 바인딩하는 어노테이션이다.
     *
     *   signUpSubmit()에서 리다이렉트할 때 이메일을 URL에 담아 보내면:
     *     return "redirect:/check-email?email=" + account.getEmail();
     *
     *   이 메서드에서 쿼리 파라미터로 받을 수 있다:
     *     public String checkEmail(@RequestParam String email, Model model)
     *
     *   로그인 여부와 무관하게 이메일을 전달할 수 있으므로
     *   비로그인 상태에서도 정상 동작한다. ✅
     *
     * ──────────────────────────────────────────────────────────────────
     * [핵심 정리]
     * ──────────────────────────────────────────────────────────────────
     *
     *   @CurrentUser  : SecurityContext에 의존 → login() 이후에만 사용 가능
     *   @RequestParam : URL 파라미터에 의존 → 로그인 여부와 무관하게 사용 가능
     *
     *   이메일 인증은 login() 이전 단계이므로 @CurrentUser를 쓸 수 없다.
     *   따라서 로그인 없이도 값을 전달할 수 있는 @RequestParam으로 변경했다.
     *
     * SecurityConfig 변경 필요:
     *   /check-email을 permitAll()에 추가해야 함 (비로그인 상태에서 접근하므로)
     *
     * @param email 가입 시 사용한 이메일 (쿼리 파라미터)
     * @param model View에 전달할 데이터
     */
    @GetMapping("/check-email")
    public String checkEmail(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "account/check-email";
    }

    /**
     * 인증 이메일 재전송 요청 처리
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 사항] @CurrentUser Account → @RequestParam email
     * ──────────────────────────────────────────────────────────────────
     *
     * 기존 코드:
     *   public String resendConfirmEmail(@CurrentUser Account account, Model model) {
     *       if (!account.canSendConfirmEmail()) { ... }
     *       signUpService.sendSignUpConfirmEmail(account);
     *       return "redirect:/";
     *   }
     *
     * 변경 이유:
     *   checkEmail()과 동일 — 비로그인 상태에서 접근하므로 @CurrentUser 사용 불가
     *
     * 해결:
     *   이메일을 쿼리 파라미터로 받아 DB에서 Account를 조회한 후 처리한다.
     *
     * 보안 고려:
     *   - 이메일만으로 재전송을 요청할 수 있으므로, 악의적인 사용자가 타인의 이메일로 대량 전송을 시도할 수 있다.
     *   - 이를 방지하기 위해 canSendConfirmEmail()의 1시간 재전송 제한이 적용된다.
     *   - 추가로 IP 기반 Rate Limiting을 도입하면 더 안전하다.
     *
     * SecurityConfig 변경 필요:
     *   /resend-confirm-email을 permitAll()에 추가해야 함
     *
     * @param email 재전송 대상 이메일 (쿼리 파라미터)
     * @param model View에 전달할 데이터
     */
    @GetMapping("/resend-confirm-email")
    public String resendConfirmEmail(@RequestParam String email, Model model) {
        // [1] 이메일로 Account 조회
        Account account = accountRepository.findByEmail(email);

        // 존재하지 않는 이메일이면 에러 처리
        if (account == null) {
            model.addAttribute("error", "해당 이메일로 가입된 계정이 없습니다.");
            model.addAttribute("email", email);
            return "account/check-email";
        }

        // [2] 1시간 재전송 제한 체크
        if (!account.canSendConfirmEmail()) {
            model.addAttribute("error", "인증 이메일은 1시간에 한번만 전송할 수 있습니다.");
            model.addAttribute("email", email);
            return "account/check-email";
        }

        // [3] 인증 이메일 재발송
        signUpService.sendSignUpConfirmEmail(account);

        // [변경] 이메일 확인 안내 페이지로 리다이렉트 (기존: redirect:/)
        return "redirect:/check-email?email=" + email;
    }
}