package com.studyolle.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 클래스.
 *
 * =============================================
 * 이 서비스의 인증/인가 전략
 * =============================================
 *
 * notification-service 는 Spring Security 를 최소한으로만 사용한다.
 * JWT 검증은 api-gateway 에서 전담하고,
 * 이 서비스는 X-Account-Id 헤더를 신뢰하는 방식으로 사용자를 식별한다.
 *
 * 따라서 Security 필터 체인에서 모든 요청을 permitAll() 로 열어둔다.
 * 실질적인 접근 제어는 아래 두 계층이 담당한다:
 *
 *   1. api-gateway        : 외부 요청의 JWT 검증 (JwtAuthenticationFilter)
 *   2. InternalRequestFilter : /internal/** 경로의 X-Internal-Service 헤더 검증
 *
 * =============================================
 * CSRF 를 비활성화하는 이유
 * =============================================
 *
 * CSRF(Cross-Site Request Forgery) 보호는 브라우저 기반 세션 인증에서 필요하다.
 * 이 서비스는 세션 없이 JWT + 헤더 기반으로 동작하므로 CSRF 공격이 의미 없다.
 * CSRF 를 활성화하면 POST / PATCH / DELETE 요청 시 CSRF 토큰이 필요해
 * API 호출이 불필요하게 복잡해진다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Security 필터 체인을 구성한다.
     *
     * csrf().disable()     : REST API 서버이므로 CSRF 비활성화
     * anyRequest().permitAll() : 모든 경로를 허용 (실질적 접근 제어는 다른 계층에서 처리)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
