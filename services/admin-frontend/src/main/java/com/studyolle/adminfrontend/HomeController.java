package com.studyolle.adminfrontend;

import com.studyolle.adminfrontend.member.client.AdminInternalClient;
import com.studyolle.adminfrontend.member.dto.AdminMemberDto;
import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * admin-frontend 의 최상위 페이지 컨트롤러.
 *
 * 세 가지 라우트를 담당한다:
 *   GET /           로그인 화면 또는 대시보드 리다이렉트
 *   GET /dashboard  관리자 대시보드
 *   GET /logout     쿠키 삭제 후 로그인 화면으로 복귀
 *
 * [권한 검사가 여기서 수동으로 이루어지는 이유]
 * admin-gateway 에서는 /** 페이지 경로에 OptionalJwtFilter 만 적용하고 있다.
 * 이 필터는 이름 그대로 "토큰이 없어도 통과시키는" 느슨한 필터라서, 비로그인 상태에서도 어떤 경로든 이 컨트롤러까지 도달한다.
 * 그래야 비로그인 사용자가 로그인 화면(/) 에 접근할 수 있기 때문이다.
 *
 * 그런데 그 대가로 /dashboard 나 /members 같은 보호 경로에도 비로그인 사용자가 도달해버리므로,
 * 각 핸들러가 X-Account-Id 와 X-Account-Role 헤더를 직접 확인해야 한다.
 * 이 검사 로직을 여러 컨트롤러에 반복해서 쓰는 것이 보기 싫다면 나중에 인터셉터나 Spring AOP 로 공통화할 수도 있지만,
 * 지금은 컨트롤러가 두 개뿐이고 검사 로직이 세 줄 정도라 직접 써두는 것이 가장 읽기 쉽다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AdminInternalClient adminInternalClient;

    // application.yml 의 app.api-base-url 값을 주입받아 login.html 의 JS 에 넘긴다.
    // 이렇게 하면 나중에 게이트웨이 주소가 바뀌어도 yml 만 수정하면 된다.
    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    /**
     * GET /
     * <p>
     * 비로그인(또는 토큰 만료) 상태: 로그인 페이지를 보여준다.
     * 관리자 토큰으로 로그인 상태: 곧바로 /dashboard 로 보낸다.
     * 일반 사용자 토큰(ROLE_USER)으로 접근: 로그인 페이지를 다시 보여준다.
     * — 이 경우는 실제로는 발생하기 어렵다.
     * login.html 의 JS 가 관리자가 아닌 토큰을 애초에 저장하지 않기 때문이다.
     * 그래도 방어적으로 로그인 페이지로 돌려보낸다.
     */
    @GetMapping("/")
    public String index(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Role", required = false) String role,
            Model model) {

        if (accountId != null && "ROLE_ADMIN".equals(role)) {
            return "redirect:/dashboard";
        }
        // 로그인 페이지 렌더링 — apiBase 는 JS fetch 에서 사용한다
        model.addAttribute("apiBase", apiBaseUrl);
        return "login";
    }

    /**
     * GET /dashboard
     *
     * 관리자 대시보드. 권한 검사를 통과한 뒤 간단한 통계 데이터를 모아서 템플릿에 넘긴다.
     *
     * [현재 가져오는 통계]
     * - totalMembers: admin-service 의 /api/admin/members 를 size=1 로 호출해 totalElements 만 사용
     *
     * [왜 size=1 로 호출하는가]
     * 대시보드에 필요한 것은 전체 회원 수 하나뿐이다.
     * 굳이 size=20 으로 호출해서 20명의 상세 데이터를 다 받아올 필요가 없다.
     * totalElements 는 페이지 크기와 무관하게 전체 개수를 알려주므로 size=1 만으로도 정확한 값을 얻을 수 있다.
     * 네트워크 트래픽을 최소화하고 불필요한 데이터 직렬화 비용을 줄이는 기법이다.
     *
     * 만약 나중에 dedicated 한 count-only API (GET /api/admin/members/count) 를 만들면 더 효율적이 된다.
     * 지금은 그것을 만들 가치가 없을 정도로 부하가 작으므로 생략한다.
     */
    @GetMapping("/dashboard")
    public Object dashboard(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Role", required = false) String role,
            @RequestHeader(value = "X-Account-Nickname", required = false) String nickname,
            Model model) {

        // 권한 검사 — 비로그인이거나 관리자가 아니면 루트로 돌려보낸다.
        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            log.info("비관리자의 /dashboard 접근 차단: accountId={}, role={}", accountId, role);
            return "redirect:/";
        }

        // 전체 회원 수 조회 — admin-service 에 위임한다.
        // 만약 admin-service 가 잠시 다운되어 있어도 대시보드가 완전히 깨지지 않도록
        // try/catch 로 감싸 실패 시 "—" 표시로 떨어지게 한다.
        long totalMembers = 0;
        try {
            AdminPageResponse<AdminMemberDto> page = adminInternalClient.listMembers(null, 0, 1, accountId, nickname);
            totalMembers = page.getTotalElements();
        } catch (Exception e) {
            log.warn("전체 회원 수 조회 실패 — 대시보드는 계속 렌더링한다", e);
        }

        model.addAttribute("nickname", nickname);
        model.addAttribute("totalMembers", totalMembers);
        return "dashboard";
    }

    /**
     * GET /logout
     * <p>
     * 서버 사이드에서 쿠키를 만료시키고 로그인 페이지로 리다이렉트한다.
     * <p>
     * [왜 JS 의 document.cookie 삭제만으로는 부족한가]
     * 브라우저마다 JS 에서 쿠키를 삭제하는 동작이 조금씩 다르다.
     * 특히 Safari 는 SameSite 속성과 관련해 JS 쿠키 조작을 제한적으로 적용하는 경우가 있다.
     * 서버가 Set-Cookie 헤더로 max-age=0 을 내려주면 브라우저는 반드시 그 쿠키를 제거하므로 훨씬 확실하다.
     * 따라서 로그아웃은 반드시 서버 라운드트립을 동반하도록 /logout 엔드포인트를 경유시킨다.
     */
    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("accessToken", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/";
    }
}