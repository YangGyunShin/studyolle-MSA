package com.studyolle.infra.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.authentication.DisabledException;

import javax.sql.DataSource;
import java.io.IOException;

/**
 * Spring Security 전체 보안 설정을 담당하는 클래스입니다.
 *
 * - URL 접근권한 설정 (인가 정책)
 * - 로그인/로그아웃 설정
 * - Remember-Me 자동 로그인 설정
 * - 정적 자원 보안 제외 설정 등 포함
 *
 * ================= 전체 보안 흐름 요약 =================
 *
 * ① 로그인 흐름
 * - 사용자가 /login 경로에서 로그인 시도
 * - 로그인 성공 시 SecurityContextHolder에 인증 정보 저장
 *
 * ② Remember-Me 흐름
 * - 사용자가 자동 로그인 체크 → 쿠키에 series/token 저장
 * - 서버 DB(persistent_logins)에 series/token 저장
 *
 * DB 테이블 구조
 *
 * 프로젝트의 `PersistentLogins.java`가 이 토큰을 저장하는 테이블
 *
 * persistent_logins 테이블
 *   series   | username | token    | last_used
 *   abc123.. | user@..  | xyz789.. | 2026-02-06
 *
 * - series:    브라우저 세션을 식별하는 고유 ID (고정값)
 * - username:  누구의 토큰인지
 * - token:     실제 인증에 쓰이는 값 (로그인할 때마다 갱신됨)
 * - last_used: 마지막 사용 시각
 *
 * ③ 이후 방문 시 자동 로그인 처리
 * - 쿠키 → 서버의 persistent_logins 테이블 → 토큰 검증 → 자동 로그인 처리
 *
 * ④ URL 인가 흐름
 * - 공개 URL은 permitAll()로 누구나 접근 허용
 * - anyRequest().authenticated()로 나머지 요청은 인증 요구
 *
 * ⑤ 정적 자원 제외
 * - WebSecurityCustomizer 통해 CSS/JS/Image 요청은 보안 필터 자체를 거치지 않음
 *
 * ──────────────────────────────────────────────────────────────────
 * [변경 이력] 이메일 인증 정책 변경
 * ──────────────────────────────────────────────────────────────────
 *
 * [v2] 이메일 미인증 → 로그인 차단 (enabled=false, DisabledException)
 *   - permitAll()에 /check-email, /resend-confirm-email 추가
 *   - 커스텀 AuthenticationFailureHandler로 DisabledException 분기 처리
 *
 * [v3 - 현재] 이메일 미인증이어도 로그인 허용 (enabled=true 고정)
 *   - 기능 제한은 EmailVerificationInterceptor에서 처리
 *   - AuthenticationFailureHandler는 방어적으로 유지 (카운트 비활성화 등 향후 활용)
 */
@Configuration
@EnableWebSecurity // Spring Security 기본 보안 필터 활성화 (자동으로 SecurityFilterChain 등록됨)
@RequiredArgsConstructor // 생성자 자동 생성 (final 필드 주입)
public class SecurityConfig {

    // 로그인 시 사용자 정보를 로딩하는 서비스 (UserDetailsService 구현체)
    private final UserDetailsService userDetailsService;

    // DB 연결 (RememberMe 토큰 저장에 사용)
    private final DataSource dataSource;

