package com.studyolle.notification.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * /internal/** 경로를 허용된 내부 서비스만 접근할 수 있도록 제한하는 인터셉터.
 *
 * 외부에서 /internal/** 로 직접 접근하는 것은 api-gateway 에서 전면 차단한다.
 * 이 필터는 내부 네트워크에서의 비정상 접근을 2차로 차단한다.
 * 요청 헤더 X-Internal-Service 가 허용된 서비스 목록에 없으면 403 을 반환한다.
 */
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    private static final List<String> ALLOWED_SERVICES = List.of(
            "frontend-service",
            "study-service",
            "event-service",
            "admin-service"
    );

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/internal/")) {
            String serviceName = request.getHeader("X-Internal-Service");
            if (serviceName == null || !ALLOWED_SERVICES.contains(serviceName)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }
        return true;
    }
}