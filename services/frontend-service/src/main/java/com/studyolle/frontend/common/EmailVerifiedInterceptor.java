package com.studyolle.frontend.common;

import com.studyolle.frontend.account.client.AccountInternalClient;
import com.studyolle.frontend.account.dto.AccountSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 이메일 인증 필수 페이지 접근을 제어하는 Spring MVC HandlerInterceptor.
 *
 * ====================================================================
 * [이 인터셉터의 역할]
 * ====================================================================
 * WebMvcConfig 에 등록된 특정 경로 패턴에 한해 요청을 가로채서
 * "현재 사용자가 이메일 인증을 완료했는지" 를 실시간으로 검증한다.
 *
 * 인증 완료 → true 반환 → 원래 컨트롤러로 요청 전달
 * 인증 미완료 → /check-email-required 로 리다이렉트 + false 반환 → 컨트롤러 실행 차단
 *
 * ====================================================================
 * [왜 Filter 가 아니라 Interceptor 인가]
 * ====================================================================
 * Servlet Filter (예: InternalRequestFilter) 와 HandlerInterceptor 는
 * 언뜻 비슷해 보이지만 다음과 같은 차이가 있다.
 *
 *   Filter:      DispatcherServlet 앞에서 동작. Spring 의 빈 주입이 까다롭다.
 *                경로 패턴도 Servlet spec 기준이라 Spring MVC 의 경로 매칭과 다르다.
 *
 *   Interceptor: DispatcherServlet 안에서 동작. 완전한 Spring MVC 맥락이 있어
 *                @Component, @Autowired 가 자연스럽다. 경로 패턴도 Ant Style 로
 *                /study/&#42;/new-event 같은 와일드카드가 지원된다.
 *
 * 이 프로젝트의 기존 InternalRequestFilter 들은 "모든 요청에 대해 헤더 검사" 라는 단순한 목적이라 Filter 가 적합했다.
 * 반면 이 검증은 경로 패턴 기반이고 AccountInternalClient 주입이 필요하므로 Interceptor 가 자연스럽다.
 *
 * ====================================================================
 * [왜 preHandle 만 구현하는가]
 * ====================================================================
 * HandlerInterceptor 인터페이스는 세 가지 hook 을 제공한다.
 *
 *   preHandle(request, response, handler)
 *     — 컨트롤러 실행 전. false 를 반환하면 컨트롤러 실행 자체가 차단된다.
 *
 *   postHandle(request, response, handler, modelAndView)
 *     — 컨트롤러 실행 후, View 렌더링 전. Model 을 수정할 기회가 있다.
 *
 *   afterCompletion(request, response, handler, ex)
 *     — View 렌더링 후. 리소스 정리 등에 사용.
 *
 * 이 인터셉터는 "들어오는 요청을 막거나 통과시키는" 단 하나의 책임만 가지므로 preHandle 만 구현한다.
 * default 메서드이므로 나머지는 자동으로 "아무 것도 안 함" 이 된다.
 *
 * ====================================================================
 * [호출 빈도와 성능]
 * ====================================================================
 * 이 인터셉터는 요청 한 번마다 account-service 를 한 번 호출한다.
 * 페이지 렌더링 경로에 미묘한 오버헤드가 추가되는 셈이다.
 *
 * 성능 최적화 방안 (현재는 미적용, 필요시 향후 도입):
 *   1. Redis 캐싱: emailVerified 결과를 5분 TTL 로 캐싱 → 인증 완료 시 INVALIDATE
 *   2. JWT claim 에 emailVerified 포함 → 게이트웨이 헤더로 전달 → 이 호출 생략 가능
 *      (단점: JWT 발급 시점 스냅샷이라 방금 인증한 사용자가 즉시 풀리지 않음)
 *
 * 학습 프로젝트에서는 실시간 정확성을 우선하여 캐시 없이 매번 호출한다.
 * 실제 사용량이 늘어 성능 병목이 생기면 그때 Redis 캐시를 추가한다.
 *
 * ====================================================================
 * [적용 경로]
 * ====================================================================
 * 어느 경로에 이 인터셉터가 적용되는지는 이 클래스가 결정하지 않는다.
 * WebMvcConfig 의 InterceptorRegistry 설정에서 결정한다.
 * 따라서 이 클래스는 "어떻게 검사하는가" 만 책임지고, "어디에 적용하는가" 는 WebMvcConfig 의 책임이다.
 * 관심사 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerifiedInterceptor implements HandlerInterceptor {

    private final AccountInternalClient accountInternalClient;

    /**
     * 컨트롤러 실행 전에 호출되는 검사 메서드.
     *
     * @param request  들어온 HTTP 요청
     * @param response 응답 객체 — 리다이렉트를 여기에 쓴다
     * @param handler  요청을 처리할 컨트롤러 메서드 객체 (이 인터셉터는 사용하지 않음)
     * @return true 면 컨트롤러로 요청을 전달, false 면 차단
     * @throws Exception sendRedirect 가 IOException 을 던질 수 있음
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // -------------------------------------------------------------
        // 1. X-Account-Id 헤더 추출
        //
        // api-gateway 의 OptionalJwtFilter 가 토큰을 검증한 뒤 이 헤더를 주입한다.
        // 비로그인 사용자 요청에는 이 헤더가 없다.
        // -------------------------------------------------------------
        String accountIdHeader = request.getHeader("X-Account-Id");

        // -------------------------------------------------------------
        // 2. 비로그인은 이 인터셉터의 관심사가 아님 — 통과시킨다
        //
        // "이메일 인증 안 했으므로 차단" 과 "비로그인이므로 차단" 은 다른 문제다.
        // 비로그인 차단은 각 페이지 컨트롤러의 기존 로직이나 게이트웨이가 담당한다.
        //
        // 이 인터셉터가 비로그인까지 막으려고 하면 책임 과잉이 된다.
        // 예: /new-study 는 비로그인도 막아야 하지만, 그건 StudyPageController 가
        //     accountId == null 이면 /login 으로 보내는 기존 로직으로 처리한다.
        //     이 인터셉터는 "로그인했는데 이메일 인증만 안 한" 중간 상태만 다룬다.
        // -------------------------------------------------------------
        if (accountIdHeader == null || accountIdHeader.isBlank()) {
            return true;
        }

        Long accountId;
        try {
            accountId = Long.parseLong(accountIdHeader);
        } catch (NumberFormatException e) {
            // 비정상적인 헤더 값 — 통과시키되 로그는 남김
            log.warn("잘못된 X-Account-Id 헤더: {}", accountIdHeader);
            return true;
        }

        // -------------------------------------------------------------
        // 3. account-service 에 현재 상태 질의
        //
        // getAccountSummary 는 AccountSummaryDto 를 반환한다.
        // 네트워크 오류 / 계정 삭제 등으로 null 이 반환될 수 있다.
        // -------------------------------------------------------------
        AccountSummaryDto account;
        try {
            account = accountInternalClient.getAccountSummary(accountId);
        } catch (Exception e) {
            // account-service 장애 시 페이지 자체를 막으면 사용자 경험이 너무 나쁘다.
            // 통과시키되 에러 로그를 남겨 운영자가 파악하게 한다.
            // 백엔드 2차 방어선이 있으므로 이 계층이 한 번 실패해도 보안이 뚫리지는 않는다.
            log.error("이메일 인증 검사 중 account-service 호출 실패: accountId={}", accountId, e);
            return true;
        }

        // -------------------------------------------------------------
        // 4. 계정이 사라진 경우 (탈퇴 등)
        //
        // 토큰은 있는데 계정 DB 에는 없는 상태.
        // 홈으로 리다이렉트하면 HomeController 가 비로그인으로 재렌더링한다.
        // -------------------------------------------------------------
        if (account == null) {
            log.warn("토큰의 accountId 에 해당하는 계정이 존재하지 않음: accountId={}", accountId);
            response.sendRedirect("/");
            return false;
        }

        // -------------------------------------------------------------
        // 5. 이메일 인증 완료 → 통과
        // -------------------------------------------------------------
        if (account.isEmailVerified()) {
            return true;
        }

        // -------------------------------------------------------------
        // 6. 이메일 인증 미완료 → 안내 페이지로 리다이렉트
        //
        // sendRedirect 는 302 Found 응답을 만들어 브라우저가 Location 헤더의 URL 로 자동 이동하게 한다.
        // POST 요청이라도 브라우저는 GET 으로 리다이렉트 대상 URL 을 요청하므로 안내 페이지는 GET 만 구현하면 된다.
        //
        // request.getRequestURI() 를 "next" 쿼리로 붙여서 "인증 후 원래 가려던 곳으로" 자동 복귀시키는 것도 가능하지만,
        // 이메일 인증이라는 흐름 자체가 몇 분~몇 시간 걸릴 수 있어 "돌아갈 곳" 의 유효성이 낮다.
        // 이번 구현에서는 단순 리다이렉트만.
        // -------------------------------------------------------------
        log.debug("이메일 인증 미완료 차단: accountId={}, path={}", accountId, request.getRequestURI());
        response.sendRedirect("/check-email-required");
        return false;

    }
}