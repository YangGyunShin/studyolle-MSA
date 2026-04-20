package com.studyolle.adminfrontend.study.controller;

import com.studyolle.adminfrontend.member.dto.AdminPageResponse;
import com.studyolle.adminfrontend.study.client.AdminStudyClient;
import com.studyolle.adminfrontend.study.dto.AdminStudyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 스터디 관리 페이지 컨트롤러.
 *
 * [담당 엔드포인트]
 *   GET  /studies                 스터디 목록 페이지 렌더링
 *   POST /studies/{path}/force-close  강제 비공개 처리 (JS fetch, JSON 응답)
 *
 * 회원 관리의 AdminMemberController 와 동일한 패턴을 따른다. 권한 가드, 토스트
 * UX 를 위한 JSON 응답, 사용자 친화적 에러 메시지 등이 모두 복사-변형 관계다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminStudyController {

    private final AdminStudyClient adminStudyClient;

    /**
     * GET /studies
     * <p>
     * admin-service 에 위임해 목록을 받아온 뒤 템플릿에 필요한 모델 속성을 채운다.
     */
    @GetMapping("/studies")
    public Object studies(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Nickname", required = false) String nickname,
            @RequestHeader(value = "X-Account-Role", required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // 권한 가드 — OptionalJwtFilter 는 비로그인도 통과시키므로 직접 검증
        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            log.info("비관리자의 /studies 접근 차단: accountId={}, role={}", accountId, role);
            return "redirect:/";
        }

        AdminPageResponse<AdminStudyDto> result = adminStudyClient.listStudies(keyword, page, size, accountId, nickname);

        model.addAttribute("studies", result.getContent());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("nickname", nickname);

        return "studies";
    }

    /**
     * POST /studies/{path}/force-close
     *
     * 요청 본문 없음. 브라우저 fetch 가 호출하는 AJAX 엔드포인트이며 JSON 으로 응답한다.
     *
     * @ResponseBody 로 JSON 을 직접 내려주는 이유는 회원 권한 변경과 동일하다 — 페이지
     * 리다이렉트 없이 행을 즉석에서 갱신해 깜빡임 없는 UX 를 주기 위함이다.
     *
     * 요청 본문이 비어있어도 Spring 은 정상 동작한다. 본문이 없는 POST 는 HTTP 스펙상
     * 허용되며, 여기서는 단일 동작(강제 종료) 의 의미가 URL 에 충분히 드러난다.
     */
    @PostMapping("/studies/{path}/force-close")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceCloseStudy(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            @RequestHeader(value = "X-Account-Nickname", required = false) String nickname,
            @RequestHeader(value = "X-Account-Role", required = false) String role) {

        if (accountId == null || !"ROLE_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "권한이 없습니다."));
        }

        try {
            AdminStudyDto updated = adminStudyClient.forceCloseStudy(path, accountId, nickname);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "closed", updated.isClosed(),
                    "title", updated.getTitle()
            ));
        } catch (Exception e) {
            log.error("강제 비공개 처리 실패: path={}", path, e);
            // e.getMessage() 가 admin-service 의 사용자 친화적 메시지를 포함하고 있다
            // (예: "이미 종료된 스터디는..." / "스터디를 찾을 수 없습니다..." 등)
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "message", "강제 비공개 처리에 실패했습니다: " + e.getMessage()
                    ));
        }
    }
}