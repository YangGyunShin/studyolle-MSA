package com.studyolle.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 클래스
 *
 * [이 클래스가 필요한 이유]
 *
 * event-service 는 build.gradle 에 spring-boot-starter-security 의존성이 있다.
 * Spring Boot 는 Security 의존성이 존재하면 자동으로 모든 요청에 인증을 요구하는 기본 설정을 적용한다.
 * 이 기본 설정을 그대로 두면 api-gateway 에서 정상적으로 통과한 요청도 event-service 에서 전부 막힌다.
 *
 * 따라서 이 클래스에서 기본 설정을 명시적으로 재정의하여 Spring Security 의 인증 기능을 비활성화한다.
 *
 * [MSA 에서 인증을 api-gateway 에 위임하는 이유]
 *
 * 모노리틱에서는 각 서비스가 직접 JWT 를 검증했다.
 * MSA 에서는 모든 외부 요청이 api-gateway 를 통과하므로 JWT 검증을 api-gateway 한 곳에서만 수행한다.
 *
 *   [브라우저] -- JWT --> [api-gateway] -- X-Account-Id 헤더 --> [event-service]
 *
 * event-service 는 이미 api-gateway 가 검증한 요청만 받는다.
 * 여기서 Spring Security 가 또 인증을 요구하는 것은 불필요한 중복이다.
 *
 * 실질적인 권한 검증은 다음 두 가지로 처리한다:
 *   1. EventService.validateManager(): 모임 운영자 여부를 X-Account-Id 헤더로 확인
 *   2. InternalRequestFilter: /internal/** 경로에 X-Internal-Service 헤더 검증
 *
 * [CSRF 를 비활성화하는 이유]
 *
 * CSRF(Cross-Site Request Forgery) 방어는 브라우저가 쿠키를 자동으로 포함하는 세션 기반 인증에서 필요하다.
 * MSA 에서는 JWT 를 Authorization 헤더 또는 쿠키로 전달하지만,
 * api-gateway 와 각 서비스 간 통신은 서버 간 직접 호출이므로 CSRF 공격이 발생할 수 없다.
 * 따라서 CSRF 방어를 비활성화한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Spring Security 필터 체인 설정
     *
     * SecurityFilterChain 은 HTTP 요청이 들어올 때 순서대로 실행되는 필터들의 묶음이다.
     * @Bean 으로 등록하면 Spring Boot 의 자동 설정보다 이 설정이 우선 적용된다.
     *
     * csrf.disable()
     *   - CSRF 토큰 검증 필터를 비활성화한다.
     *   - REST API + JWT 방식에서는 불필요하다.
     *
     * authorizeHttpRequests - anyRequest().permitAll()
     *   - 모든 경로의 요청을 인증 없이 통과시킨다.
     *   - 실제 인증은 api-gateway 의 JwtAuthenticationFilter 가 담당한다.
     *   - /internal/** 경로의 접근 제어는 InternalRequestFilter(인터셉터)가 별도로 처리한다.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
