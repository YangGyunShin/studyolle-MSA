package com.studyolle.modules.main;

import com.studyolle.modules.account.entity.Account;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 애플리케이션 전역 예외 처리기 (Global Exception Handler)
 *
 * =====================================================
 * [이 클래스가 해결하는 문제]
 *
 * 컨트롤러에서 처리되지 않은 RuntimeException이 발생하면
 * 기본적으로 Spring Boot의 Whitelabel Error Page가 표시됨
 * -> 사용자에게 불친절하고, 개발자에게도 어떤 요청에서 에러가 발생했는지 추적이 어려움
 *
 * 이 클래스는 두 가지 역할을 담당:
 *   1. 사용자에게는 커스텀 에러 페이지("error" 뷰)를 보여줌
 *   2. 서버 로그에는 "누가, 어디서, 무엇이" 문제인지 기록함
 *
 * =====================================================
 * [@ControllerAdvice의 동작 원리]
 *
 * @ControllerAdvice는 모든 @Controller에 대해 공통으로 적용되는 AOP 기반 어드바이스임
 * -> 개별 컨트롤러에 try-catch를 넣지 않아도, 여기서 일괄 처리 가능
 *
 *   사용자 요청
 *       |
 *   DispatcherServlet
 *       |
 *   Controller 실행 -> RuntimeException 발생!
 *       |
 *   Spring이 @ControllerAdvice 클래스들을 탐색
 *       |
 *   이 클래스의 @ExceptionHandler 메서드가 매칭되어 실행
 *       |
 *   에러 로그 기록 + "error" 뷰 반환
 *
 * =====================================================
 * [@ControllerAdvice가 제공하는 3가지 기능]
 *
 *   1. @ExceptionHandler  : 예외 처리 메서드 정의 (이 클래스에서 사용)
 *   2. @InitBinder        : 컨트롤러 전역 데이터 바인딩/검증 설정
 *   3. @ModelAttribute    : 모든 컨트롤러에 공통 Model 데이터 추가
 *
 * 이 클래스는 1번 역할만 수행 -> 전역 예외 처리 전담
 *
 * =====================================================
 * [ExceptionAdvice vs NotificationInterceptor 비교]
 *
 *   NotificationInterceptor (HandlerInterceptor)
 *     -> "정상 흐름"에서 매 요청마다 model에 데이터를 추가하는 역할
 *     -> postHandle(): 컨트롤러 성공 후 실행
 *
 *   ExceptionAdvice (@ControllerAdvice)
 *     -> "예외 흐름"에서 컨트롤러가 던진 예외를 잡아 처리하는 역할
 *     -> @ExceptionHandler: 컨트롤러 실행 중 예외 발생 시 실행
 *
 * 즉, 정상 흐름은 Interceptor가, 예외 흐름은 ControllerAdvice가 담당하는 구조
 *
 * =====================================================
 * [패키지 위치: modules.main]
 *
 * 이 클래스는 특정 도메인(account, event, study 등)에 종속되지 않고
 * 애플리케이션 전체에 걸쳐 동작하므로 main 패키지에 위치
 * -> MainController, error.html 뷰와 함께 "메인/공통 영역"을 구성
 */
@Slf4j           // Lombok: log 객체를 자동 생성 (log.info(), log.error() 사용 가능)
@ControllerAdvice // 모든 @Controller에서 발생하는 예외를 이 클래스에서 일괄 처리
public class ExceptionAdvice {

