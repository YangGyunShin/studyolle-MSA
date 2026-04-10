package com.studyolle.frontend.notification.controller;

import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 알림 페이지 컨트롤러.
 *
 * GET /notifications -> templates/notifications.html
 *
 * =============================================
 * 이 컨트롤러가 매우 가벼운 이유
 * =============================================
 *
 * notifications.html 페이지의 알림 목록은 브라우저가 직접
 * fetch(/api/notifications) 로 api-gateway 를 거쳐 가져온다.
 * 따라서 이 컨트롤러는 페이지 렌더링에 필요한 최소 정보(account, apiBase)만
 * 모델에 주입하면 끝난다.
 *
 * unreadNotificationCount 는 GlobalModelAttributes(@ControllerAdvice)에서
 * 자동으로 주입되므로 여기서 직접 추가할 필요가 없다.
 *
 * =============================================
 * 비로그인 사용자 차단
 * =============================================
 *
 * /notifications 는 로그인 사용자 전용 페이지다.
 * X-Account-Id 헤더가 없으면 (=비로그인) 로그인 페이지로 리다이렉트한다.
 */
@Controller
@RequiredArgsConstructor
public class NotificationPageController {

    private final AccountInternalClient accountInternalClient;

    @Value("${app.api-base-url}")
    private String apiBaseUrl;

    @GetMapping("/notifications")
    public Object notifications(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
            Model model) {

        // 비로그인 사용자는 로그인 페이지로 보낸다.
        if (accountId == null) {
            return new RedirectView("/login");
        }

        AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);
        model.addAttribute("account", account);
        model.addAttribute("apiBase", apiBaseUrl);

        return "notifications";
    }
}