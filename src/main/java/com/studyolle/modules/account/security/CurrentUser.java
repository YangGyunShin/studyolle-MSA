package com.studyolle.modules.account.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ✅ 컨트롤러에서 현재 로그인한 사용자의 Account 객체를 바로 주입받기 위한 커스텀 어노테이션
 *
 * ──────────────────────────────────────────────────────────────────
 * [문제] 왜 이 어노테이션이 필요한가?
 * ──────────────────────────────────────────────────────────────────
 *
 * SecurityContext에 저장된 principal은 Account가 아니라 UserAccount(UserDetails 구현체)이다.
 *
 *   login() 메서드에서 저장한 것:
 *     new UsernamePasswordAuthenticationToken(
 *         new UserAccount(account),    ← principal = UserAccount
 *         account.getPassword(),
 *         authorities
 *     );
 *
 * 따라서 @AuthenticationPrincipal을 그대로 사용하면 UserAccount가 주입된다:
 *
 *   @AuthenticationPrincipal UserAccount userAccount  ← UserAccount 타입
 *   Account account = userAccount.getAccount();       ← 매번 꺼내야 함
 *
 * 컨트롤러마다 이 변환 코드를 반복하는 건 비효율적이므로,
 * expression을 사용하여 Account를 바로 꺼내주는 편의 어노테이션을 만든 것이다.
 *
 * ──────────────────────────────────────────────────────────────────
 * [동작 원리] expression = "#this == 'anonymousUser' ? null : account"
 * ──────────────────────────────────────────────────────────────────
 *
 * 이 expression은 SpEL(Spring Expression Language)로 작성되어 있다.
 * #this는 SecurityContext에서 꺼낸 principal 객체 자체를 가리킨다.
 *
 * [비로그인 사용자인 경우]
 *   - Spring Security는 인증되지 않은 사용자의 principal을 문자열 "anonymousUser"로 설정
 *   - #this == 'anonymousUser' → true → null 반환
 *   - 컨트롤러에서 Account account = null 로 주입됨
 *
 * [로그인 사용자인 경우]
 *   - principal은 UserAccount 객체
 *   - #this == 'anonymousUser' → false
 *   - "account" 평가 → #this.account → UserAccount.getAccount() 호출
 *   - Account 객체가 반환되어 컨트롤러 파라미터에 주입됨
 *
 *   여기서 "account"는 UserAccount 클래스의 필드명이다:
 *     public class UserAccount extends User {
 *         private final Account account;    ← 이 필드명!
 *         public Account getAccount() { }   ← expression이 이 getter를 호출
 *     }
 *
 * ──────────────────────────────────────────────────────────────────
 * [전체 흐름] 로그인부터 @CurrentUser 주입까지
 * ──────────────────────────────────────────────────────────────────
 *
 *   1. 로그인 시 (login 메서드):
 *      Account → new UserAccount(account) → SecurityContext에 저장 → 세션에 저장
 *
 *   2. 다음 요청 시 (컨트롤러 호출):
 *      세션에서 SecurityContext 복원
 *          ↓
 *      @CurrentUser 발동
 *          ↓
 *      @AuthenticationPrincipal이 SecurityContext에서 principal 추출 → UserAccount
 *          ↓
 *      expression 평가: UserAccount.getAccount() → Account
 *          ↓
 *      컨트롤러 파라미터에 Account 주입 완료
 *
 * ──────────────────────────────────────────────────────────────────
 * [사용 예시]
 * ──────────────────────────────────────────────────────────────────
 *
 *   // 로그인 필수 엔드포인트: account에 Account 객체가 주입됨
 *   @GetMapping("/settings/profile")
 *   public String updateProfile(@CurrentUser Account account, Model model) {
 *       // account를 바로 사용 가능 (UserAccount에서 꺼내는 과정이 생략됨)
 *   }
 *
 *   // 비로그인 접근 가능 엔드포인트: account가 null일 수 있음
 *   @GetMapping("/profile/{nickname}")
 *   public String viewProfile(@CurrentUser Account account, Model model) {
 *       // 비로그인 사용자면 account == null
 *   }
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? null : account")
public @interface CurrentUser {
}
/**
 * ✅ 컨트롤러에서 현재 로그인한 사용자의 Account 객체를 바로 주입받기 위한 커스텀 어노테이션
 *
 * ──────────────────────────────────────────────────────────────────
 * [배경] SecurityContext에서 사용자 정보를 꺼내는 3가지 방법
 * ──────────────────────────────────────────────────────────────────
 *
 * Spring Security에서 로그인하면 인증 정보가 SecurityContext에 저장된다.
 * 컨트롤러에서 이 정보를 꺼내는 방법은 3단계로 발전해왔다:
 *
 *   [1단계] SecurityContextHolder에서 직접 꺼내기 — 가장 원시적인 방법
 *
 *     @GetMapping("/settings/profile")
 *     public String updateProfile(Model model) {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         Object principal = auth.getPrincipal();
 *         UserAccount userAccount = (UserAccount) principal;
 *         Account account = userAccount.getAccount();
 *         // ... 이제야 account 사용 가능
 *     }
 *     → 문제: 매 컨트롤러마다 이 4줄을 반복해야 함, 캐스팅도 직접 해야 함
 *
 *   [2단계] @AuthenticationPrincipal 사용 — Spring Security 기본 제공 어노테이션
 *
 *     @AuthenticationPrincipal은 위의 4줄을 자동화해주는 어노테이션이다.
 *     역할: SecurityContext에서 principal 객체를 꺼내서 컨트롤러 파라미터에 자동 주입
 *
 *     @GetMapping("/settings/profile")
 *     public String updateProfile(@AuthenticationPrincipal UserAccount userAccount) {
 *         Account account = userAccount.getAccount();  // 여전히 한 단계 변환 필요
 *     }
 *     → 개선: 4줄 → 1줄로 줄었지만, UserAccount에서 Account를 꺼내는 작업은 여전히 수동
 *
 *     expression 옵션을 추가하면 꺼낸 principal에 추가 가공이 가능하다:
 *       @AuthenticationPrincipal(expression = "account") Account account
 *       → principal(UserAccount)에서 .getAccount()를 자동 호출하여 Account를 바로 주입
 *
 *   [3단계] @CurrentUser 사용 — 우리 프로젝트 전용 편의 어노테이션 (이 클래스)
 *
 *     @GetMapping("/settings/profile")
 *     public String updateProfile(@CurrentUser Account account) {
 *         // Account가 바로 주입됨! 변환 과정이 완전히 숨겨짐
 *     }
 *     → @AuthenticationPrincipal + expression + 비로그인 null 처리를 하나로 합침
 */