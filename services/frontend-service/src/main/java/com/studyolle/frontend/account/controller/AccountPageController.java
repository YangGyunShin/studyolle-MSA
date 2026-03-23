package com.studyolle.frontend.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSettingsDto;
import com.studyolle.frontend.study.client.StudyInternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class AccountPageController {

    private final AccountInternalClient accountInternalClient;
    private final StudyInternalClient studyInternalClient;
    private final ObjectMapper objectMapper;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    private void addCommonAttributes(Model model, Long accountId, AccountSettingsDto settings) {
        model.addAttribute("apiBase", apiBaseUrl);
        model.addAttribute("account", accountInternalClient.getAccountSummary(accountId));
        model.addAttribute("accountSettings", settings);
    }

    @GetMapping("/profile")
    public String profile(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {
        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }
        AccountSettingsDto settings = accountInternalClient.getAccountSettings(accountId);
        addCommonAttributes(model, accountId, settings);
        return "settings/profile";
    }

    @GetMapping("/password")
    public String password(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {
        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }
        AccountSettingsDto settings = accountInternalClient.getAccountSettings(accountId);
        addCommonAttributes(model, accountId, settings);
        return "settings/password";
    }

    @GetMapping("/notifications")
    public String notifications(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {
        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }
        AccountSettingsDto settings = accountInternalClient.getAccountSettings(accountId);
        addCommonAttributes(model, accountId, settings);
        return "settings/notifications";
    }

    @GetMapping("/tags")
    public String tags(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {
        if (accountId == null) {
            response.sendRedirect("/login");
            return null;
        }
        AccountSettingsDto settings = accountInternalClient.getAccountSettings(accountId);
        List<String> currentTags = accountInternalClient.getAccountTags(accountId);
        List<String> whitelist = studyInternalClient.getTagWhitelist(accountId);
        addCommonAttributes(model, accountId, settings);
        try {
            /*
             * [Tagify 초기값 형식 변환]
             *
             * 문제:
             * account-service 에서 받아온 currentTags 는 단순 List<String> 이다.
             *   예) ["Java", "Spring Boot", "JPA"]
             *
             * 그런데 Tagify 는 초기 태그를 아래 형식으로만 인식한다.
             *   예) [{"value": "Java"}, {"value": "Spring Boot"}, {"value": "JPA"}]
             *
             * 해결:
             * stream().map() 으로 각 문자열 t 를
             * Map.of("value", t) 형태의 객체로 변환한다.
             *   "Java"  ->  {"value": "Java"}
             *
             * [objectMapper 를 사용하는 이유]
             *
             * Thymeleaf 가 List<Map> 을 th:inline="javascript" 로 그냥 출력하면
             * Java 의 toString() 결과인 [{value=Java}] 가 되어 JS 가 파싱하지 못한다.
             * objectMapper.writeValueAsString() 으로 미리 올바른 JSON 문자열
             * "[{\"value\":\"Java\"}]" 로 직렬화해두면
             * Thymeleaf 가 이 문자열을 JS 코드에 그대로 삽입하므로 브라우저에서 정상 파싱된다.
             *
             * [whitelist 를 따로 변환하지 않는 이유]
             * whitelist 는 Tagify 자동완성 드롭다운 후보 목록이다.
             * Tagify 는 whitelist 로 단순 문자열 배열 ["Java", "Spring Boot"] 도 허용하므로
             * {value: ...} 형태로 변환할 필요 없이 JSON 문자열화만 하면 된다.
             */
            List<Map<String, String>> tagifyFormat = currentTags.stream()
                    .map(t -> Map.of("value", t))
                    .collect(Collectors.toList());
            model.addAttribute("tags", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            // JSON 직렬화 실패 시 빈 배열로 폴백 — 페이지는 정상 렌더링되고 태그만 빈 상태로 표시
            model.addAttribute("tags", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "settings/tags";
    }

    @GetMapping("/zones")
    public String zones(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model, HttpServletResponse response) throws IOException {
        if (accountId == null) { response.sendRedirect("/login"); return null; }
        AccountSettingsDto settings = accountInternalClient.getAccountSettings(accountId);
        List<String> currentZones = accountInternalClient.getAccountZones(accountId);
        List<String> whitelist    = studyInternalClient.getZoneWhitelist(accountId);
        addCommonAttributes(model, accountId, settings);
        try {
            List<Map<String, String>> tagifyFormat = currentZones.stream()
                    .map(z -> Map.of("value", z)).collect(Collectors.toList());
            model.addAttribute("zones", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
            model.addAttribute("zones", "[]");
            model.addAttribute("whitelist", "[]");
        }
        return "settings/zones";
    }

}