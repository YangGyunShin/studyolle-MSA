package com.studyolle.modules.account.security;

import com.studyolle.modules.account.entity.Account;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Spring Security의 UserDetails와 도메인 엔티티 Account를 연결하는 어댑터 클래스
 *
 * ──────────────────────────────────────────────────────────────────
 * [문제] 왜 이 클래스가 필요한가?
 * ──────────────────────────────────────────────────────────────────
 *
 * Spring Security와 우리 애플리케이션이 원하는 사용자 객체가 다르다:
 *
 *   - Spring Security: UserDetails 인터페이스 (username, password, authorities, 잠금 여부 등)
 *   - 우리 애플리케이션: Account 엔티티 (nickname, email, bio, profileImage, tags, zones 등)
 *
 * 이 둘을 연결하는 방법이 두 가지 있다:
 *
 *   [방법 1] Account가 직접 UserDetails를 구현
 *     @Entity
 *     public class Account implements UserDetails {
 *         // 도메인 필드 + Security 메서드가 한 클래스에 혼재
 *         public Collection<GrantedAuthority> getAuthorities() { ... }
 *         public boolean isAccountNonExpired() { ... }
 *         // ... Security가 강제하는 메서드 6개를 구현해야 함
 *     }
 *     → 문제: 도메인 엔티티가 Spring Security 프레임워크에 직접 의존하게 됨
 *     → Account는 순수한 비즈니스 객체여야 하는데 보안 로직이 섞여버림
 *     → Security 변경 시 엔티티까지 수정해야 함
 *
 *   [방법 2] 별도의 어댑터 클래스를 만들어 연결 ← 현재 방식 (UserAccount)
 *     → Account는 Spring Security를 전혀 모르는 순수한 도메인 엔티티로 유지
 *     → UserAccount만 Security에 의존하고, 내부에 Account를 감싸서 보관
 *     → Security 변경 시 UserAccount만 수정하면 됨
 *
 * ──────────────────────────────────────────────────────────────────
 * [구조] 어댑터 패턴 (Adapter Pattern)
 * ──────────────────────────────────────────────────────────────────
 *
 *   Spring Security 세계              우리 도메인 세계
 *   ───────────────────              ──────────────────
 *   UserDetails (인터페이스)          Account (엔티티)
 *        ↑                               ↑
 *        │                               │
 *        └──── UserAccount ──────────────┘
 *              (어댑터: 둘을 연결하는 다리)
 *
 *   - Spring Security는 UserAccount를 UserDetails로 봄 → 인증/권한 처리 가능
 *   - 우리 앱은 userAccount.getAccount()로 Account를 꺼냄 → 비즈니스 로직 처리 가능
 *   - Account 엔티티는 Spring Security의 존재를 전혀 모름 → 도메인 순수성 유지
 *
 * ──────────────────────────────────────────────────────────────────
 * [사용처] 어디서 생성되고 어디서 사용되는가?
 * ──────────────────────────────────────────────────────────────────
 *
 *   [생성되는 곳 1] loadUserByUsername() — 폼 로그인 시 Spring Security가 자동 호출
 *     public UserDetails loadUserByUsername(String emailOrNickname) {
 *         Account account = accountRepository.findByEmail(emailOrNickname);
 *         return new UserAccount(account);  ← 여기서 생성
 *     }
 *
 *   [생성되는 곳 2] login() — 프로그래밍 방식 로그인 (이메일 인증 완료 시 등)
 *     public void login(Account account) {
 *         new UsernamePasswordAuthenticationToken(
 *             new UserAccount(account),     ← 여기서 생성하여 SecurityContext에 저장
 *             ...
 *         );
 *     }
 *
 *   [사용되는 곳] @CurrentUser 어노테이션
 *     - SecurityContext에서 principal(UserAccount)을 꺼낸 후
 *     - expression "account"로 UserAccount.getAccount()를 호출하여
 *     - 최종적으로 Account 객체를 컨트롤러 파라미터에 주입
 *
 * ──────────────────────────────────────────────────────────────────
 * [주의] 이 객체는 세션에 직렬화되어 저장됨
 * ──────────────────────────────────────────────────────────────────
 *
 * login() 시점에 SecurityContext에 저장된 UserAccount(내부의 Account 포함)는
 * HttpSession에 직렬화되어 저장된다. 이후 브라우저 요청마다 세션에서 역직렬화되어 복원된다.
 *
 * 이것이 바로 @CurrentUser로 주입받는 Account가 Detached 상태인 이유이다:
 *   - 로그인 시점의 Account 스냅샷이 세션을 거쳐 돌아온 것
 *   - JPA 영속성 컨텍스트와 무관하므로 Dirty Checking이 작동하지 않음
 *   - Lazy Loading도 불가능 (getTags() 호출 시 LazyInitializationException)
 */
@Getter
public class UserAccount extends User {

    /**
     * 실제 사용자 정보를 담고 있는 도메인 객체
     *
     * - @CurrentUser의 expression "account"가 이 필드의 getter를 호출하여 Account를 꺼냄
     * - 필드명을 변경하면 @CurrentUser의 expression도 함께 변경해야 함
     *   (예: 필드명을 "member"로 바꾸면 expression도 "member"로 변경 필요)
     */
    private final Account account;

