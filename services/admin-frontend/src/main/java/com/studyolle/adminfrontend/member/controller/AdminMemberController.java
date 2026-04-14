package com.studyolle.adminfrontend.member.controller;

import com.studyolle.adminfrontend.member.client.AdminInternalClient;
import com.studyolle.adminfrontend.member.dto.AdminMemberDto;
import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 회원 관리 페이지 컨트롤러.
 *
 * 기존 frontend-service 의 AdminPageController 를 그대로 옮겨온 뒤 경로만 /admin/members 에서 /members 로 단축했다.
 * admin-frontend 는 관리자 전용이므로 경로에 /admin/ 접두사가 따로 필요 없다.
 * 이 사이트의 모든 페이지가 이미 관리자용이다.
 *
 * [단일 책임]
 * 이 컨트롤러는 /members 한 경로만 담당한다.
 * 나중에 회원 상세 페이지 /members/{id} 나 권한 변경 처리 POST /members/{id}/role 이 추가되면 이 파일에 함께 둘 수도 있고,
 * 핸들러가 다섯 개를 넘어가면 별도 컨트롤러로 쪼개는 것도 한 방법이다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminInternalClient adminInternalClient;

    /**
     * GET /members
     *
     * keyword/page/size 쿼리 파라미터를 받아 admin-service 에 위임하고 결과를 템플릿에 넘긴다.
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

        // 권한 검사 — HomeController 와 동일한 패턴.
        // admin-gateway 의 OptionalJwtFilter 가 토큰이 없어도 통과시키기 때문에
        // 여기서 직접 확인해야 비로그인 사용자가 회원 목록을 보지 못하게 막을 수 있다.
        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            log.info("비관리자의 /members 접근 차단: accountId={}, role={}", accountId, role);
            return "redirect:/";
        }

        // admin-service 호출 — 내부적으로 account-service 까지 한 번 더 호출된다.
        AdminPageResponse<AdminMemberDto> result = adminInternalClient.listMembers(keyword, page, size, accountId, nickname);

        model.addAttribute("members", result.getContent());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("nickname", nickname);   // 네비게이션 바에서 사용

        return "members";
    }
}