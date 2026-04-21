package com.studyolle.frontend.account.controller;

import com.studyolle.frontend.account.client.AccountInternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AuthPageController
 *
 * 역할: 인증 관련 HTML 페이지를 서빙한다.
 *
 * [모노리틱과의 핵심 차이]
 * - 모노리틱: POST 요청도 처리했다. 폼 제출 → 서버 검증 → 성공/실패 뷰 렌더링.
 *             @ModelAttribute, BindingResult, RedirectAttributes 등을 사용했다.
 *
 * - MSA:      GET 요청만 처리한다 (HTML 페이지 전달만).
 *             POST 처리(로그인, 회원가입 등)는 브라우저 JS 가
 *             api-gateway(:8080) 에 직접 JSON API 를 호출해서 수행한다.
 *             컨트롤러는 비즈니스 로직을 전혀 모른다.
 *
 * [apiBase 주입 방식]
 * - application.yml 의 app.api-base-url 을 @Value 로 주입받아
 *   모든 페이지 Model 에 ${apiBase} 로 전달한다.
 * - 각 HTML 의 <script th:inline="javascript"> 블록에서
 *   const API_BASE = [[${apiBase}]]; 로 JS 전역 변수에 삽입된다.
 * - 덕분에 운영 배포 시 application.yml 만 바꾸면 되고 HTML 파일은 수정이 불필요하다.
 */
@Controller
@RequiredArgsConstructor
public class AuthPageController {

    // application.yml: app.api-base-url
    // 개발: http://localhost:8080  |  운영: https://api.studyolle.com
    @Value("${app.api-base-url}")
    private String apiBase;

    private final AccountInternalClient accountInternalClient;

    // 공통 헬퍼: 모든 페이지 렌더링 시 apiBase 를 Model 에 추가한다.
    private void addApiBase(Model model) {
        model.addAttribute("apiBase", apiBase);
    }

    /**
     * 로그인 페이지
     * GET /login -> templates/login.html
     */
    @GetMapping("/login")
    public String loginPage(Model model) {
        addApiBase(model);
        return "login";
    }

    /**
     * 회원가입 페이지
     * GET /sign-up -> templates/account/sign-up.html
     */
    @GetMapping("/sign-up")
    public String signUpPage(Model model) {
        addApiBase(model);
        return "account/sign-up";
    }

    /**
     * 이메일 인증 안내 페이지 (회원가입 직후 표시)
     * GET /check-email -> templates/account/check-email.html
     *
     * [흐름]
     * sign-up.html 의 JS 가 회원가입 성공 후
     * location.href = '/check-email?email=' + encodeURIComponent(email) 로 이동.
     * 이 컨트롤러가 email 을 받아 Model 에 넣으면 Thymeleaf 가 ${email} 로 표시한다.
     *
     * @param email 회원가입 시 입력한 이메일 (선택적 - 없으면 빈 안내 페이지)
     */
    @GetMapping("/check-email")
    public String checkEmailPage(@RequestParam(required = false) String email,
                                 Model model) {
        addApiBase(model);
        model.addAttribute("email", email);
        return "account/check-email";
    }

    /**
     * 이메일 토큰 검증 결과 페이지
     * GET /check-email-token -> templates/account/check-email-token.html
     *
     * [흐름]
     * 1. account-service 가 발송하는 인증 이메일의 링크:
     *    http://localhost:8090/check-email-token?token=UUID&email=xxx
     *    (account-service 의 app.host = http://localhost:8090 으로 설정 필요)
     *
     * 2. 사용자가 이메일 링크 클릭 → 이 컨트롤러가 HTML 을 내려준다.
     *
     * 3. 페이지 로드 후 JS 가 URL 파라미터의 token, email 을 읽어
     *    GET /api/auth/check-email-token?token=...&email=... 를 호출한다.
     *
     * 4. 성공이면 JWT 를 localStorage 에 저장 후 성공 화면 표시,
     *    실패면 에러 화면 표시.
     *
     * @param token URL 파라미터 (UUID - account-service 의 emailCheckToken)
     * @param email URL 파라미터 (사용자 이메일)
     */
    @GetMapping("/check-email-token")
    public String checkEmailTokenPage(@RequestParam(required = false) String token,
                                      @RequestParam(required = false) String email,
                                      Model model) {
        addApiBase(model);
        // token 과 email 은 JS 가 URL 파라미터에서 직접 읽으므로 Model 불필요.
        // 단, 서버사이드 디버깅 및 향후 확장 대비로 전달해둔다.
        model.addAttribute("token", token);
        model.addAttribute("email", email);
        return "account/check-email-token";
    }

    /**
     * 패스워드 없이 로그인 (이메일 링크 로그인) 요청 페이지
     * GET /email-login -> templates/account/email-login.html
     */
    @GetMapping("/email-login")
    public String emailLoginPage(Model model) {
        addApiBase(model);
        return "account/email-login";
    }

    /**
     * 이메일 인증 필수 기능 접근 차단 안내 페이지.
     * GET /check-email-required -> templates/check-email-required.html
     *
     * [언제 여기로 오는가]
     * EmailVerifiedInterceptor 가 인증 미완료 사용자의 /new-study, /settings/** 등
     * 인증 필수 경로 접근을 막고 이 페이지로 리다이렉트한다.
     * 사용자가 URL 을 직접 쳐서 오는 경우도 있을 수 있다.
     *
     * [다른 컨트롤러 메서드와 다른 점 — account 를 Model 에 주입]
     * 이 페이지는 "로그인은 했는데 이메일 인증만 안 한" 사용자를 대상으로 한다.
     * 즉 accountId 가 반드시 존재하는 상태이며, 페이지에 현재 계정의 이메일 주소를
     * 표시해야 하므로 account 정보가 필요하다.
     *
     * accountInternalClient 주입을 피하고 템플릿 렌더링만 담당하려면 HomeController 처럼
     * @RequestHeader("X-Account-Id") 와 AccountInternalClient 를 합쳐서 model.account 를 채워야 한다.
     * 현재 AuthPageController 는 그런 의존성이 없으므로, 이 메서드는 간단히 accountId 만 Model 에 넣고
     * HTML 에서 @{apiBase} 를 통해 AJAX 로 이메일을 가져오게 할 수도 있었지만,
     * 일관성을 위해 서버에서 먼저 account 를 꺼내는 방식을 택했다.
     *
     * → 이를 위해 AccountInternalClient 를 이 컨트롤러에 주입해야 한다.
     *
     * @param accountId api-gateway 가 주입한 X-Account-Id (이 페이지는 로그인 전제이므로 required = false 로 둬 안전망 확보)
     */
    @GetMapping("/check-email-required")
    public String checkEmailRequiredPage(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model) {

        addApiBase(model);

        // 비로그인 사용자가 직접 이 URL 로 온 경우 홈으로 보낸다.
        // 정상 흐름 (인터셉터 리다이렉트) 에서는 절대 null 일 수 없지만 방어적 처리.
        if (accountId == null) {
            return "redirect:/";
        }

        // account 정보를 가져와 템플릿에 넘긴다.
        // navigation 바의 프로필 드롭다운과 안내 카드의 이메일 표시에 사용된다.
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));

        return "check-email-required";
    }
}
