package com.studyolle.adminfrontend.member.controller;

import com.studyolle.adminfrontend.member.client.AdminInternalClient;
import com.studyolle.adminfrontend.member.dto.AdminMemberDto;
import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 회원 관리 페이지 컨트롤러.
 *
 * 기존 frontend-service 의 AdminPageController 를 그대로 옮겨온 뒤 경로만 /admin/members 에서 /members 로 단축했다.
 * admin-frontend 는 관리자 전용이므로 경로에 /admin/ 접두사가 따로 필요 없다.
 * 이 사이트의 모든 페이지가 이미 관리자용이다.
 *
 * [담당 엔드포인트]
 *   GET  /members              회원 목록 페이지 렌더링
 *   POST /members/{id}/role    회원 권한 변경 (JS fetch 호출용, JSON 응답)
 *
 * 핸들러가 다섯 개를 넘어가면 별도 컨트롤러로 쪼개는 것도 한 방법이지만, 지금은 두 개라 한 파일에 두는 것이 충분히 읽기 쉽다.
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
        model.addAttribute("currentAccountId", accountId);

        return "members";
    }

    /**
     * POST /members/{id}/role
     *
     * 요청 본문(JSON): { "role": "ROLE_ADMIN" } 또는 { "role": "ROLE_USER" }
     * 응답(JSON): { "success": true, "role": "ROLE_ADMIN" } 또는 실패 시 { "success": false, "message": "..." }
     *
     * [POST 인데 PATCH 가 아닌 이유]
     * HTML 폼은 GET/POST 만 지원하므로, 브라우저 fetch 도 POST 로 받아두면
     * 나중에 form fallback 이 가능해진다. 내부적으로 admin-service 에는 PATCH 로 보낸다.
     *
     * @ResponseBody 로 JSON 을 직접 응답하는 이유:
     * JS fetch 가 호출하므로 페이지 리다이렉트 대신 결과를 JSON 으로 받아 화면을 동적으로 갱신하는 것이 자연스럽다.
     * 이러면 "권한 변경 후 페이지 깜빡임 없음" UX 를 얻을 수 있다.
     */
    @PostMapping("/members/{id}/role")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateMemberRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Nickname", required = false) String nickname,
            @RequestHeader(value = "X-Account-Role", required = false) String role) {

        // 권한 가드 — admin-gateway 의 OptionalJwtFilter 가 비로그인도 통과시키므로 직접 검증
        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "권한이 없습니다."));
        }

        // 자기 자신 권한 변경 금지 — admin-service 에서도 검증하지만 여기서 미리 차단
        if (accountId.equals(id)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "자기 자신의 권한은 변경할 수 없습니다."));
        }

        String newRole = body.get("role");
        if (newRole == null || newRole.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "role 값이 필요합니다."));
        }

        try {
            AdminMemberDto updated = adminInternalClient.updateMemberRole(id, newRole, accountId, nickname);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "role", updated.getRole(),
                    "nickname", updated.getNickname()
            ));
        } catch (Exception e) {
            log.error("권한 변경 실패: memberId={}, role={}", id, newRole, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "권한 변경에 실패했습니다: " + e.getMessage()));
        }
    }
}