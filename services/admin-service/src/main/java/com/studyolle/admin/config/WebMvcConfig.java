package com.studyolle.admin.config;

import com.studyolle.admin.filter.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 — 현재는 인터셉터 등록만 담당한다.
 *
 * [WebMvcConfigurer 인터페이스]
 * Spring MVC 의 기본 설정을 커스터마이징하기 위한 표준 확장 지점이다.
 * 구현할 메서드가 여러 개 있지만(CORS, MessageConverter, ViewResolver 등)
 * 필요한 것만 오버라이드하면 된다. 나머지는 기본 구현을 그대로 쓴다.
 *
 * [@RequiredArgsConstructor + final 필드]
 * Lombok 이 final 필드를 파라미터로 받는 생성자를 자동 생성한다.
 * Spring 은 생성자 파라미터를 보고 Bean 을 자동 주입한다(생성자 주입).
 * 필드 주입(@Autowired) 대신 생성자 주입을 쓰는 이유는 테스트 용이성과 불변성 때문이다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/admin/** 경로에만 인터셉터를 적용한다.
        // 예를 들어 actuator 엔드포인트(/actuator/health 등) 는 Eureka 헬스 체크에 쓰이므로 인터셉터 대상에서 제외해야 한다.
        // addPathPatterns 로 적용 범위를 한정하는 것이 핵심이다.
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}