    /**
     * RuntimeException 전역 처리 메서드
     *
     * =====================================================
     * [@ExceptionHandler 동작 방식]
     *
     * @ExceptionHandler에 별도 예외 타입을 지정하지 않으면
     * 메서드 파라미터의 예외 타입(RuntimeException)을 기준으로 매칭됨
     * -> RuntimeException 및 그 하위 타입(IllegalArgumentException 등) 모두 이 메서드로 들어옴
     *
     * =====================================================
     * [⚠️ 중요: @ExceptionHandler의 파라미터 주입 규칙]
     *
     * @ExceptionHandler 메서드는 일반 @GetMapping/@PostMapping 컨트롤러 메서드와
     * 파라미터 주입(해석) 규칙이 다르다. 이것은 Spring MVC의 처리 경로가 다르기 때문이다.
     *
     *   [일반 컨트롤러 메서드의 처리 경로]
     *     DispatcherServlet
     *       -> HandlerAdapter
     *         -> HandlerMethodArgumentResolver 체인 (등록된 리졸버 전체 사용)
     *           -> 컨트롤러 메서드 호출
     *
     *     -> @CurrentUser Account, @RequestParam, @PathVariable, Model 등
     *        모든 커스텀/기본 ArgumentResolver가 동작하여 다양한 타입 주입 가능
     *
     *   [@ExceptionHandler 메서드의 처리 경로]
     *     DispatcherServlet
     *       -> ExceptionHandlerExceptionResolver (별도의 예외 전용 처리기)
     *         -> 제한된 리졸버만 사용
     *           -> @ExceptionHandler 메서드 호출
     *
     *     -> 커스텀 HandlerMethodArgumentResolver가 적용되지 않음
     *     -> 따라서 @CurrentUser나 커스텀 도메인 객체(Account 등)를 파라미터로 받을 수 없음
     *
     *   [@ExceptionHandler에서 주입 가능한 파라미터 목록] (Spring 공식 문서 기준)
     *     - Exception (또는 하위 타입) : 발생한 예외 객체
     *     - HttpServletRequest        : 현재 HTTP 요청 객체
     *     - HttpServletResponse       : HTTP 응답 객체
     *     - WebRequest                : Spring의 요청 래퍼
     *     - Locale                    : 현재 로케일
     *     - Model                     : 에러 뷰에 데이터 전달 시
     *     - OutputStream / Writer     : 응답 스트림 직접 쓰기
     *
     *   [주입 불가능한 것들]
     *     - @CurrentUser Account      : 커스텀 ArgumentResolver 필요 -> 동작 안 함
     *     - @RequestParam, @PathVariable 등 : 원래 요청의 바인딩 정보 접근 불가
     *     - 커스텀 도메인 객체 일반       : HandlerMethodArgumentResolver 미적용
     *
     * =====================================================
     * [그러면 로그인 사용자 정보는 어떻게 얻는가?]
     *
     * @ExceptionHandler에서 현재 로그인 사용자를 알고 싶으면,
     * 파라미터 주입 대신 SecurityContextHolder에서 직접 꺼내야 한다.
     *
     *   SecurityContextHolder.getContext().getAuthentication()
     *     -> Authentication 객체 반환
     *     -> getPrincipal()로 현재 인증된 사용자 정보 획득
     *     -> 우리 프로젝트에서는 UserAccount 타입
     *     -> userAccount.getAccount()로 Account 엔티티 추출
     *
     * SecurityContextHolder는 ThreadLocal 기반이므로,
     * 같은 요청 스레드 내라면 어디서든 현재 인증 정보에 접근할 수 있다.
     * -> 파라미터 주입이 불가능한 상황에서의 대안으로 적합
     *
     * =====================================================
     * [로깅 전략]
     *
     * 두 단계로 나누어 로깅:
     *   1. log.info() -> "누가 어떤 URL을 요청했는지" 기록 (요청 추적용)
     *      -> 로그인 사용자: 닉네임 + URI
     *      -> 비로그인 사용자: URI만
     *
     *   2. log.error() -> "어떤 예외가 발생했는지" 기록 (장애 진단용)
     *      -> 예외 객체를 두 번째 인자로 전달하면 스택 트레이스가 자동 출력됨
     *      -> log.error("bad request", e)
     *         = "bad request" 메시지 + e.getMessage() + 전체 스택 트레이스
     *
     * =====================================================
     * [반환값: "error"]
     *
     * 문자열 "error"를 반환하면 Thymeleaf가 templates/error.html을 찾아 렌더링
     * -> 사용자에게 친절한 에러 페이지를 보여줌
     * -> Whitelabel Error Page 대신 프로젝트 커스텀 에러 화면이 표시됨
     *
     * 참고: Spring Boot는 templates/error/ 폴더 아래에
     *       404.html, 500.html 등 HTTP 상태 코드별 에러 페이지도 지원하지만,
     *       이 프로젝트에서는 단일 error.html로 통합 처리하고 있음
     *
     * @param req 현재 HTTP 요청 객체
     * @param e   발생한 RuntimeException
     * @return "error" -> templates/error.html 뷰를 렌더링
     */
    @ExceptionHandler
    public String handleRuntimeException(HttpServletRequest req, RuntimeException e) {

        // SecurityContextHolder에서 직접 인증 정보를 꺼냄
        // -> @ExceptionHandler에서는 커스텀 ArgumentResolver가 동작하지 않으므로
        //    Account를 파라미터로 주입받을 수 없음
        // -> 대신 ThreadLocal 기반의 SecurityContextHolder를 통해
        //    현재 요청 스레드의 인증 정보에 직접 접근
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof UserAccount userAccount) {
            // 로그인 상태: 어떤 사용자가 어떤 URL에서 에러를 발생시켰는지 기록
            // -> 운영 환경에서 특정 사용자의 문제를 추적할 때 유용
            //
            // instanceof 패턴 매칭 (Java 16+):
            //   authentication.getPrincipal() instanceof UserAccount userAccount
            //   = instanceof 검사와 캐스팅을 한 줄로 수행
            //   = 기존의 if (x instanceof Y) { Y y = (Y) x; ... } 패턴을 간결화
            Account account = userAccount.getAccount();
            log.info("'{}' requested '{}'", account.getNickname(), req.getRequestURI());
        } else {
            // 비로그인 상태 또는 인증 정보가 없는 경우: 요청 URL만 기록
            log.info("requested '{}'", req.getRequestURI());
        }

        // 예외 상세 정보(메시지 + 스택 트레이스) 기록
        // -> log.error(String, Throwable) 형태로 호출하면
        //    SLF4J가 자동으로 예외의 전체 스택 트레이스를 로그에 출력함
        log.error("bad request", e);

        return "error"; // templates/error.html 렌더링
    }
}