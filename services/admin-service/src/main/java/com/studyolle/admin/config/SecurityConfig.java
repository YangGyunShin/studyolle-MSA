package com.studyolle.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * admin-service 의 Spring Security 설정.
 *
 * [왜 Security 를 건드리는가]
 * build.gradle 에 spring-boot-starter-security 가 포함되어 있으면, Spring Boot 는
 * 자동으로 "모든 요청에 Basic Auth 요구" 라는 기본 보안을 활성화한다. 이대로 두면
 * 브라우저에서 admin-service 에 접근할 때마다 로그인 팝업이 뜬다. 이것을 무력화해야 한다.
 *
 * [그럼 왜 Security 의존성을 뺄 수 없는가]
 * 뺄 수 있긴 하다. 다만 나중에 "특정 엔드포인트만 별도 검증" 같은 기능이 필요해지면
 * Security 를 다시 넣어야 하고, 그때 이 설정을 복구해야 한다. 어차피 들어가 있는 의존성을 깔끔하게 비활성화하는 것이 유지보수에 더 낫다.
 *
 * [실제 권한 검증은 어디서 하는가]
 * 이 서비스의 권한 검증은 두 계층으로 이루어진다.
 *   1차: api-gateway 의 AdminRoleFilter (JWT 에서 role 을 꺼내 확인)
 *   2차: admin-service 의 AdminAuthInterceptor (X-Account-Role 헤더 재확인)
 * Security 필터 체인은 그 중 어느 것도 담당하지 않는다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF: JWT 기반 stateless API 이므로 불필요
                // (CSRF 공격은 세션 쿠키가 자동 전송되는 환경에서만 성립한다)
                .csrf(csrf -> csrf.disable())

                // 세션: 쓰지 않음 — 매 요청마다 헤더로 사용자 식별
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 모든 요청을 Security 단에서는 일단 통과시킨다.
                // 실제 인가는 이후 MVC Interceptor (AdminAuthInterceptor) 에서 수행한다.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())

                // 기본 로그인 폼과 Basic Auth 프롬프트를 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}