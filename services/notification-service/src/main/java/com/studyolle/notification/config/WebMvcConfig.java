package com.studyolle.notification.config;

import com.studyolle.notification.filter.InternalRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 인터셉터 등록 설정 클래스.
 *
 * =============================================
 * 인터셉터란?
 * =============================================
 *
 * 인터셉터는 HTTP 요청이 컨트롤러에 도달하기 전/후에 공통 로직을 실행하는 컴포넌트다.
 * 필터(Filter)와 비슷하지만 Spring MVC 레이어에서 동작하므로
 * HandlerMethod 정보 등 Spring 컨텍스트를 활용할 수 있다.
 *
 * =============================================
 * /internal/** 경로에만 적용하는 이유
 * =============================================
 *
 * InternalRequestFilter 는 X-Internal-Service 헤더를 검증하는 인터셉터다.
 * 이 검증이 필요한 경로는 /internal/** 뿐이다.
 * addPathPatterns("/internal/**") 로 적용 범위를 제한해
 * /api/** 같은 일반 사용자 요청에는 불필요한 헤더 검증이 발생하지 않도록 한다.
 *
 * 요청 흐름:
 *   [api-gateway]          → /internal/** 경로 전면 차단 (403)   ← 외부 접근 1차 차단
 *   [InternalRequestFilter] → X-Internal-Service 헤더 검증        ← 내부 접근 2차 차단
 *   [NotificationInternalController] → 실제 비즈니스 로직 처리
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalRequestFilter internalRequestFilter;

    /**
     * InternalRequestFilter 를 /internal/** 경로에만 등록한다.
     *
     * addPathPatterns("/internal/**") : /internal/ 로 시작하는 모든 경로에 적용.
     * excludePathPatterns() 를 추가하면 특정 경로를 제외할 수 있다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalRequestFilter)
                .addPathPatterns("/internal/**");
    }
}
