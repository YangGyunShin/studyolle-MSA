package com.studyolle.notification.controller;

import com.studyolle.notification.dto.NotificationResponse;
import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 읽지 않은 알림 목록 (PostgreSQL)
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestHeader("X-Account-Id") Long accountId) {
        return ResponseEntity.ok(
                notificationService.getUnreadNotifications(accountId)
                        .stream().map(NotificationResponse::from).toList()
        );
    }

    // 전체 읽음 처리
    @PatchMapping
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("X-Account-Id") Long accountId) {
        notificationService.markAllAsRead(accountId);
        return ResponseEntity.ok().build();
    }

    // 단건 읽음 처리
    @PatchMapping("/{id}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestHeader("X-Account-Id") Long accountId) {
        notificationService.markAsRead(id, accountId);
        return ResponseEntity.ok().build();
    }
}