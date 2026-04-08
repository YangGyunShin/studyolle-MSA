package com.studyolle.notification.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * /internal/** 경로를 허용된 내부 서비스만 접근할 수 있도록 제한하는 인터셉터.
 *
 * =============================================
 * 왜 이 필터가 필요한가?
 * =============================================
 *
 * MSA 에서 /internal/** 경로는 서비스 간 통신 전용이다.
 * 브라우저 같은 외부 클라이언트가 /internal/** 에 직접 접근하면 안 된다.
 *
 * 1차 방어: api-gateway 가 /internal/** 경로를 전면 차단한다. (SetStatus=403)
 *           외부에서 오는 모든 /internal/** 요청은 서비스에 도달하지 않는다.
 *
 * 2차 방어: 이 인터셉터가 X-Internal-Service 헤더를 검증한다.
 *           내부 네트워크에서 직접 접근하거나 api-gateway 를 우회하는 경우를 막는다.
 *           허용된 서비스 목록에 없으면 403 을 반환한다.
 *
 * =============================================
 * X-Internal-Service 헤더란?
 * =============================================
 *
 * 서비스 간 내부 호출 시 요청하는 서비스가 직접 붙이는 헤더다.
 * 예: frontend-service 가 notification-service 에 알림 수를 요청할 때
 *     "X-Internal-Service: frontend-service" 헤더를 포함한다.
 *
 * 이 헤더가 없거나 허용 목록에 없는 값이면 403 Forbidden 을 반환한다.
 *
 * =============================================
 * ALLOWED_SERVICES 목록 관리 주의사항
 * =============================================
 *
 * 새로운 서비스가 /internal/notifications/** 를 호출해야 한다면
 * 반드시 이 목록에 해당 서비스 이름을 추가해야 한다.
 * 추가하지 않으면 403 에러가 발생하고, 에러 메시지만으로는 원인을 찾기 어렵다.
 * (MSA 트러블슈팅 이력 017번 참고 — study-service 누락으로 403 발생했던 사례)
 */
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    /**
     * /internal/** 경로에 접근을 허용하는 서비스 목록.
     *
     * 현재 notification-service 의 /internal/** 를 호출하는 서비스:
     *   frontend-service : HomeController 에서 읽지 않은 알림 수 조회
     *
     * 나중에 추가될 수 있는 서비스:
     *   admin-service    : 관리자 페이지에서 알림 통계 조회 시
     */
    private static final List<String> ALLOWED_SERVICES = List.of(
            "frontend-service",
            "study-service",
            "event-service",
            "admin-service"
    );

    /**
     * 요청이 컨트롤러에 도달하기 전에 실행된다.
     *
     * 처리 흐름:
     *   1. 요청 URI 가 /internal/ 로 시작하는지 확인
     *   2. X-Internal-Service 헤더가 존재하는지 확인
     *   3. 헤더 값이 ALLOWED_SERVICES 에 포함되는지 확인
     *   4. 검증 실패 → 403 Forbidden 반환 후 컨트롤러 실행 차단 (return false)
     *   5. 검증 성공 → 컨트롤러로 진행 (return true)
     *
     * @return true  → 요청을 컨트롤러로 전달
     *         false → 요청을 차단 (이미 response 에 403 을 설정)
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/internal/")) {
            String serviceName = request.getHeader("X-Internal-Service");

            if (serviceName == null || !ALLOWED_SERVICES.contains(serviceName)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                return false;  // 컨트롤러 실행 차단
            }
        }

        return true;  // 검증 통과, 컨트롤러로 진행
    }
}