    /**
     * UserAccount 생성자 — Account 도메인 객체를 Spring Security의 User로 변환
     *
     * ──────────────────────────────────────────────────────────────────
     * [변경 이력] 3개 → 7개 파라미터 → enabled 고정 true
     * ──────────────────────────────────────────────────────────────────
     *
     * [v1] 3개 파라미터 생성자
     *   - enabled=true 고정 → 이메일 미인증이어도 로그인 + 모든 기능 사용 가능
     *
     * [v2] 7개 파라미터 생성자 + enabled = account.isEmailVerified()
     *   - 이메일 미인증 → enabled=false → DisabledException → 로그인 자체 차단
     *   - 문제: 사용자가 앱을 구경조차 못 함 → 이탈률 증가
     *
     * [v3 - 현재] 7개 파라미터 생성자 + enabled = true (고정)
     *   - 이메일 미인증이어도 로그인은 허용 (구경 가능)
     *   - 핵심 기능(스터디 생성/가입, 모임 참가)은 EmailVerificationInterceptor에서 제한
     *   - 사용자가 앱을 둘러보며 동기 부여 → 자연스러운 이메일 인증 유도
     *
     * ──────────────────────────────────────────────────────────────────
     * [7개 파라미터 각각의 역할]
     * ──────────────────────────────────────────────────────────────────
     *
     *   1. username              : 사용자 식별자 (Account의 nickname)
     *   2. password              : 암호화된 비밀번호
     *   3. enabled               : 계정 활성화 여부 (현재 항상 true)
     *      - 이메일 인증 제한은 인터셉터에서 처리하므로 여기서는 true 고정
     *      - 나중에 관리자가 계정을 비활성화하는 기능 도입 시 활용 가능
     *   4. accountNonExpired     : 계정 만료 여부 (true = 만료 안 됨)
     *   5. credentialsNonExpired : 비밀번호 만료 여부 (true = 만료 안 됨)
     *   6. accountNonLocked      : 계정 잠금 여부 (true = 잠금 안 됨)
     *   7. authorities           : 권한 목록
     *
     *   3~6은 현재 사용하지 않으므로 true로 고정한다.
     *   나중에 관리자가 계정을 잠그거나, 비밀번호 주기적 변경 정책 등을
     *   도입할 때 활용할 수 있다.
     *
     * ──────────────────────────────────────────────────────────────────
     * [이메일 인증 제한의 현재 구조]
     * ──────────────────────────────────────────────────────────────────
     *
     *   Spring Security (여기)       → 로그인 허용/차단만 담당 (enabled=true 고정)
     *   EmailVerificationInterceptor → 이메일 미인증 시 특정 기능 접근 제한
     *   WebConfig                    → 인터셉터가 적용되는 URL 패턴 관리
     *
     *   이렇게 분리한 이유:
     *   - Spring Security의 enabled는 로그인 시점에 고정됨
     *     → 이메일 인증 완료 후 재로그인해야 반영되는 문제
     *   - 인터셉터는 매 요청마다 account.isEmailVerified()를 체크
     *     → 인증 완료 즉시 기능 사용 가능 (재로그인 불필요)
     *
     * @param account 인증 대상 사용자의 Account 도메인 객체
     */
    public UserAccount(Account account) {
        super(
                account.getNickname(),      // username: 로그인 식별자
                account.getPassword(),      // password: 암호화된 비밀번호

                // enabled = true 고정 (이메일 미인증이어도 로그인은 허용)
                // 기능 제한은 EmailVerificationInterceptor에서 처리
                true,
                true,       // accountNonExpired (현재 미사용, 추후 확장 가능)
                true,                       // credentialsNonExpired (현재 미사용)
                true,                       // accountNonLocked (현재 미사용, 관리자 잠금 기능 시 활용)

                // 권한 목록: Account.role 기반으로 동적 부여
                //
                // [왜 재로그인이 필요한가?]
                // Spring Security는 로그인 시점에 DB에서 Account를 조회하여
                // 이 UserAccount 객체를 세션에 직렬화(캐싱)한다.
                // 이후 요청은 DB를 다시 조회하지 않고, 세션에 저장된 UserAccount를 그대로 사용한다.
                //
                // 따라서 DB에서 role을 변경해도 세션이 살아있는 동안에는 이전 권한이 유지된다.
                // 재로그인하면 loadUserByUsername()이 다시 호출되어 변경된 role이 반영된다.
                //
                // [왜 매 요청마다 DB를 조회하지 않는가?]
                // 성능 때문이다. 사용자가 버튼 하나 클릭할 때마다 권한 조회 쿼리가 발생하면
                // 트래픽이 조금만 늘어도 불필요한 DB 부하가 급격히 증가한다.
                // 권한이 바뀌는 일은 드물기 때문에 세션 캐싱이 합리적인 트레이드오프다.
                //
                // [보완 방법 — 필요 시 도입 검토]
                // 1. 세션 강제 만료: 권한 변경 시 SessionRegistry로 해당 사용자 세션을 즉시 무효화
                //      sessionRegistry.getAllSessions(account, false)
                //          .forEach(SessionInformation::expireNow);
                // 2. 주기적 재검증: 일정 시간(예: 5분)마다 DB에서 권한을 다시 읽도록 커스터마이징
                // 3. JWT + 짧은 만료 시간: 토큰 만료 시 자동으로 새 권한 반영 (아키텍처 변경 수반)
                //
                // StudyOlle에서는 관리자 권한 변경 빈도가 매우 낮으므로
                // 현재는 재로그인 방식으로 충분하다.
                List.of(new SimpleGrantedAuthority(account.getRole()))
        );
        this.account = account;
    }
}
