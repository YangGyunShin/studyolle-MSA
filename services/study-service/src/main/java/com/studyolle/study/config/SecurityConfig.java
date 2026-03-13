package com.studyolle.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * study-service Spring Security 설정.
 *
 * =============================================
 * 모노리틱과의 차이
 * =============================================
 *
 * 모노리틱에서는 Spring Security 가 핵심 역할을 담당했다:
 * - 세션 기반 로그인 처리 (formLogin)
 * - SecurityContext 에 Account 저장 → @CurrentUser 로 꺼내기
 * - 경로별 인증/인가 규칙 (.authorizeHttpRequests().authenticated())
 *
 * MSA 에서는 이 모든 역할을 api-gateway 가 대신한다:
 * - JWT 검증 (JwtAuthenticationFilter)
 * - 권한 확인 (AdminRoleFilter)
 * - 사용자 정보를 X-Account-Id 헤더로 주입
 *
 * api-gateway 를 통과한 요청은 이미 검증된 요청이므로
 * study-service 의 Spring Security 는 모든 요청을 허용하기만 하면 된다.
 *
 * =============================================
 * 각 설정 항목 설명
 * =============================================
 *
 * csrf.disable():
 *   CSRF(Cross-Site Request Forgery) 공격 방어를 비활성화한다.
 *   CSRF 는 브라우저의 쿠키 기반 세션을 악용하는 공격이다.
 *   JWT + Stateless 방식에서는 쿠키/세션을 사용하지 않으므로 CSRF 공격이 불가능하다.
 *   따라서 이 방어 기능을 켜둘 필요가 없다.
 *
 * sessionManagement(STATELESS):
 *   서버가 세션을 생성하지 않는다.
 *   사용자 상태를 서버 메모리에 저장하지 않으므로 수평 확장(서버 대수 증가)이 용이하다.
 *   JWT 방식에서는 클라이언트가 매 요청마다 토큰을 보내므로 세션이 필요 없다.
 *
 * formLogin.disable():
 *   Spring Security 의 기본 로그인 폼(HTML 로그인 페이지)을 비활성화한다.
 *   REST API 서버에는 HTML 폼이 필요 없다.
 *
 * httpBasic.disable():
 *   HTTP Basic 인증(Authorization: Basic base64encoded)을 비활성화한다.
 *   이 방식은 보안에 취약하고 MSA 아키텍처에서 사용하지 않는다.
 *
 * anyRequest().permitAll():
 *   모든 요청을 인증 없이 허용한다.
 *   /internal/** 경로 보호는 Spring Security 가 아닌 InternalRequestFilter 가 담당한다.
 *   api-gateway 를 통과한 외부 요청은 이미 검증되었으므로 여기서 다시 검증하지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)         // CSRF 방어 불필요 (JWT + Stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용
                .formLogin(AbstractHttpConfigurer::disable)    // HTML 로그인 폼 불필요
                .httpBasic(AbstractHttpConfigurer::disable)    // HTTP Basic 인증 불필요
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // 모든 요청 허용

        return http.build();
    }
}