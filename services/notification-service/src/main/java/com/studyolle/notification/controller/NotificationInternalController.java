package com.studyolle.notification.controller;

import com.studyolle.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 서비스 간 내부 통신 전용 알림 API 컨트롤러.
 *
 * =============================================
 * NotificationInternalController vs NotificationController
 * =============================================
 *
 * 이 컨트롤러 (/internal/notifications/**)
 *   - 호출 주체 : frontend-service (서버 사이드)
 *   - 인증 방식 : X-Internal-Service 헤더 (InternalRequestFilter 에서 검증)
 *   - 접근 경로 : frontend-service → lb://NOTIFICATION-SERVICE (api-gateway 우회)
 *   - 외부 접근 : api-gateway 에서 /internal/** 를 전면 차단하므로 불가능
 *   - 용도      : nav 바 🔔 뱃지 숫자 표시 (페이지 로드마다 호출)
 *   - 데이터    : Redis 에서 즉시 반환 (DB 조회 없음, ~0.1ms)
 *
 * NotificationController (/api/notifications/**)
 *   - 호출 주체 : 브라우저 (사용자)
 *   - 인증 방식 : JWT → api-gateway → X-Account-Id 헤더
 *   - 용도      : 알림 목록 조회, 읽음 처리
 *   - 데이터    : PostgreSQL 조회 (~수 ms)
 *
 * =============================================
 * 왜 내부 API 와 외부 API 를 분리하는가?
 * =============================================
 *
 * nav 바 뱃지는 모든 페이지 로드마다 호출된다.
 * 사용자가 많아지면 DB 조회 방식(COUNT query)은 부하가 커진다.
 * Redis 카운터를 사용하면 메모리에서 즉시 읽으므로 DB 부하 없이 빠르게 응답 가능하다.
 *
 * 이 API 는 frontend-service 의 서버에서만 호출하므로
 * JWT 인증이 필요 없고 X-Internal-Service 헤더로 충분하다.
 * 브라우저가 직접 접근할 수 없으므로 보안상 문제도 없다.
 *
 * =============================================
 * 응답 형식을 Map<String, Long> 으로 반환하는 이유
 * =============================================
 *
 * CommonApiResponse 로 감싸지 않는다.
 * 이 API 는 외부 사용자가 아닌 내부 서비스(frontend-service)가 호출하는 API 이므로
 * 응답 구조를 양측이 합의하여 단순하게 유지한다.
 * frontend-service 에서 response.get("count") 로 바로 꺼낼 수 있다.
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationService notificationService;

    /**
     * GET /internal/notifications/count/{accountId}
     *
     * 읽지 않은 알림 수를 Redis 에서 즉시 반환한다.
     *
     * frontend-service 의 HomeController 가 페이지 렌더링 전에 이 API 를 호출하여
     * nav 바 🔔 뱃지 숫자를 모델에 담아 Thymeleaf 템플릿에 전달한다.
     *
     * Redis 가 다운된 경우: NotificationService.getUnreadCount() 가 0을 반환한다.
     * (뱃지가 0으로 표시될 뿐, 서비스 장애로 이어지지 않는다)
     *
     * 응답 예시: { "count": 5 }
     *
     * @param accountId URL Path 에서 직접 받는다.
     *                  (외부 API 와 달리 헤더가 아닌 Path Variable 로 받음)
     */
    @GetMapping("/count/{accountId}")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(
                Map.of("count", notificationService.getUnreadCount(accountId))
        );
    }
}
