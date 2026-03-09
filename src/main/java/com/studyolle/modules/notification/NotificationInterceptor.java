package com.studyolle.modules.notification;

import com.studyolle.modules.account.entity.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 모든 페이지에 "읽지 않은 알림 여부(hasNotification)"를 자동으로 전달하는 인터셉터
 *
 * =====================================================
 * [이 클래스가 해결하는 문제]
 *
 * 이 프로젝트의 모든 페이지 상단 네비게이션 바에는 알림 종 아이콘이 있음
 * (fragments.html 참고)
 *
 *   th:if="${!hasNotification}" -> class="fa fa-bell-o" -> 빈 종
 *   th:if="${hasNotification}"  -> class="fa fa-bell"   -> 꽉찬 종
 *
 * 이 ${hasNotification} 값을 모든 페이지에서 model에 넣어줘야 함
 * 모든 컨트롤러에 일일이 넣으면 코드 중복이 심해짐
 * 그래서 인터셉터로 "컨트롤러 실행 후, View 렌더링 전"에 자동 삽입
 *
 * =====================================================
 * [HandlerInterceptor 인터페이스의 3가지 메서드]
 *
 *   사용자 요청
 *       |
 *   (1) preHandle()        <-- 컨트롤러 실행 전 (인증 체크, 로깅, 요청 차단 등)
 *       |
 *   Controller 실행
 *       |
 *   (2) postHandle()       <-- 컨트롤러 실행 후, View 렌더링 전 ** 여기 사용
 *       |                      (model에 데이터 추가, 뷰 이름 변경 등)
 *   View 렌더링 (HTML 생성)
 *       |
 *   (3) afterCompletion()  <-- 모든 처리 완료 후 (리소스 정리, 예외 로깅 등)
 *
 * 이 클래스는 (2) postHandle()만 사용
 * -> 컨트롤러가 model을 다 만든 후, View가 렌더링되기 전에
 *    hasNotification 값을 끼워넣는 것이 목적이기 때문
 *
 * =====================================================
 * [Interceptor - Controller - Fragment - Model 의 관계]
 *
 * 이 인터셉터는 fragments.html을 직접 호출하거나 알지 못한다.
 * 각자의 역할이 분리되어 있고, 최종적으로 Thymeleaf가 조립하는 구조이다.
 *
 *   (1) Controller가 비즈니스 로직을 실행하고 model에 데이터를 담음
 *       예: model.addAttribute("account", account);
 *
 *   (2) Interceptor의 postHandle()이 자동 실행됨
 *       -> Controller가 만든 그 model 객체에 hasNotification을 추가
 *       -> 이 시점에서 인터셉터는 어떤 template이 렌더링될지, fragment가 있는지 모름
 *       -> 그냥 model에 값을 넣을 뿐
 *
 *   (3) Thymeleaf가 View 렌더링을 시작
 *       -> 예: profile.html을 읽다가 아래 코드를 만남
 *          <nav th:replace="fragments.html :: main-nav"></nav>
 *       -> fragments.html에서 main-nav 조각을 가져와서 profile.html에 끼워넣기
 *       -> 이때 main-nav 안의 ${hasNotification}은 이미 model에 들어있으므로
 *          자연스럽게 값이 채워짐
 *
 * 즉 "fragment에 model을 넘긴다"가 아니라,
 * "하나의 페이지로 합쳐진 다음에 하나의 model로 렌더링된다"는 것이 핵심.
 *
 * 역할 정리:
 *   Controller  -> 자기 비즈니스 로직에 필요한 model 데이터만 담당
 *   Interceptor -> 모든 페이지에 공통으로 필요한 model 데이터 담당 (hasNotification)
 *   Thymeleaf   -> 렌더링 시점에 template + fragment를 조립하고 model에서 값을 꺼내 HTML 완성
 *
 * =====================================================
 * [주의] 이 클래스는 만들기만 해서는 동작하지 않음!
 * WebConfig.java에서 registry.addInterceptor()로 등록해야
 * Spring MVC가 매 요청마다 이 인터셉터를 실행함
 */
@Component  // 스프링 빈으로 등록 -> WebConfig에서 주입받아 사용
@RequiredArgsConstructor
public class NotificationInterceptor implements HandlerInterceptor {
    // HandlerInterceptor: Spring MVC가 제공하는 인터셉터 인터페이스
    // -> preHandle, postHandle, afterCompletion 중 필요한 것만 오버라이드하면 됨
    // -> 나머지는 기본 구현(아무것도 안 함)이 적용됨

