package com.studyolle.event.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 내부 서비스 간 요청 검증 인터셉터
 *
 * [이 클래스가 필요한 이유]
 *
 * event-service 의 /internal/** 경로는 외부 브라우저가 아닌
 * frontend-service, study-service 같은 내부 서비스만 호출할 수 있어야 한다.
 *
 * 보호 방식은 두 단계로 이루어진다:
 *
 *   1차 방어 — api-gateway
 *     외부에서 /internal/** 로 직접 접근하는 요청을 403 으로 전면 차단한다.
 *     api-gateway 의 application.yml 에서 SetStatus=403 필터로 처리한다.
 *
 *   2차 방어 — 이 인터셉터 (InternalRequestFilter)
 *     api-gateway 를 통하지 않고 내부 네트워크에서 직접 접근하는 경우를 차단한다.
 *     X-Internal-Service 헤더가 없거나 허용되지 않은 서비스이면 403 을 반환한다.
 *
 * 2차 방어가 필요한 이유:
 *   api-gateway 가 유일한 방어선이면, 내부 네트워크에서 event-service 로 직접 요청을 보낼 경우
 *   /internal/** 경로가 무방비 상태가 된다.
 *   각 서비스에서 자체적으로 검증하여 api-gateway 우회 시에도 보안을 유지한다.
 *
 * [내부 서비스 식별 방식]
 *
 * JWT 없이 X-Internal-Service 헤더 값으로 요청 출처를 식별한다.
 *
 *   외부 요청: Authorization: Bearer {JWT}  → api-gateway 에서 검증
 *   내부 요청: X-Internal-Service: frontend-service  → 이 인터셉터에서 검증
 *
 * 내부 서비스가 /internal/** 를 호출할 때 반드시 이 헤더를 포함해야 한다:
 *
 *   예시 (frontend-service 의 RestTemplate 호출):
 *     headers.set("X-Internal-Service", "frontend-service");
 *     restTemplate.exchange("/internal/events/by-study/{path}", GET, entity, ...);
 *
 * [HandlerInterceptor 를 구현하는 이유]
 *
 * Spring MVC 의 인터셉터 인터페이스다.
 * preHandle() 메서드는 컨트롤러 실행 전에 호출되며,
 * false 를 반환하면 요청 처리가 중단되고 컨트롤러로 진입하지 않는다.
 *
 * WebMvcConfig.addInterceptors() 에서 /internal/** 경로에 등록되어 있다.
 */
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    /**
     * /internal/** 호출을 허용하는 내부 서비스 목록
     * <p>
     * 새로운 서비스가 event-service 의 내부 API 를 호출해야 한다면 이 목록에 추가한다.
     * 예: notification-service 가 추가되면 "notification-service" 를 목록에 넣는다.
     */
    private static final List<String> ALLOWED_SERVICES =
            List.of("frontend-service", "admin-service", "event-service", "notification-service", "study-service");

    /**
     * 요청 전처리 — 내부 서비스 헤더 검증
     *
     * /internal/** 경로로 들어오는 요청에서 X-Internal-Service 헤더를 확인한다.
     * WebMvcConfig 에서 addPathPatterns("/internal/**") 로 등록했으므로
     * 이 메서드는 /internal/** 경로에서만 실행된다.
     *
     * @return true  — 검증 통과, 요청을 컨트롤러로 전달
     *         false — 검증 실패, 403 반환하고 요청 처리 중단
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        String serviceName = request.getHeader("X-Internal-Service");

        // 헤더가 없거나 허용 목록에 없는 서비스이면 403 반환
        if (serviceName == null || !ALLOWED_SERVICES.contains(serviceName)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        return true;
    }
}
