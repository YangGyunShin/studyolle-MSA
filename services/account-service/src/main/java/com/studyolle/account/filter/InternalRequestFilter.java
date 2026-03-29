package com.studyolle.account.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class InternalRequestFilter implements HandlerInterceptor {

    private static final List<String> ALLOWED_SERVICES =
            List.of("frontend-service", "admin-service", "event-service", "notification-service");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String internalService = request.getHeader("X-Internal-Service");
        if (internalService == null || !ALLOWED_SERVICES.contains(internalService)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"Internal access only\"}");
            return false;
        }
        return true;
    }
}