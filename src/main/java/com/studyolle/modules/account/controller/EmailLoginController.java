package com.studyolle.modules.account.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.account.service.AccountAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ✅ 이메일 기반 로그인(패스워드 없는 로그인)을 담당하는 컨트롤러
 *
 * 담당 기능:
 *   - 이메일 로그인 폼 렌더링 (GET /email-login)
 *   - 로그인 링크 이메일 발송 (POST /email-login)
 *   - 이메일 로그인 링크 클릭 시 인증 처리 (GET /login-by-email)
 *
 * 설계 의도:
 *   - 비밀번호 분실 등의 상황에서 이메일만으로 로그인할 수 있는 대안 인증 경로
 *   - 일반 로그인(폼 기반)과 독립된 인증 흐름이므로 별도의 컨트롤러로 분리
 *   - 내부적으로는 이메일 인증 토큰을 생성하고, 해당 토큰이 포함된 URL을 이메일로 발송
 *
 * 의존 서비스:
 *   - AccountAuthService: 로그인 처리, 로그인 링크 발송
 */
@Controller
@RequiredArgsConstructor
public class EmailLoginController {

    private final AccountAuthService accountAuthService;
    private final AccountRepository accountRepository;

    /**
     * ✅ 이메일 로그인 폼 페이지 렌더링
     *
     * - 사용자가 이메일 주소를 입력하여 로그인 링크를 요청할 수 있는 화면
     * - View: resources/templates/account/email-login.html
     */
    @GetMapping("/email-login")
    public String emailLoginForm() {
        return "account/email-login";
    }

    /**
     * ✅ 이메일 로그인 링크 발송 처리 (POST)
     *
     * 처리 흐름:
     *   1. 사용자가 입력한 이메일로 Account 조회
     *   2. 계정이 존재하지 않으면 → 에러 메시지와 함께 폼으로 되돌아감
     *   3. 계정이 존재하면 → AccountAuthService에서 로그인 토큰 생성 + 이메일 발송
     *   4. 성공 시 Flash Attribute로 안내 메시지 전달 후 리다이렉트 (Post/Redirect/Get)
     *
     * 보안 고려사항:
     *   - canSendConfirmEmail() 호출을 활성화하면 1시간 내 중복 요청을 차단 가능
     *   - 현재는 비활성화 상태 (주석 처리됨)
     *
     * @param email              사용자가 폼에서 입력한 이메일 주소 (name 속성으로 자동 바인딩)
     * @param model              에러 메시지 전달용 모델
     * @param redirectAttributes 리다이렉트 시 1회성 메시지 전달용 (Flash Attribute)
     */
    @PostMapping("/email-login")
    public String sendEmailLoginLink(String email, Model model, RedirectAttributes redirectAttributes) {
        Account account = accountRepository.findByEmail(email);

        if (account == null) {
            model.addAttribute("error", "유효한 이메일 주소가 아닙니다.");
            return "account/email-login";
        }

        // 로그인 링크 이메일 발송
        // 내부적으로: 토큰 생성 → Thymeleaf 템플릿으로 이메일 본문 렌더링 → EmailService로 발송
        accountAuthService.sendLoginLink(account);

        redirectAttributes.addFlashAttribute("message", "이메일 인증 메일을 발송했습니다.");
        return "redirect:/email-login";
    }

    /**
     * ✅ 이메일 로그인 링크 클릭 시 호출되는 인증 처리 핸들러
     *
     * - 사용자가 이메일에서 받은 로그인 URL을 클릭하면 이 메서드가 실행됨
     *   예시 URL: /login-by-email?token=ABC123&email=test@example.com
     *
     * 처리 흐름:
     *   1. 이메일로 Account 조회
     *   2. 계정이 없거나 토큰이 유효하지 않으면 → 에러 메시지와 함께 결과 화면 렌더링
     *   3. 모두 유효하면 → SecurityContext에 인증 정보를 설정하여 로그인 처리
     *
     * - 성공/실패 모두 동일한 View(account/logged-in-by-email)를 사용하며,
     *   "error" 속성 존재 여부에 따라 화면이 달라짐
     *
     * @param token 이메일 인증 링크에 포함된 로그인 토큰
     * @param email 이메일 인증 링크에 포함된 사용자 이메일
     * @param model 에러 메시지 전달용 모델
     */
    @GetMapping("/login-by-email")
    public String loginByEmail(String token, String email, Model model) {
        Account account = accountRepository.findByEmail(email);
        String view = "account/logged-in-by-email";

        if (account == null || !account.isValidToken(token)) {
            model.addAttribute("error", "로그인할 수 없습니다.");
            return view;
        }

        // SecurityContextHolder에 인증 정보 설정 + 세션에 저장
        accountAuthService.login(account);
        return view;
    }
}