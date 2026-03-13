package com.studyolle.study.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 내부 서비스 전용 경로(/internal/**) 보호 인터셉터
 *
 * =============================================
 * 보안 아키텍처 가이드(MSA_AUTH_FLOW.md) 참조
 * =============================================
 *
 * [외부에서의 /internal/** 접근]
 * api-gateway 가 /internal/** 경로를 전면 차단(403)하므로
 * 외부 클라이언트는 이 경로에 도달할 수 없다.
 *
 * [서비스 간 내부 호출]
 * admin-service, event-service 등이 Feign Client 로 호출할 때
 * X-Internal-Service: {서비스명} 헤더를 포함해야 한다.
 * 이 인터셉터는 해당 헤더가 없거나 허용되지 않은 서비스면 403 을 반환한다.
 *
 * [이중 보안의 이유]
 * api-gateway 가 유일한 방어선이면 Gateway 우회 시 무방비 상태가 된다.
 * 각 서비스에서도 2차 검증을 수행해 내부 네트워크 비정상 접근도 차단한다.
 */
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    // 이 서비스의 /internal/** 에 접근을 허용할 서비스 목록
    private static final List<String> ALLOWED_SERVICES =
            List.of("admin-service", "event-service", "notification-service");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String internalService = request.getHeader("X-Internal-Service");

        if (internalService == null || !ALLOWED_SERVICES.contains(internalService)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"Internal access only\"}");
            return false;
        }
        return true;
    }
}