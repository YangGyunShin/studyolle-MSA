package com.studyolle.admin.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 권한 2차 검증 인터셉터.
 *
 * [심층 방어 (Defense in Depth) 원칙]
 * 보안은 "하나의 완벽한 방어선" 보다 "여러 겹의 무난한 방어선" 이 더 튼튼하다.
 * api-gateway 가 완벽하리라 가정하고 내부 서비스를 열어두면, Gateway 가 잠깐만 뚫려도
 * 모든 데이터가 노출된다. Gateway 가 1차 방어선이라면 이 인터셉터는 2차 방어선이다.
 *
 * [왜 HandlerInterceptor 인가 — 다른 선택지와의 비교]
 * Filter, Interceptor, AOP, Spring Security 모두 가능하다. 각각의 차이는 이렇다.
 *
 *   Servlet Filter  : 가장 낮은 계층, Spring Context 전이라 MVC 정보(핸들러 메서드)에 접근 불가
 *   Interceptor     : Spring MVC 계층, 핸들러 메서드 정보에 접근 가능, 구성이 가장 단순
 *   AOP             : 메서드 수준에서 어노테이션 기반으로 제어, 세밀하지만 설정이 복잡
 *   Spring Security : 가장 강력하지만 학습 곡선이 크고 이 용도에는 과하다
 *
 * 여기서는 "URL 패턴으로 일괄 검사만 하면 충분" 하므로 Interceptor 가 가장 간단하다.
 *
 * [왜 /api/admin/** 만 검증하는가]
 * admin-service 는 /internal/** 경로를 노출하지 않는다 (다른 서비스에서 이 서비스를 호출할
 * 일이 없기 때문). 따라서 들어올 수 있는 유효한 요청은 모두 /api/admin/** 뿐이고,
 * 이 경로 전체에 일괄적으로 관리자 검증을 걸어도 아무런 부작용이 없다.
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_HEADER = "X-Account-Role";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * preHandle — 컨트롤러가 호출되기 전에 실행되는 메서드.
     * true 를 반환하면 요청이 컨트롤러로 전달되고, false 를 반환하면 여기서 중단된다.
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String role = request.getHeader(ROLE_HEADER);

        if (!ROLE_ADMIN.equals(role)) {
            // 로그는 WARN 레벨로 — 정상 운영 중에는 발생하지 않아야 하는 상황이기 때문
            log.warn("관리자 권한 없이 admin-service 접근 시도: uri={}, role={}",
                    request.getRequestURI(), role);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        return true;
    }
}