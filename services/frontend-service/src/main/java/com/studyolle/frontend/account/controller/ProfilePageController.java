package com.studyolle.frontend.account.controller;

import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class ProfilePageController {

    private final AccountInternalClient accountInternalClient;

    @Value("${app.api-base-url}")
    private String apiBase;

    @GetMapping("/profile/{nickname}")
    public String profilePage(
            @PathVariable String nickname,
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model,
            HttpServletResponse response) throws IOException {

        // 프로필 주인 조회
        AccountSummaryDto profileAccount = accountInternalClient.getAccountByNickname(nickname);
        if (profileAccount == null) {
            response.sendError(404);
            return null;
        }

        // 현재 로그인한 사용자 (nav 바용)
        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);

        model.addAttribute("profileAccount", profileAccount);
        model.addAttribute("account", account);
        model.addAttribute("isOwner", accountId != null && accountId.equals(profileAccount.getId()));
        model.addAttribute("apiBase", apiBase);
        return "account/profile";
    }
}