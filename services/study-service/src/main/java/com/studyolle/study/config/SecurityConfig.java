package com.studyolle.study.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * study-service Security 설정
 *
 * account-service 의 SecurityConfig 와 동일한 패턴.
 * JWT 검증은 api-gateway 에서 완료되어 X-Account-Id 헤더가 주입되므로,
 * 이 서비스는 Spring Security 의 인증/인가 로직이 필요 없다.
 *
 * [모노리틱과의 차이]
 * 모노리틱에서는 Spring Security 가 세션 기반 인증을 전담했으나,
 * MSA 에서는 api-gateway 가 그 역할을 대신하므로 SecurityConfig 가 단순해진다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // api-gateway 까지 도달한 요청은 이미 검증된 것이므로 전부 허용
                // /internal/** 보호는 InternalRequestFilter(HandlerInterceptor) 가 담당
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}