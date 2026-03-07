package com.studyolle.modules.admin.controller;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.security.CurrentUser;
import com.studyolle.modules.admin.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 플랫폼 관리자 전용 컨트롤러
 *
 * - SecurityConfig에서 /admin/** 에 hasRole("ADMIN") 접근 제어가 설정되어 있으므로
 *   일반 사용자는 이 컨트롤러의 어떤 엔드포인트에도 접근 불가
 * - @PreAuthorize 없이도 SecurityFilterChain에서 차단됨
 *
 * URL 구조:
 *   /admin              -> 대시보드
 *   /admin/members      -> 회원 목록
 *   /admin/members/{id} -> 회원 상세
 *   /admin/studies      -> 스터디 관리
 *   /admin/statistics   -> 플랫폼 전체 통계
 *   /admin/privacy      -> 개인정보처리내역
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ──────────────────────────────────────────────
    // 대시보드
    // ──────────────────────────────────────────────

    @GetMapping
    public String dashboard(@CurrentUser Account account, Model model) {
        model.addAttribute("stats", adminService.getDashboardStats());
        model.addAttribute("recentMembers", adminService.getRecentMembers(5));
        return "admin/dashboard";
    }

    // ──────────────────────────────────────────────
    // 회원 관리
    // ──────────────────────────────────────────────

    @GetMapping("/members")
    public String members(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        model.addAttribute("memberPage", adminService.searchMembers(keyword, pageable));
        model.addAttribute("keyword", keyword);
        return "admin/members";
    }

    @GetMapping("/members/{id}")
    public String memberDetail(@PathVariable Long id, Model model) {
        model.addAttribute("member", adminService.getMemberDetail(id));
        return "admin/member-detail";
    }

    @PostMapping("/members/{id}/verify-email")
    public String forceVerifyEmail(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.forceVerifyEmail(id);
        redirectAttributes.addFlashAttribute("message", "이메일 인증이 완료 처리되었습니다.");
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/toggle-enabled")
    public String toggleEnabled(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        boolean nowEnabled = adminService.toggleAccountEnabled(id);
        redirectAttributes.addFlashAttribute("message",
                nowEnabled ? "계정이 활성화되었습니다." : "계정이 비활성화되었습니다.");
        return "redirect:/admin/members/" + id;
    }

    @PostMapping("/members/{id}/change-role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        adminService.changeRole(id, role);
        redirectAttributes.addFlashAttribute("message", "권한이 변경되었습니다.");
        return "redirect:/admin/members/" + id;
    }

    // ──────────────────────────────────────────────
    // 스터디 관리
    // ──────────────────────────────────────────────

    @GetMapping("/studies")
    public String studies(
            @RequestParam(defaultValue = "") String keyword,
            @PageableDefault(size = 20, sort = "publishedDateTime", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        model.addAttribute("studyPage", adminService.searchStudies(keyword, pageable));
        model.addAttribute("keyword", keyword);
        return "admin/studies";
    }

    @PostMapping("/studies/{id}/close")
    public String closeStudy(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        adminService.forceCloseStudy(id);
        redirectAttributes.addFlashAttribute("message", "스터디가 강제 종료되었습니다.");
        return "redirect:/admin/studies";
    }

    // ──────────────────────────────────────────────
    // 통계
    // ──────────────────────────────────────────────

    @GetMapping("/statistics")
    public String statistics(
            @RequestParam(defaultValue = "monthly") String period,
            Model model) {
        model.addAttribute("period", period);
        model.addAttribute("signupStats", adminService.getSignupStats(period));
        model.addAttribute("studyStats", adminService.getStudyCreationStats(period));
        model.addAttribute("eventStats", adminService.getEventStats(period));
        return "admin/statistics";
    }

    // ──────────────────────────────────────────────
    // 개인정보처리내역
    // ──────────────────────────────────────────────

    @GetMapping("/privacy")
    public String privacy(Model model) {
        return "admin/privacy";
    }
}