    /**
     * 정적 자원에 대한 보안 검사 제외 설정
     * - 보안필터를 아예 거치지 않도록 제외시킴 (필터 자체 무시)
     * - 정적 리소스(css, js, image 등)은 보안필터를 탈 이유가 없으므로 제외하는 것이 성능에도 유리
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                "/css/**",
                "/js/**",
                "/images/**",
                "/favicon.ico",
                "/webjars/**",
                "/node_modules/**"
        );
    }

    /**
     * 메인 보안 필터 체인 설정
     * - URL 인가 정책, 로그인/로그아웃, RememberMe 까지 모두 설정
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // 1. URL 접근 인가 규칙 설정
        http.authorizeHttpRequests(auth -> auth
                // 인증 없이 누구나 접근 가능한 공개 URL 목록
                .requestMatchers(
                        "/",
                        "/login",
                        "/sign-up",
                        "/check-email-token",
                        "/email-login",
                        "/check-email-login",
                        "/login-link",
                        "/index",
                        "/css/**", "/images/**", "/js/**",
                        "/login-by-email",
                        "/search/study",
                        "/error",  // Spring Boot 에러 페이지 접근 허용 (없으면 status 999 발생)

                        // [추가] 이메일 인증 관련 페이지
                        // 회원가입 직후 비로그인 상태에서 접근해야 하므로 permitAll 필요
                        // - /check-email           : 이메일 확인 안내 페이지
                        // - /resend-confirm-email   : 인증 이메일 재전송
                        "/check-email",
                        "/resend-confirm-email"
                ).permitAll()

                // HTTP GET 방식으로만 허용되는 URL 예외 처리 (프로필 조회 공개 허용)
                .requestMatchers(HttpMethod.GET, "/profile/*").permitAll()

                // 관리자 전용 URL: ROLE_ADMIN만 접근 가능
                // /admin, /admin/members, /admin/studies, /admin/statistics, /admin/privacy
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // 나머지 요청은 모두 인증이 필요
                .anyRequest().authenticated()
        );

        // 2. Form 기반 로그인 설정 (Spring Security가 기본 제공하는 로그인 방식)
        http.formLogin(form -> form
                // 로그인 페이지 경로 지정 (기본 제공 로그인 페이지를 오버라이딩)
                .loginPage("/login")
                // 로그인 페이지 접근은 비인증 사용자도 가능
                .permitAll()

                // [추가] 커스텀 로그인 실패 핸들러
                // → 이메일 미인증(DisabledException) vs 일반 로그인 실패를 구분하여
                //   서로 다른 에러 메시지를 보여주기 위함
                .failureHandler(authenticationFailureHandler())
        );

        // 3. 로그아웃 설정
        http.logout(logout -> logout
                // 로그아웃 성공 시 이동할 URL 설정 (홈으로 이동)
                .logoutSuccessUrl("/")
        );

        // 4. 자동 로그인(Remember-Me) 설정
        http.rememberMe(remember -> remember
                // 자동 로그인 시 사용할 UserDetailsService 지정 (사용자 정보 로딩)
                .userDetailsService(userDetailsService)

                // PersistentTokenRepository 지정 → DB에 RememberMe 토큰을 영구 저장
                .tokenRepository(tokenRepository())
        );

        // 모든 보안 설정을 포함한 SecurityFilterChain 객체 반환
        return http.build();
    }

    /**
     * 로그인 실패 시 예외 유형에 따라 서로 다른 URL로 리다이렉트하는 핸들러
     *
     * ──────────────────────────────────────────────────────────────────
     * [현재 상태] 방어적으로 유지 중 (v3 이후 실제 발동하지 않음)
     * ──────────────────────────────────────────────────────────────────
     *
     * UserAccount의 enabled가 항상 true로 고정되었으므로 (v3 변경)
     * DisabledException은 현재 발생하지 않는다.
     * 이메일 미인증 제한은 EmailVerificationInterceptor에서 처리한다.
     *
     * 이 핸들러를 제거하지 않고 유지하는 이유:
     *   - 나중에 관리자가 계정을 비활성화하는 기능을 도입하면 다시 필요해짐
     *   - 제거해도 동작에 영향 없음 (발동할 조건이 없으므로)
     *   - 방어적 프로그래밍: 예상치 못한 DisabledException 발생 시 적절한 UX 제공
     *
     * ──────────────────────────────────────────────────────────────────
     * [원래 설계 의도 (v2 시절)]
     * ──────────────────────────────────────────────────────────────────
     *
     * v2에서는 enabled = account.isEmailVerified()였으므로
     * 이메일 미인증 → DisabledException → 이 핸들러가 /login?disabled로 리다이렉트
     * → login.html에서 "이메일 인증을 완료해주세요" 메시지 표시
     *
     * v3에서 인터셉터 방식으로 전환하면서 이 흐름은 비활성화됨.
     *
     * ──────────────────────────────────────────────────────────────────
     * [SimpleUrlAuthenticationFailureHandler란?]
     * ──────────────────────────────────────────────────────────────────
     *
     * Spring Security가 제공하는 기본 실패 핸들러 구현체이다.
     * setDefaultFailureUrl()로 기본 리다이렉트 URL을 설정하고,
     * onAuthenticationFailure()를 오버라이드하여 예외 유형별 분기 처리를 할 수 있다.
     */
    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

