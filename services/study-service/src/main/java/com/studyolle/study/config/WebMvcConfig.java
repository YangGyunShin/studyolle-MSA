package com.studyolle.study.config;

import com.studyolle.study.filter.InternalRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 설정 — InternalRequestFilter 인터셉터 등록
 *
 * InternalRequestFilter 는 /internal/** 경로에 대해
 * X-Internal-Service 헤더 존재 여부를 검증한다.
 * 인터셉터로 구현하는 이유는 모든 컨트롤러에 중복 검증 코드를 넣지 않기 위함이다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalRequestFilter internalRequestFilter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalRequestFilter)
                .addPathPatterns("/internal/**");
    }
}