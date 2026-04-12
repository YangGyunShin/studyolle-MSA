package com.studyolle.frontend.admin.controller;

import com.studyolle.frontend.admin.client.AdminInternalClient;
import com.studyolle.frontend.admin.dto.AdminMemberDto;
import com.studyolle.frontend.admin.dto.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 관리자 HTML 페이지 컨트롤러.
 *
 * [왜 /api/admin/** 이 아니라 /admin/** 인가]
 * /api/admin/** 은 api-gateway 가 lb://ADMIN-SERVICE 로 라우팅하는 REST API 경로다.
 * 사용자가 브라우저로 보는 HTML 페이지는 frontend-service 가 Thymeleaf 로 렌더링하므로
 * 경로가 달라야 한다. 두 경로는 역할과 대상이 다르다:
 *
 *   /admin/members       → frontend-service 가 렌더링하는 HTML 페이지 (이 컨트롤러)
 *   /api/admin/members   → admin-service 가 반환하는 REST API (AdminMemberController)
 *
 * [접근 제어가 여기서 직접 이루어지는 이유]
 * 이 컨트롤러의 경로는 frontend-service 라우트이고, api-gateway 에서 OptionalJwtFilter 만
 * 거친다. OptionalJwtFilter 는 이름 그대로 "토큰이 없어도 통과시키는" 느슨한 필터라서
 * 비로그인 사용자나 일반 사용자도 /admin/members 경로에 도달할 수 있다. 따라서 여기서 직접
 * role 을 확인하고 관리자가 아니면 홈으로 돌려보내야 한다.
 *
 * [반환 타입이 Object 인 이유]
 * 정상 경로에서는 템플릿 이름 문자열("admin/members") 을 반환하지만, 비관리자일 때는
 * RedirectView 를 반환해야 한다. 두 반환 타입이 다르므로 공통 부모 타입인 Object 를 쓴다.
 * Spring MVC 는 반환 객체의 실제 타입을 보고 처리 방식을 결정하므로 문제없이 동작한다.
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPageController {

    private final AdminInternalClient adminInternalClient;

    /**
     * GET /admin/members
     *
     * 관리자 회원 목록 페이지.
     * 쿼리 파라미터로 keyword, page, size 를 받아 admin-service 에 위임한다.
     */
    @GetMapping("/members")
    public Object members(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Nickname", required = false) String nickname,
            @RequestHeader(value = "X-Account-Role", required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // 비로그인 또는 비관리자 접근 차단 — 홈으로 리다이렉트
        // 여기서 403 Forbidden 을 반환하는 것도 대안이지만, UX 적으로는 조용히 홈으로
        // 보내는 편이 자연스럽다. 관리자 경로의 존재 자체를 노출하지 않는 보안 효과도 있다.
        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            log.info("비관리자의 /admin/members 접근 차단: accountId={}, role={}", accountId, role);
            return new RedirectView("/");
        }

        // admin-service 호출 — 내부적으로 account-service 까지 한 번 더 호출된다.
        // 전체 체인: 브라우저 → api-gateway → frontend-service → admin-service → account-service
        AdminPageResponse<AdminMemberDto> result =
                adminInternalClient.listMembers(keyword, page, size, accountId, nickname);

        // 템플릿에 필요한 모델 속성 주입
        // fragments 의 main-nav 가 account 객체를 요구하지만, GlobalModelAttributes 가
        // 이미 account 와 unreadNotificationCount 를 모든 페이지에 자동 주입하므로
        // 여기서는 페이지 고유 데이터만 넘기면 된다.
        model.addAttribute("members", result.getContent());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("keyword", keyword == null ? "" : keyword);

        // 템플릿 경로: templates/admin/members.html
        return "admin/members";
    }
}