                // DisabledException: UserDetails.isEnabled()가 false일 때 발생
                // → 이메일 미인증 사용자가 로그인을 시도한 경우
                if (exception instanceof DisabledException) {
                    getRedirectStrategy().sendRedirect(request, response, "/login?disabled");
                    return;
                }

                // 그 외 예외 (BadCredentialsException, LockedException 등)
                // → 기본 실패 URL(/login?error)로 리다이렉트
                setDefaultFailureUrl("/login?error");
                super.onAuthenticationFailure(request, response, exception);
            }
        };
    }

    /**
     * Remember-Me 토큰을 DB에 저장하기 위한 저장소 설정
     * - Spring Security가 제공하는 기본 JdbcTokenRepositoryImpl 사용
     * - 내부적으로 'persistent_logins' 테이블을 자동 사용 (테이블 명/구조 반드시 일치)
     *
     * 이 코드에서 PersistentLogins.java(엔티티)를 직접 참조하는 곳은 없음!
     * 두 개의 서로 다른 기술이 "같은 테이블명"을 바라보는 구조:
     *
     *   PersistentLogins.java     → 테이블 자동 생성 담당 (JPA/Hibernate)
     *   JdbcTokenRepositoryImpl   → 토큰 읽기/쓰기/갱신 담당 (JDBC/SQL)
     *   SecurityConfig (여기)      → DB 연결 정보(dataSource)를 전달하는 설정
     *
     *   즉, PersistentLogins.java가 없어도 Remember-Me 로직 자체는 동작하지만,
     *   테이블이 자동 생성되지 않아 "테이블 없음" 에러가 발생함
     */
    @Bean
    public PersistentTokenRepository tokenRepository() {
        JdbcTokenRepositoryImpl tokenRepository = new JdbcTokenRepositoryImpl();
        tokenRepository.setDataSource(dataSource); // DB 연결 설정
        return tokenRepository;
    }
}

// 자동 로그인(Remember-Me) 설정
//
//   Remember-Me란?
//   로그인 시 "로그인 유지" 체크 → 브라우저를 닫았다 열어도 로그인 유지
//
//   동작 방식
//   [로그인 시]
//    1. 서버가 고유한 series(고정) + token(매번 변경) 쌍을 생성
//    2. 쿠키 → 브라우저에 저장 / DB(persistent_logins) → 서버에 저장
//
//   [재방문 시]
//    1. 브라우저가 쿠키(series, token)를 서버에 전송
//    2. 서버가 DB에서 series로 조회 → token 일치 확인 → 자동 로그인
//    3. token을 새 값으로 갱신 (series는 유지)
//
//   토큰 도난 감지 원리 (series + token 분리 설계)
//
//   [정상 흐름]
//    내가 접속 → series=AAA, token=111 → 성공 → token을 222로 갱신
//    내가 재접속 → series=AAA, token=222 → 성공 → token을 333으로 갱신
//
//   [해커가 쿠키를 탈취한 경우]
//    현재 상태:  내 쿠키(AAA, 222), DB(AAA, 222)
//    해커가 쿠키(AAA, 222) 탈취 후 접속 → 성공 → DB token이 333으로 갱신
//    내가 접속 → 쿠키(AAA, 222) 전송 → DB에는 333 → token 불일치!
//    → "series는 같은데 token이 다르다" = 누군가 내 토큰을 도용함
//    → 해당 series 전체 삭제 → 해커도 나도 자동 로그인 차단
//    → 사용자에게 비밀번호 재로그인 요구
//
//   만약 토큰 하나만 사용했다면?
//    → 해커가 로그인해도 "정상 로그인"으로 보여서 도난 감지 불가