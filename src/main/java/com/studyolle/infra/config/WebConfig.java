package com.studyolle.infra.config;

import com.studyolle.modules.account.EmailVerificationInterceptor;
import com.studyolle.modules.notification.NotificationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.StaticResourceLocation;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring MVC 설정 확장 클래스 — 커스텀 인터셉터를 등록하는 역할
 *
 * =====================================================
 *   📌 이 클래스가 왜 필요한가?
 *
 *   이 프로젝트의 모든 페이지 상단에는 "알림 종 아이콘(🔔)"이 있음
 *   → 읽지 않은 알림이 있으면 종이 강조됨
 *   → 이 정보(hasNotification)는 모든 페이지에서 필요함
 *
 *   문제: 모든 컨트롤러 메서드에 알림 조회 코드를 복붙하면?
 *   → 수십 개의 메서드에 동일한 코드가 중복됨 😱
 *
 *   해결: 인터셉터(Interceptor)를 사용
 *   → "모든 요청이 처리된 후, 자동으로 알림 여부를 model에 추가해줘"
 *   → 컨트롤러는 알림 코드를 전혀 신경 쓰지 않아도 됨
 *
 *   이 WebConfig가 하는 일:
 *   → NotificationInterceptor를 Spring MVC에 "등록"하는 설정 클래스
 *   → 정적 리소스(CSS, JS 등)는 제외시켜서 불필요한 DB 조회 방지
 *
 * =====================================================
 *   📌 인터셉터(Interceptor)란?
 *
 *   컨트롤러의 앞/뒤에서 자동으로 실행되는 "공통 처리기"
 *   비유: 호텔 로비의 안내 데스크 — 모든 손님이 방에 들어가기 전/후에 자동으로 거치는 곳
 *
 *   Spring MVC 요청 흐름에서의 위치:
 *
 *    사용자 요청
 *        ↓
 *    DispatcherServlet
 *        ↓
 *    [Interceptor.preHandle()]  ← 컨트롤러 실행 전
 *        ↓
 *    Controller (비즈니스 로직 처리)
 *        ↓
 *    [Interceptor.postHandle()] ← 컨트롤러 실행 후, View 렌더링 전
 *        ↓     ⭐ NotificationInterceptor가 여기서
 *        ↓        hasNotification을 model에 추가
 *    View 렌더링 (Thymeleaf)
 *        ↓
 *    [Interceptor.afterCompletion()] ← 렌더링 완료 후
 *        ↓
 *    응답 반환
 *
 * =====================================================
 *   📌 왜 정적 리소스를 제외하는가?
 *
 *   하나의 HTML 페이지를 로딩하면, 브라우저가 추가로 요청하는 것들:
 *    - /css/style.css      ← 스타일 파일
 *    - /js/app.js          ← 스크립트 파일
 *    - /images/logo.png    ← 이미지 파일
 *
 *   이런 정적 파일 요청에도 인터셉터가 실행되면?
 *    → CSS 파일 하나 불러올 때마다 DB에서 알림 개수를 조회하게 됨
 *    → 페이지 하나 열 때 불필요한 DB 쿼리가 수십 번 발생
 *    → 성능 낭비!
 *
 *   그래서 정적 리소스 경로는 인터셉터 대상에서 제외시키는 것
 */
@Configuration  // 스프링이 이 클래스를 설정 클래스로 인식하게 함
@RequiredArgsConstructor  // final 필드에 대한 생성자 자동 생성 (의존성 주입)
public class WebConfig implements WebMvcConfigurer {
    // WebMvcConfigurer: Spring MVC의 기본 설정을 커스터마이징할 수 있는 인터페이스
    // → 인터셉터 등록, CORS 설정, 뷰 리졸버 설정 등을 오버라이드할 수 있음

    // NotificationInterceptor 주입
    // → 이 인터셉터가 "모든 페이지 요청 후 알림 여부를 model에 추가"하는 실제 로직을 담고 있음
    // → @Component로 등록된 빈이므로 생성자 주입으로 가져옴
    private final NotificationInterceptor notificationInterceptor;
    private final EmailVerificationInterceptor emailVerificationInterceptor;

    /**
     * 인터셉터를 Spring MVC에 등록하는 메서드
     *
     * NotificationInterceptor를 만들기만 해서는 아무 일도 안 일어남!
     * 여기서 registry에 등록해야 Spring MVC가 "아, 이 인터셉터를 매 요청마다 실행해야 하는구나" 알게 됨
     *
     * 비유: 직원(NotificationInterceptor)을 채용했으면, 근무 배치표(registry)에 등록해야 실제로 일하는 것과 같은 원리
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ──────────────────────────────────────────────────────────
        // 1단계: 인터셉터를 적용하지 않을 "정적 리소스 경로 목록" 수집
        // ──────────────────────────────────────────────────────────

        /*
          StaticResourceLocation.values()
          → Spring Boot가 미리 정의해둔 정적 리소스 경로 열거형
          → 내부적으로 다음과 같은 경로들을 포함하고 있음:
             /css/**, /js/**, /images/**, /webjars/**, /favicon.ico 등

          .flatMap(StaticResourceLocation::getPatterns)
          → 각 열거형 값이 여러 경로 패턴을 가질 수 있으므로
             중첩된 Stream을 하나의 평탄한 Stream으로 변환

          .collect(Collectors.toList())
          → 최종적으로 List<String>으로 수집

          결과 예시: ["/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", ...]
         */
        List<String> staticResourcesPath = Arrays.stream(StaticResourceLocation.values())
                .flatMap(StaticResourceLocation::getPatterns)
                .collect(Collectors.toList());

        // 이 프로젝트에서 사용하는 프론트엔드 라이브러리 경로도 정적 리소스이므로 추가
        // (Spring Boot 기본 목록에는 node_modules가 없음)
        staticResourcesPath.add("/node_modules/**");

        // ──────────────────────────────────────────────────────────
        // 2단계: 인터셉터 등록 + 정적 리소스 경로 제외
        // ──────────────────────────────────────────────────────────

        /*
          .addInterceptor(notificationInterceptor)
          → NotificationInterceptor를 Spring MVC 인터셉터로 등록
          → 이제 모든 요청에서 postHandle()이 자동 호출됨

          .excludePathPatterns(staticResourcesPath)
          → 위에서 수집한 정적 리소스 경로는 인터셉터 대상에서 제외
          → /css/style.css 같은 요청에서는 DB 알림 조회를 하지 않음

          최종 효과:
          ✅ /profile, /study/xxx, /notifications → 인터셉터 실행 (알림 확인)
          ❌ /css/**, /js/**, /images/**, /node_modules/** → 인터셉터 건너뜀
         */
        registry.addInterceptor(notificationInterceptor)
                .excludePathPatterns(staticResourcesPath);

        // 추가: 이메일 인증 인터셉터 (특정 URL에만 적용)
        registry.addInterceptor(emailVerificationInterceptor)
                .addPathPatterns(
                        "/new-study",
                        "/study/*/join",
                        "/study/*/leave",
                        "/study/*/new-event",
                        "/study/*/events/*/enroll",
                        "/study/*/events/*/disenroll"
                );
    }
}