    // 알림 데이터 접근용 Repository - DB에서 읽지 않은 알림 개수를 조회하는 데 사용
    //
    // [왜 NotificationService가 아닌 Repository를 직접 사용하는가?]
    //
    // 인터셉터는 매 요청마다 실행되는 인프라 계층 컴포넌트임.
    // 여기서 수행하는 작업은 단순한 COUNT 쿼리 하나뿐이고,
    // 비즈니스 로직이 전혀 개입하지 않음.
    //
    // Service를 거치면:
    //   (1) 단순 COUNT 조회에 불필요한 추상화 계층이 추가됨
    //   (2) Service의 @Transactional 오버헤드가 매 요청마다 발생
    //
    // 따라서 이 경우는 Repository 직접 접근이 더 적절함.
    // (Controller의 Repository 직접 접근과는 다른 맥락 -
    //  Controller는 비즈니스 흐름을 다루므로 Service를 거쳐야 하지만,
    //  인터셉터는 인프라 관심사이므로 단순 데이터 조회에 Repository 직접 접근이 합리적)
    private final NotificationRepository notificationRepository;

    /**
     * 컨트롤러 실행 후, View 렌더링 전에 호출되는 메서드
     *
     * @param request       현재 HTTP 요청 객체 (URL, 헤더, 파라미터 등)
     * @param response      현재 HTTP 응답 객체
     * @param handler       실행된 컨트롤러 메서드 정보
     * @param modelAndView  컨트롤러가 만든 Model + View 이름
     *                      (여기에 hasNotification을 추가하는 것이 이 메서드의 목적)
     */
    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {

        // 현재 로그인 사용자의 인증 정보를 SecurityContext에서 가져옴
        // -> 로그인하지 않은 상태면 authentication이 null이거나 principal이 AnonymousUser임
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // [4가지 조건을 모두 만족할 때만 알림 확인을 수행]
        //
        // 조건 1: modelAndView != null
        //   -> @ResponseBody(REST API)처럼 View가 없는 경우 modelAndView가 null
        //   -> 추가할 model 자체가 없으므로 스킵
        //
        // 조건 2: !isRedirectView(modelAndView)
        //   -> redirect 응답이면 model 데이터가 View에 전달되지 않음
        //   -> 예: return "redirect:/home" -> 브라우저가 /home으로 재요청
        //   -> 재요청 시 새로운 인터셉터 사이클이 돌기 때문에 redirect에서는 스킵해도 됨
        //
        // 조건 3: authentication != null
        //   -> SecurityContext에 인증 정보가 있어야 함 (로그인된 상태)
        //
        // 조건 4: authentication.getPrincipal() instanceof UserAccount
        //   -> 실제 로그인한 사용자인지 확인
        //   -> 비로그인 상태에서는 principal이 "anonymousUser"라는 문자열임
        //   -> UserAccount 타입이어야 우리 프로젝트의 로그인 사용자임
        //
        if (modelAndView != null
                && !isRedirectView(modelAndView)
                && authentication != null
                && authentication.getPrincipal() instanceof UserAccount) {

            // UserAccount에서 실제 도메인 객체(Account)를 꺼냄
            // -> UserAccount는 Spring Security의 UserDetails 구현체
            // -> 내부에 Account 엔티티를 들고 있음 (getAccount()로 접근)
            Account account = ((UserAccount) authentication.getPrincipal()).getAccount();

            // DB에서 이 사용자의 "읽지 않은 알림" 개수를 조회
            // -> checked=false인 알림의 개수를 COUNT 쿼리로 조회
            long count = notificationRepository.countByAccountAndChecked(account, false);

            // model에 hasNotification 속성을 추가
            // -> count > 0이면 true (읽지 않은 알림 있음) -> 꽉찬 종
            // -> count == 0이면 false (알림 없음) -> 빈 종
            //
            // 이 값은 fragments.html의 네비게이션 바에서 이렇게 사용됨:
            //   th:if="${!hasNotification}" -> class="fa fa-bell-o" (빈 종)
            //   th:if="${hasNotification}"  -> class="fa fa-bell"   (꽉찬 종)
            //
            // 이 인터셉터가 fragments.html을 직접 호출하는 것이 아님!
            // -> 여기서는 그냥 model에 값을 넣을 뿐
            // -> 나중에 Thymeleaf가 template을 렌더링할 때
            //    th:replace로 fragment를 끼워넣고, 같은 model에서 값을 읽어감
            modelAndView.addObject("hasNotification", count > 0);
        }
    }

    /**
     * 현재 응답이 redirect인지 판별하는 헬퍼 메서드
     *
     * redirect인 경우 model에 데이터를 추가해도 의미 없음:
     * -> redirect는 브라우저에게 "다른 URL로 다시 요청해"라고 지시하는 것
     * -> 현재 model은 버려지고, 새 요청에서 새 model이 만들어짐
     *
     * 두 가지 redirect 형태를 모두 체크:
     * 1. 문자열 방식: return "redirect:/home"  -> viewName이 "redirect:"로 시작
     * 2. 객체 방식:   return new RedirectView("/home") -> view가 RedirectView 인스턴스
     */
    private boolean isRedirectView(ModelAndView modelAndView) {
        return modelAndView.getViewName().startsWith("redirect:")
                || modelAndView.getView() instanceof RedirectView;
    }
}