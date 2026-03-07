package com.studyolle.modules.account.service;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.dto.SignUpForm;
import com.studyolle.modules.account.repository.AccountRepository;
import com.studyolle.infra.config.AppProperties;
import com.studyolle.infra.mail.EmailMessage;
import com.studyolle.infra.mail.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * ✅ 회원가입 흐름 전체를 담당하는 서비스
 *
 * 담당 기능:
 *   - 새 계정 생성 및 저장 (processNewAccount → saveNewAccount)
 *   - 회원가입 인증 이메일 발송 (sendSignUpConfirmEmail)
 *   - 이메일 인증 완료 처리 + 자동 로그인 (completeSignUp)
 *
 * 설계 의도:
 *   - "가입 → 인증 이메일 → 인증 완료"라는 하나의 비즈니스 흐름을 응집
 *   - 인증 완료 후 자동 로그인은 AccountAuthService.login()에 위임
 *
 * 호출 관계:
 *   - SignUpController → processNewAccount(), completeSignUp(), sendSignUpConfirmEmail()
 *   - completeSignUp() → AccountAuthService.login() (변경된 계정 상태로 SecurityContext 갱신)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SignUpService {

    private final AccountRepository accountRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;
    private final AccountAuthService accountAuthService;

    /**
     * ✅ 회원가입의 핵심 비즈니스 로직
     *
     * 처리 흐름:
     *   1. saveNewAccount(): 비밀번호 암호화 + 인증 토큰 생성 + DB 저장
     *   2. sendSignUpConfirmEmail(): 인증 이메일 발송
     *
     * 트랜잭션 범위:
     *   - DB 저장과 이메일 발송이 하나의 트랜잭션으로 묶이지만,
     *     이메일 발송은 DB 롤백과 독립적이므로 예외 처리가 필요할 수 있음 (현재는 생략)
     *
     * @param signUpForm 회원가입 폼 데이터 (닉네임, 이메일, 비밀번호)
     * @return 생성된 Account 엔티티 (DB에 저장된 영속 상태)
     */
    public Account processNewAccount(SignUpForm signUpForm) {
        Account newAccount = saveNewAccount(signUpForm);
        sendSignUpConfirmEmail(newAccount);
        return newAccount;
    }

    /**
     * ✅ 회원가입 폼 데이터를 기반으로 Account 엔티티를 생성하고 DB에 저장
     *
     * 보안 규칙:
     *   - 비밀번호는 절대 평문으로 저장하면 안 됨
     *   - PasswordEncoder(BCrypt)를 통해 해시화 후 저장
     *
     * 처리 흐름:
     *   1. 평문 비밀번호를 BCrypt로 인코딩
     *   2. ModelMapper를 사용하여 SignUpForm → Account 필드 자동 매핑
     *   3. 이메일 인증용 고유 토큰(UUID) 생성
     *   4. JPA를 통해 DB에 영속화 (INSERT 쿼리 실행)
     *
     * @param signUpForm 사용자 입력 DTO
     * @return DB에 저장된 Account 엔티티
     */
    private Account saveNewAccount(@Valid SignUpForm signUpForm) {
        signUpForm.setPassword(passwordEncoder.encode(signUpForm.getPassword()));
        Account account = modelMapper.map(signUpForm, Account.class);
        account.generateEmailCheckToken();
        return accountRepository.save(account);
    }

    /**
     * ✅ 회원가입 인증 이메일 발송
     *
     * - Account에 저장된 emailCheckToken과 이메일을 URL에 포함하여 발송
     * - 사용자가 링크를 클릭하면 GET /check-email-token에서 토큰 검증 후 가입 완료 처리
     *
     * 이메일 템플릿:
     *   - mail/simple-link.html (Thymeleaf 기반 HTML 이메일)
     *   - 변수: link(인증 URL), nickname(닉네임), linkName(버튼 텍스트), message(안내 문구), host(도메인)
     *
     * @param newAccount 가입된 계정 (토큰이 생성되어 있어야 함)
     */
    public void sendSignUpConfirmEmail(Account newAccount) {
        Context context = new Context();
        context.setVariable("link", "/check-email-token?token=" + newAccount.getEmailCheckToken()
                + "&email=" + newAccount.getEmail());
        context.setVariable("nickname", newAccount.getNickname());
        context.setVariable("linkName", "이메일 인증하기");
        context.setVariable("message", "스터디올래 서비스를 이용하려면 링크를 클릭하세요.");
        context.setVariable("host", appProperties.getHost());

        String message = templateEngine.process("mail/simple-link", context);

        EmailMessage emailMessage = EmailMessage.builder()
                .to(newAccount.getEmail())
                .subject("스터디올래, 회원 인증")
                .message(message)
                .build();

        emailService.sendEmail(emailMessage);
    }

    /**
     * ✅ 이메일 인증 완료 처리 + 자동 로그인
     *
     * - 이메일 인증 링크 클릭 후 토큰 검증이 성공했을 때 호출됨
     *
     * 내부 동작:
     *   1. account.completeSignUp() → 도메인 상태 변경
     *      (emailVerified = true, joinedAt = LocalDateTime.now() 등)
     *   2. accountAuthService.login(account) → SecurityContext에 변경된 상태로 인증 정보 갱신
     *      - 로그인 시점에 저장된 Account는 emailVerified = false였으므로,
     *        변경된 상태를 반영하기 위해 다시 login()을 호출
     *
     * 트랜잭션:
     *   - 이 메서드는 @Transactional 범위 내에서 실행되므로
     *     account.completeSignUp()의 상태 변경이 Dirty Checking으로 DB에 자동 반영됨
     *
     * @param account 인증이 완료된 계정 객체
     */
    public void completeSignUp(Account account) {
        account.completeSignUp();
        accountAuthService.login(account);
    }
}