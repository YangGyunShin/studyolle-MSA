package com.studyolle.frontend.common;

import com.studyolle.frontend.notification.client.NotificationInternalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 모든 @Controller 의 모든 핸들러 메서드에 공통으로 주입할 모델 속성을 정의한다.
 *
 * =============================================
 * @ControllerAdvice + @ModelAttribute 의 동작 원리
 * =============================================
 *
 * @ControllerAdvice 가 붙은 클래스의 @ModelAttribute 메서드는
 * 모든 컨트롤러의 핸들러 메서드 실행 전에 자동으로 호출되어
 * 반환값이 Model 에 추가된다.
 *
 *   메서드명: unreadNotificationCount()
 *   → Thymeleaf 에서 ${unreadNotificationCount} 로 접근 가능
 *
 * 즉, 각 컨트롤러가 model.addAttribute("unreadNotificationCount", ...) 를
 * 매번 호출할 필요 없이 자동으로 모든 페이지에 주입된다.
 *
 * =============================================
 * 왜 이런 패턴이 필요한가
 * =============================================
 *
 * nav 바의 🔔 뱃지는 HomeController, StudyPageController, EventPageController,
 * AccountPageController, ProfilePageController 등 모든 페이지에서 표시되어야 한다.
 *
 * 만약 각 컨트롤러가 직접 NotificationInternalClient 를 호출해 모델에 추가한다면:
 *   1. 코드 중복 — 모든 컨트롤러에 동일한 코드 5줄씩 추가
 *   2. 누락 위험 — 새 컨트롤러를 만들 때 잊으면 그 페이지만 뱃지가 사라짐
 *   3. 변경 비용 — 뱃지 로직이 바뀌면 모든 컨트롤러를 수정해야 함
 *
 * @ControllerAdvice 로 한 곳에 모아두면 새 컨트롤러를 추가해도 자동 적용된다.
 *
 * =============================================
 * @RequestHeader 의 동작
 * =============================================
 *
 * @ModelAttribute 메서드도 일반 핸들러처럼 @RequestHeader, @PathVariable 등을
 * 받을 수 있다. Spring 이 현재 요청의 헤더에서 X-Account-Id 를 꺼내 자동 주입한다.
 *
 * required = false 이므로 비로그인 요청에서는 null 이 들어온다.
 * NotificationInternalClient.getUnreadCount(null) 은 0 을 반환하도록 처리되어 있어
 * 비로그인 페이지에서도 안전하게 동작한다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final NotificationInternalClient notificationInternalClient;

    /**
     * 모든 페이지의 nav 바 🔔 뱃지에 표시될 안 읽은 알림 수.
     *
     * Thymeleaf 사용법:
     *   <span th:if="${unreadNotificationCount > 0}"
     *         th:text="${unreadNotificationCount}"></span>
     */
    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(
            @RequestHeader(value = "X-Account-Id", required = false) Long accountId) {
        return notificationInternalClient.getUnreadCount(accountId);
    }
}