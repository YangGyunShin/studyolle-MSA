package com.studyolle.study.config;

import com.studyolle.study.filter.InternalRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 — 인터셉터 등록.
 *
 * =============================================
 * WebMvcConfigurer 구현 방식
 * =============================================
 *
 * WebMvcConfigurer 는 Spring MVC 의 기본 설정을 유지하면서
 * 일부 설정만 커스터마이징할 수 있는 인터페이스다.
 * 필요한 메서드만 @Override 하면 되고, 나머지는 Spring 이 기본값을 적용한다.
 *
 * addInterceptors(InterceptorRegistry) 를 오버라이드하면
 * 어떤 인터셉터를 어떤 경로 패턴에 적용할지 선언할 수 있다.
 *
 * =============================================
 * addPathPatterns("/internal/**") 의 의미
 * =============================================
 *
 * InternalRequestFilter 를 /internal/** 경로에만 적용한다.
 * 이 설정이 없으면 모든 요청에 적용되어 일반 API 호출도 X-Internal-Service 헤더를 요구하게 된다.
 * /api/studies/** 처럼 외부 클라이언트가 호출하는 경로에는 이 인터셉터가 작동하지 않는다.
 *
 * 패턴 문법:
 *   /internal/**  → /internal/ 로 시작하는 모든 경로 (하위 경로 포함)
 *   /internal/*   → /internal/ 바로 아래 한 단계 경로만 (하위 경로 미포함)
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalRequestFilter internalRequestFilter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalRequestFilter)
                .addPathPatterns("/internal/**"); // /internal/ 로 시작하는 모든 경로에만 적용
    }
}