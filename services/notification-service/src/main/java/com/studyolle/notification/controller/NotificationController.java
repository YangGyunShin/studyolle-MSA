package com.studyolle.notification.controller;

import com.studyolle.notification.dto.CommonApiResponse;
import com.studyolle.notification.dto.NotificationResponse;
import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자가 직접 호출하는 알림 외부 API 컨트롤러.
 *
 * =============================================
 * NotificationController vs NotificationInternalController
 * =============================================
 *
 * NotificationController (/api/notifications/**)
 *   - 호출 주체 : 브라우저 (사용자)
 *   - 인증 방식 : JWT → api-gateway 검증 → X-Account-Id 헤더
 *   - 접근 경로 : 브라우저 → api-gateway → 이 컨트롤러
 *   - 용도      : 알림 목록 조회, 읽음 처리
 *   - 데이터    : PostgreSQL 조회 (실시간 알림 목록)
 *
 * NotificationInternalController (/internal/notifications/**)
 *   - 호출 주체 : frontend-service (서버)
 *   - 인증 방식 : X-Internal-Service 헤더 검증
 *   - 접근 경로 : frontend-service → lb://NOTIFICATION-SERVICE (api-gateway 우회)
 *   - 용도      : nav 바 🔔 뱃지 숫자 표시
 *   - 데이터    : Redis 즉시 반환 (DB 조회 없음, 매우 빠름)
 *
 * =============================================
 * X-Account-Id 헤더를 사용하는 이유
 * =============================================
 *
 * JWT 검증은 api-gateway 에서만 수행한다.
 * api-gateway 가 JWT 를 검증하고 accountId 를 X-Account-Id 헤더에 담아 전달한다.
 * 이 서비스는 JWT 라이브러리 의존성 없이 헤더만 읽어서 사용자를 식별한다.
 *
 * =============================================
 * CommonApiResponse 로 감싸는 이유
 * =============================================
 *
 * 다른 서비스(study-service, event-service)와 응답 구조를 통일하기 위함이다.
 * 모든 API 응답이 { success: true, data: {...} } 형태로 일관성을 가진다.
 * frontend-service 에서 fetch 응답을 파싱할 때 항상 response.data 로 꺼낼 수 있다.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications
     *
     * 읽지 않은 알림 목록을 최신순으로 반환한다.
     * PostgreSQL 에서 조회한다.
     *
     * @param accountId api-gateway 가 JWT 에서 추출해 헤더에 담아준 사용자 ID
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestHeader("X-Account-Id") Long accountId) {
        List<NotificationResponse> notifications = notificationService
                .getUnreadNotifications(accountId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(CommonApiResponse.ok(notifications));
    }

    /**
     * PATCH /api/notifications
     *
     * 모든 읽지 않은 알림을 읽음 처리한다.
     * PostgreSQL 벌크 UPDATE + Redis 카운터 0으로 초기화.
     */
    @PatchMapping
    public ResponseEntity<CommonApiResponse<Void>> markAllAsRead(
            @RequestHeader("X-Account-Id") Long accountId) {
        notificationService.markAllAsRead(accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("모든 알림을 읽음 처리했습니다."));
    }

    /**
     * PATCH /api/notifications/{id}
     *
     * 특정 알림 하나를 읽음 처리한다.
     * PostgreSQL checked = true + Redis 카운터 -1.
     *
     * @param id 읽음 처리할 알림 ID
     * @param accountId 요청한 사용자 ID (자신의 알림만 처리 가능하도록 서비스에서 검증)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CommonApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @RequestHeader("X-Account-Id") Long accountId) {
        notificationService.markAsRead(id, accountId);
        return ResponseEntity.ok(CommonApiResponse.ok("알림을 읽음 처리했습니다."));
    }
}
