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
            List<Map<String, String>> tagifyFormat = currentTags.stream()
                    .map(t -> Map.of("value", t))
                    .collect(Collectors.toList());
            model.addAttribute("tags", objectMapper.writeValueAsString(tagifyFormat));
            model.addAttribute("whitelist", objectMapper.writeValueAsString(whitelist));
        } catch (JsonProcessingException e) {
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