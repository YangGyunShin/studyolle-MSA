package com.studyolle.modules.account.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.modules.account.security.UserAccount;
import com.studyolle.infra.config.AppProperties;
import com.studyolle.infra.mail.EmailMessage;
import com.studyolle.infra.mail.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.servlet.http.HttpSession;
import java.util.List;

/**
 * ✅ 인증(Authentication) 공통 로직을 담당하는 서비스
 *
 * 담당 기능:
 *   - 프로그래밍 방식 로그인 (login)
 *   - Spring Security의 UserDetailsService 구현 (loadUserByUsername)
 *   - 이메일 기반 로그인 링크 발송 (sendLoginLink)
 *
 * 설계 의도:
 *   - login()은 SignUpService, EmailLoginController, AccountSettingsService 등
 *     여러 곳에서 호출되는 공유 메서드이므로 인증 전용 서비스에 배치
 *   - UserDetailsService 구현도 인증의 핵심이므로 이 클래스가 담당
 *   - 다른 서비스에서 this.login()이 아닌 accountAuthService.login()으로 호출하여
 *     의존 방향이 명확해짐
 *
 * 호출 관계:
 *   - SignUpService → AccountAuthService.login() (가입 후 자동 로그인, 인증 완료 후 로그인)
 *   - AccountSettingsService → AccountAuthService.login() (닉네임 변경 후 인증 정보 갱신)
 *   - EmailLoginController → AccountAuthService.login(), sendLoginLink()
 *   - Spring Security → AccountAuthService.loadUserByUsername() (폼 로그인 시 자동 호출)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AccountAuthService implements UserDetailsService {

    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    /**
     * ✅ 프로그래밍 방식으로 강제 로그인을 수행하는 메서드
     *
     * Spring Security의 폼 로그인(POST /login)은 UsernamePasswordAuthenticationFilter가
     * 자동으로 처리하지만, 다음과 같은 상황에서는 로그인 폼을 거치지 않으므로
     * 개발자가 직접 SecurityContext에 인증 정보를 설정해야 한다:
     *   - 회원가입 직후 자동 로그인
     *   - 이메일 인증 완료 후 자동 로그인 (변경된 Account 상태 반영)
     *   - 이메일 로그인 링크 클릭 시 토큰 기반 로그인
     *   - 닉네임 변경 후 SecurityContext의 인증 정보 갱신
     *
     * 내부 동작:
     *   1단계 — SecurityContext에 인증 정보 저장 (현재 스레드에서만 유효)
     *     → 현재 요청에서 @CurrentUser 등이 인증 정보를 읽을 수 있게 함
     *   2단계 — HttpSession에 SecurityContext 저장
     *     → 다음 요청에서도 로그인 상태가 유지되도록 세션에 영속화
     *     → 다음 요청 시 SecurityContextPersistenceFilter가 세션에서 복원하여 스레드에 다시 넣어줌
     *
     * @param account 로그인 처리할 사용자 계정 객체
     */
    public void login(Account account) {
        // [1] 인증 토큰 생성
        //     - principal: UserAccount (UserDetails 구현체, Account를 내부에 포함)
        //     - credentials: 비밀번호 (실무에서는 null 처리 권장)
        //     - authorities: 권한 목록 (ROLE_USER)
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                new UserAccount(account),
                account.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // [2] SecurityContext에 인증 정보 설정 (현재 스레드에서만 유효)
        SecurityContextHolder.getContext().setAuthentication(token);

        // [3] HttpSession에 SecurityContext를 명시적으로 저장
        //     → 이후 브라우저 요청에서도 로그인 상태가 유지됨
        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest().getSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
        );
    }

    /**
     * ✅ Spring Security의 폼 로그인 시 자동으로 호출되는 메서드
     *
     * - 사용자가 로그인 폼에서 아이디/비밀번호를 입력하고 POST /login을 제출하면
     *   UsernamePasswordAuthenticationFilter가 이 메서드를 호출하여 사용자 정보를 조회
     *
     * 조회 전략:
     *   1. 이메일(email)로 먼저 조회
     *   2. 없으면 닉네임(nickname)으로 재조회
     *   3. 둘 다 없으면 UsernameNotFoundException 발생 → Spring Security가 로그인 실패 처리
     *
     * @param emailOrNickname 사용자가 로그인 폼에 입력한 식별자 (이메일 또는 닉네임)
     * @return UserDetails 구현체 (UserAccount)
     * @throws UsernameNotFoundException 해당 사용자가 존재하지 않을 경우
     */
    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String emailOrNickname) throws UsernameNotFoundException {
        Account account = accountRepository.findByEmail(emailOrNickname);

        if (account == null) {
            account = accountRepository.findByNickname(emailOrNickname);
        }

        if (account == null) {
            throw new UsernameNotFoundException(emailOrNickname);
        }

        return new UserAccount(account);
    }

    /**
     * ✅ 이메일 기반 로그인 링크를 생성하고 발송하는 메서드
     *
     * - 비밀번호 없이 이메일 링크만으로 로그인할 수 있는 대안 인증 방식
     * - Account에 저장된 emailCheckToken을 URL에 포함하여 이메일로 전송
     * - 사용자가 링크를 클릭하면 GET /login-by-email에서 토큰 검증 후 로그인 처리
     *
     * 처리 흐름:
     *   1. Thymeleaf Context에 링크 URL, 닉네임, 안내 메시지 등 변수 설정
     *   2. 이메일 템플릿(mail/simple-link.html)을 렌더링하여 HTML 본문 생성
     *   3. EmailService를 통해 실제 이메일 발송
     *
     * 보안 고려사항:
     *   - 토큰은 충분히 예측 불가능한 값이어야 함 (UUID 기반)
     *   - 만료 기한을 함께 기록하여 보안성 확보 필요
     *
     * @param account 로그인 링크를 받을 사용자 계정
     */
    public void sendLoginLink(Account account) {
        Context context = new Context();

        String token = account.getEmailCheckToken();
        String email = account.getEmail();
        String host = appProperties.getHost();
        String loginUrl = "/login-by-email?token=" + token + "&email=" + email;

        context.setVariable("link", loginUrl);
        context.setVariable("nickname", account.getNickname());
        context.setVariable("linkName", "스터디올래 로그인하기");
        context.setVariable("message", "로그인 하려면 아래 링크를 클릭하세요.");
        context.setVariable("host", host);

        String messageBody = templateEngine.process("mail/simple-link", context);

        EmailMessage emailMessage = EmailMessage.builder()
                .to(account.getEmail())
                .subject("스터디올래, 로그인 링크")
                .message(messageBody)
                .build();

        emailService.sendEmail(emailMessage);
    }
}