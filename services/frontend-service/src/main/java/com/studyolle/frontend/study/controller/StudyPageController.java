package com.studyolle.frontend.study.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.study.client.StudyInternalClient;
import com.studyolle.frontend.study.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 스터디 관련 모든 페이지의 라우팅을 담당하는 컨트롤러.
 *
 * [역할]
 * 1. 인증 검사 — X-Account-Id 가 없으면 /login 리다이렉트
 * 2. 권한 검사 — 관리자 페이지에 비관리자가 접근하면 /study/{path} 리다이렉트
 * 3. 데이터 조회 — StudyInternalClient 로 study-service 내부 API 호출
 * 4. 모델 적재 — Thymeleaf 에 필요한 attribute 를 model 에 담기
 * 5. 뷰 반환 — 적절한 템플릿 이름 반환
 *
 * [이 컨트롤러가 하지 않는 일]
 * - JWT 검증: api-gateway 가 이미 완료. @RequestHeader 로 결과만 받는다.
 * - 비즈니스 로직: 버튼 클릭(가입, 설정 변경 등)은 브라우저 JS 가 직접 처리.
 *   이 컨트롤러는 오직 초기 HTML 렌더링을 위한 데이터 조회만 한다.
 */
@Controller
@RequiredArgsConstructor
public class StudyPageController {

    private final StudyInternalClient studyInternalClient;
    private final AccountInternalClient accountInternalClient;
    private final ObjectMapper objectMapper;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    // ------------------------------------------------------------------
    // 공통 헬퍼
    // ------------------------------------------------------------------

    /**
     * 모든 스터디 페이지에서 반복되는 공통 model attribute 를 한 번에 담는다.
     * apiBase, account, study, isManager, isMember 는 모든 스터디 페이지에 필요하다.
     */
    private void addCommonAttributes(Model model, Long accountId, StudyPageDataDto study) {
        model.addAttribute("apiBase", apiBaseUrl);
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));
        model.addAttribute("study", study);
        model.addAttribute("isManager", study.isManager());
        model.addAttribute("isMember", study.isMember());
    }

    // ------------------------------------------------------------------
    // 스터디 생성 폼
    // GET /new-study
    // ------------------------------------------------------------------

    /**
     * 스터디 생성 폼 페이지.
     * 폼 데이터는 빈 상태로 내려준다. 실제 저장은 JS fetch(POST /api/studies) 로 처리.
     */
    @GetMapping("/new-study")
    public String newStudyForm(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model, HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login"); return null;
        }

        model.addAttribute("apiBase", apiBaseUrl);
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));
        return "study/form";
    }

    // ------------------------------------------------------------------
    // 스터디 상세 뷰
    // GET /study/{path}
    // ------------------------------------------------------------------

    /**
     * 스터디 소개 페이지.
     * 비로그인도 접근 가능 (공개 스터디 열람).
     * accountId 가 있으면 study-service 가 isManager/isMember/hasPendingRequest 를 계산해 준다.
     */
    @GetMapping("/study/{path}")
    public String studyView(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        model.addAttribute("hasPendingRequest", study.isHasPendingRequest());

        // 모임 목록: event-service 연동 전까지 빈 리스트로 초기화
        model.addAttribute("newEvents", List.of());
        model.addAttribute("oldEvents", List.of());
        return "study/view";
    }

    // ------------------------------------------------------------------
    // 멤버 목록
    // GET /study/{path}/members
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/members")
    public String studyMembers(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/members";
    }

    // ------------------------------------------------------------------
    // 설정 — 소개 수정
    // GET /study/{path}/settings/description
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/description")
    public String settingsDescription(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/description";
    }

    // ------------------------------------------------------------------
    // 설정 — 배너
    // GET /study/{path}/settings/banner
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/banner")
    public String settingsBanner(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/banner";
    }

    // ------------------------------------------------------------------
    // 설정 — 태그
    // GET /study/{path}/settings/tags
    // ------------------------------------------------------------------

    /**
     * 태그 설정 페이지.
     *
     * [Tagify 초기화 데이터 JSON 직렬화]
     * Thymeleaf [[${tags}]] 로 List 를 그냥 출력하면 자바 배열 표현식이 되어
     * JS 파싱이 실패한다. ObjectMapper 로 명시적 JSON 직렬화를 해야 안전하다.
     *
     * Tagify 가 인식하는 형식: [{value:"Java"}, {value:"Spring"}]
     */
    @GetMapping("/study/{path}/settings/tags")
    public String settingsTags(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<String> currentTags = studyInternalClient.getStudyTags(path, accountId);
        List<String> whitelist   = studyInternalClient.getTagWhitelist(accountId);

        addCommonAttributes(model, accountId, study);
        try {
            List<Map<String, String>> tagifyFormat = currentTags.stream()
                    .map(t -> Map.of("value", t))
                    .collect(Collectors.toList());
            model.addAttribute("tags", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            model.addAttribute("tags", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "study/settings/tags";
    }

    // ------------------------------------------------------------------
    // 설정 — 지역
    // GET /study/{path}/settings/zones
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/zones")
    public String settingsZones(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<String> currentZones = studyInternalClient.getStudyZones(path, accountId);
        List<String> whitelist    = studyInternalClient.getZoneWhitelist(accountId);

        addCommonAttributes(model, accountId, study);
        try {
            List<Map<String, String>> tagifyFormat = currentZones.stream()
                    .map(z -> Map.of("value", z))
                    .collect(Collectors.toList());
            model.addAttribute("zones", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            model.addAttribute("zones", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "study/settings/zones";
    }

    // ------------------------------------------------------------------
    // 설정 — 스터디 관리 (공개/종료/경로/이름/삭제)
    // GET /study/{path}/settings/study
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/study")
    public String settingsStudy(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        addCommonAttributes(model, accountId, study);
        return "study/settings/study";
    }

    // ------------------------------------------------------------------
    // 설정 — 가입 관리
    // GET /study/{path}/settings/join-requests
    // ------------------------------------------------------------------

    @GetMapping("/study/{path}/settings/join-requests")
    public String settingsJoinRequests(
            @PathVariable String path,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }

        StudyPageDataDto study = studyInternalClient.getStudyPageData(path, accountId);
        if (study == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!study.isManager()) {
            response.sendRedirect("/study/" + path);
            return null;
        }

        List<JoinRequestDto> pendingRequests = studyInternalClient.getJoinRequests(path, accountId);

        addCommonAttributes(model, accountId, study);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingCount", pendingRequests.size());
        return "study/settings/join-requests";
    }
}