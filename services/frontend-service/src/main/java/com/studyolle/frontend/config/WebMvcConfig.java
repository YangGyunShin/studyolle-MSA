package com.studyolle.frontend.config;

import com.studyolle.frontend.common.EmailVerifiedInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 — 인터셉터 등록 전담.
 *
 * ====================================================================
 * [이 클래스의 유일한 역할]
 * ====================================================================
 * EmailVerifiedInterceptor 를 Spring MVC 의 HandlerInterceptor 체인에 등록하고,
 * 어느 경로에 적용할지 선언한다.
 *
 * 인터셉터 자체는 "어떻게 검사하는가" 만 알고, 이 클래스는 "어디에 적용하는가" 만 안다.
 * 두 관심사를 분리해두면 적용 대상 변경 시 이 파일만 고치면 된다.
 *
 * ====================================================================
 * [addPathPatterns / excludePathPatterns 의 동작 원리]
 * ====================================================================
 * 두 패턴 모두 Spring 의 Ant Style 경로 매칭을 쓴다.
 *
 *   *;                = 한 세그먼트 안의 임의 문자열
 *                    /study/*;/new-event  → /study/java-study/new-event 매칭
 *                                              /study/java-study/sub/new-event 는 매칭 안 됨
 *
 *   **    = 여러 세그먼트에 걸친 임의 경로
 *                    /settings/**    → /settings/profile, /settings/tags/edit 모두 매칭
 *
 *   {varName}     = 경로 변수 (매칭에는 영향 없음)
 *
 * addPathPatterns    : 인터셉터가 적용될 경로 화이트리스트
 * excludePathPatterns: 위 화이트리스트 중에서 제외할 경로 블랙리스트
 *
 * include 가 먼저 매칭되고, 그 중 exclude 에 해당하는 것이 빠진다.
 *
 * ====================================================================
 * [왜 include 를 명시적으로 나열하는가 — "필수" 가 아닌 "허용" 기반 선택]
 * ====================================================================
 * 방식 1 (여기서 채택): 인증 필수 경로를 명시 — "화이트리스트"
 *   addPathPatterns("/new-study", "/settings/&#42;&#42;", ...)
 *
 * 방식 2: 모든 경로에 적용 + 예외만 제외 — "블랙리스트"
 *   addPathPatterns("/&#42;&#42;")
 *   excludePathPatterns("/login", "/sign-up", "/check-email", ...)
 *
 * 장단점:
 *   방식 1 — 새 페이지 추가 시 "이 페이지도 인증 필수인가?" 를 의식적으로 결정해야 함
 *             (보안 측면 : 실수로 인증 필수 경로가 누락될 수 있음)
 *   방식 2 — 기본값이 "인증 필수" 라 누락 위험 없음
 *             (UX 측면 : 비로그인 허용 페이지를 깜빡하고 제외에 안 넣으면 UX 가 깨짐)
 *
 * 이 프로젝트에서는 방식 1 을 채택했다. 이유:
 *   - 비로그인 허용 페이지 (홈, 스터디 조회, 검색 등) 가 많다.
 *   - 인증 필수 페이지가 더 적고 명확히 구분된다 (쓰기 + 개인 설정 + 프로필 조회).
 *   - "이 페이지에는 왜 인터셉터가 걸렸지?" 를 이해하려 할 때 WebMvcConfig 한 곳만
 *     보면 되도록 명시성을 중시.
 *
 * ====================================================================
 * [적용 대상 페이지 범위 — 2026-04-21 결정]
 * ====================================================================
 * 허용되는 것:
 *   - 홈 / 로그인 / 회원가입 / 이메일 인증 처리
 *   - 스터디 목록 · 검색 · 상세 조회 (GET)
 *   - check-email / check-email-required / resend-confirm-email / logout
 *
 * 차단되는 것 (이 인터셉터가 리다이렉트):
 *   - /new-study                    — 스터디 생성
 *   - /study/*settings/**;         — 스터디 설정
 *   - /study/*;/new-event            — 모임 생성
 *   - /study/*;/events/*;/edit    — 모임 수정
 *   - /settings/**;              — 프로필/비밀번호/알림/태그/지역 설정
 *   - /notifications                    — 알림 목록
 *   - /profile/*;                    — 프로필 공개 조회 (시스템 내부인만 허용)
 *
 * 차단 대상에는 "가입/탈퇴" 는 포함되지 않는다. 그것은 API 호출 (fetch POST) 이지
 * 페이지가 아니므로 인터셉터 경로 매칭이 일어나지 않는다. 대신 백엔드
 * study-service 의 해당 엔드포인트에서 이메일 인증 체크 (2차 방어선) 를 수행한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EmailVerifiedInterceptor emailVerifiedInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(emailVerifiedInterceptor)
                .addPathPatterns(
                        // 스터디 쓰기 기능
                        "/new-study",
                        "/study/*/settings/**",
                        "/study/*/new-event",
                        "/study/*/events/*/edit",

                        // 계정 설정 (모든 /settings/* 하위)
                        "/settings/**",

                        // 알림 목록 — 인증된 사용자만 자기 알림 목록을 본다
                        "/notifications",

                        // 프로필 조회 — 양균님 결정에 따라 시스템 내부인만 허용
                        // /profile/john, /profile/철수 등 닉네임 부분이 와일드카드로 매칭됨
                        "/profile/*"
                )
                .excludePathPatterns(
                        // 이 인터셉터가 리다이렉트하는 대상 페이지 자체는 당연히 제외
                        "/check-email-required",

                        // 이메일 인증 흐름에 필요한 페이지들 — 막으면 영원히 인증 못 함
                        "/check-email",
                        "/check-email-token",
                        "/email-login",

                        // 정적 리소스 — addPathPatterns 에 /settings/** 등만 있어
                        // 원래 매칭 안 되지만 방어적으로 명시
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico"
                );
    }
}