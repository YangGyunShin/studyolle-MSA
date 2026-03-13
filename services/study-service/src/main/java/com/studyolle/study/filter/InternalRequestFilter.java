package com.studyolle.study.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * /internal/** 경로 보호 인터셉터.
 *
 * =============================================
 * HandlerInterceptor vs Filter
 * =============================================
 *
 * Java 서블릿에는 요청을 가로채는 방법이 두 가지 있다.
 *
 * Filter (javax.servlet.Filter):
 *   - 서블릿 컨테이너(Tomcat) 레벨에서 동작한다.
 *   - Spring 컨텍스트 밖에서 실행되어 Spring 빈을 주입받기 번거롭다.
 *   - 모든 요청(정적 파일 포함)에 적용된다.
 *
 * HandlerInterceptor (Spring MVC):
 *   - Spring 의 DispatcherServlet 내부에서 동작한다.
 *   - Spring 빈을 자유롭게 주입받을 수 있다.
 *   - WebMvcConfigurer.addInterceptors() 로 경로 패턴을 지정할 수 있다.
 *   - preHandle(): 컨트롤러 실행 전 / postHandle(): 실행 후 / afterCompletion(): 응답 완료 후
 *
 * 이 인터셉터는 /internal/** 경로에만 적용하면 되고 Spring 빈이 필요 없으므로
 * HandlerInterceptor 로 구현했다. 경로 지정은 WebMvcConfig 에서 담당한다.
 *
 * =============================================
 * 보안 계층 구조
 * =============================================
 *
 * 외부 클라이언트 → api-gateway: /internal/** 전면 403 차단 (1차 방어)
 * 내부 서비스    → 이 인터셉터: X-Internal-Service 헤더 검증 (2차 방어)
 *
 * 2차 방어가 필요한 이유: api-gateway 를 우회하거나(내부 네트워크 직접 접근),
 * 다른 서비스가 잘못된 헤더 없이 호출하는 경우를 차단하기 위함이다.
 */
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    // study-service 의 /internal/** 에 접근을 허용할 서비스 이름 목록
    // 새로운 서비스가 추가되면 이 목록에 추가해야 한다
    private static final List<String> ALLOWED_SERVICES =
            List.of("admin-service", "event-service", "notification-service");

    /**
     * 컨트롤러 실행 전에 호출된다.
     *
     * X-Internal-Service 헤더가 없거나 ALLOWED_SERVICES 목록에 없는 값이면
     * 403 을 반환하고 false 를 리턴하여 컨트롤러 실행을 중단한다.
     * true 를 리턴하면 요청이 다음 단계(컨트롤러)로 넘어간다.
     *
     * @param request  HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param handler  실행될 컨트롤러 메서드 정보
     * @return true 면 컨트롤러 실행 계속, false 면 즉시 중단
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String internalService = request.getHeader("X-Internal-Service");

        if (internalService == null || !ALLOWED_SERVICES.contains(internalService)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            response.getWriter().write("{\"error\": \"Internal access only\"}");
            return false; // 컨트롤러 실행 중단
        }
        return true; // 허용된 서비스 → 컨트롤러로 진행
    }
}