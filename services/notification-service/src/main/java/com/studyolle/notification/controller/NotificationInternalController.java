package com.studyolle.notification.controller;

import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationService notificationService;

    /**
     * 읽지 않은 알림 수를 Redis 에서 즉시 반환한다.
     *
     * frontend-service 의 HomeController 가 호출하여
     * nav 바 🔔 뱃지 숫자를 표시하는 데 사용한다.
     * DB 조회 없이 Redis 에서 바로 읽으므로 매우 빠르다.
     */
    @GetMapping("/count/{accountId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(accountId)));
    }
}