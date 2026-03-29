package com.studyolle.event.config;

import com.studyolle.event.filter.InternalRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 클래스
 *
 * [이 클래스가 필요한 이유]
 *
 * InternalRequestFilter 는 @Component 로 빈 등록은 되어 있지만,
 * Spring MVC 인터셉터로 등록하지 않으면 실제 요청에서 동작하지 않는다.
 *
 * Spring MVC 의 인터셉터는 DispatcherServlet 이 요청을 받은 후,
 * 컨트롤러 메서드가 실행되기 전에 preHandle() 을 호출하는 구조다.
 *
 *   [요청] --> DispatcherServlet --> [인터셉터 preHandle()] --> [컨트롤러]
 *
 * 인터셉터를 등록하려면 WebMvcConfigurer 인터페이스의 addInterceptors() 를 구현해야 한다.
 * 이 클래스가 그 역할을 담당한다.
 *
 * [Spring Security 필터와 MVC 인터셉터의 차이]
 *
 * Spring Security 필터는 DispatcherServlet 보다 앞단에서 동작한다 (서블릿 필터 체인).
 * MVC 인터셉터는 DispatcherServlet 이후에 동작한다.
 *
 *   [요청]
 *     --> [서블릿 필터 (Spring Security)]  ← SecurityConfig 에서 설정
 *     --> [DispatcherServlet]
 *     --> [MVC 인터셉터 (InternalRequestFilter)]  ← 이 클래스에서 등록
 *     --> [컨트롤러]
 *
 * /internal/** 보호를 Security 필터가 아닌 MVC 인터셉터로 처리하는 이유:
 *   - Security 필터에서 처리하려면 JWT 등 인증 객체가 필요한데,
 *     내부 서비스 간 통신은 JWT 없이 X-Internal-Service 헤더만 사용한다.
 *   - 헤더 값 하나를 확인하는 단순한 검증은 인터셉터로 처리하는 것이 더 적합하다.
 *   - study-service 의 InternalRequestFilter 와 동일한 패턴을 유지하기 위함이다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * InternalRequestFilter 빈 주입
     *
     * @Component 로 등록된 InternalRequestFilter 를 생성자 주입으로 받는다.
     * @RequiredArgsConstructor 가 final 필드의 생성자를 자동 생성한다.
     */
    private final InternalRequestFilter internalRequestFilter;

    /**
     * 인터셉터 등록
     *
     * addPathPatterns("/internal/**")
     *   - /internal/ 로 시작하는 모든 경로에만 인터셉터를 적용한다.
     *   - /api/** 경로는 대상이 아니므로 일반 외부 요청에는 영향이 없다.
     *   - api-gateway 에서 /internal/** 외부 접근을 차단하지만,
     *     만약 우회 접근이 발생하더라도 이 인터셉터가 2차 방어선 역할을 한다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalRequestFilter)
                .addPathPatterns("/internal/**");
    }
}
