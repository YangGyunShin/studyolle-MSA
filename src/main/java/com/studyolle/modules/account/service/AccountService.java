//package com.studyolle.modules.account.service;
//
//import com.studyolle.infra.config.AppProperties;
//import com.studyolle.infra.mail.EmailMessage;
//import com.studyolle.infra.mail.EmailService;
//import com.studyolle.modules.account.dto.Notifications;
//import com.studyolle.modules.account.dto.Profile;
//import com.studyolle.modules.account.entity.Account;
//import com.studyolle.modules.account.repository.AccountRepository;
//import com.studyolle.modules.account.security.UserAccount;
//import com.studyolle.modules.account.dto.SignUpForm;
//import com.studyolle.modules.tag.Tag;
//import com.studyolle.modules.zone.Zone;
//import jakarta.servlet.http.HttpSession;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.modelmapper.ModelMapper;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.context.request.RequestContextHolder;
//import org.springframework.web.context.request.ServletRequestAttributes;
//import org.thymeleaf.TemplateEngine;
//import org.thymeleaf.context.Context;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.Set;
//
//@Slf4j
//@Transactional
//@Service
//@RequiredArgsConstructor
//public class AccountService implements UserDetailsService {
//
//    private final AccountRepository accountRepository;
//    private final EmailService emailService;
//    private final PasswordEncoder passwordEncoder;
//    private final ModelMapper modelMapper;
//    private final TemplateEngine templateEngine;
//    private final AppProperties appProperties;
//
//    /**
//     * [1] 회원 가입 절차의 핵심 비즈니스 로직을 처리하는 메서드
//     *
//     * - 컨트롤러(SignUpController)에서 호출됨
//     * - 가입 폼(SignUpForm)의 데이터를 바탕으로 새로운 Account 객체를 생성하고 DB에 저장한 뒤,
//     *   이메일 인증을 위한 확인 메일을 전송하는 전체 절차를 담당
//     *
//     * - 내부적으로 두 가지 메서드를 호출함:
//     *   1) saveNewAccount(): 사용자 정보를 저장
//     *   2) sendSignUpConfirmEmail(): 인증용 메일 발송
//     *
//     * - 트랜잭션 어노테이션(@Transactional)에 의해 DB 저장과 메일 전송은 하나의 논리적 작업처럼 묶이지만,
//     *   실제로 메일 전송은 DB 롤백과는 독립적이기 때문에 예외 처리가 필요할 수 있음 (현재는 생략)
//     */
//    public Account processNewAccount(SignUpForm signUpForm) {
//        Account newAccount = saveNewAccount(signUpForm);     // [1-1] 계정 저장 (비밀번호 암호화 포함)
//        sendSignUpConfirmEmail(newAccount);                  // [1-2] 이메일 인증 링크 전송
//        return newAccount;
//    }
//
//    /**
//     * [2] 사용자의 회원 가입 폼 데이터를 바탕으로 실제 Account 엔티티를 생성하고 DB에 저장하는 메서드
//     *
//     * - 여기서의 SignUpForm은 단순 DTO(데이터 전달 객체)로, Entity와는 분리된 형태
//     * - 저장 과정에서 반드시 지켜야 할 보안 규칙 존재 → 비밀번호는 절대로 평문으로 저장하면 안 됨
//     *   → 반드시 PasswordEncoder를 통해 해시함수를 적용해야 한다 (BCrypt 등)
//     *
//     * - modelMapper를 활용하여 SignUpForm → Account로 데이터 필드 매핑을 수행함
//     *   → 이메일, 닉네임, 암호화된 비밀번호 등 필드 자동 복사
//     *
//     * - 이메일 인증 기능을 위한 토큰을 생성
//     *   → 이 토큰은 가입자의 이메일 주소로 전송되어 인증 확인에 사용됨
//     */
//    private Account saveNewAccount(@Valid SignUpForm signUpForm) {
//        // [2-1] 사용자로부터 입력받은 평문 비밀번호를 해시화하여 보안 저장
//        signUpForm.setPassword(passwordEncoder.encode(signUpForm.getPassword()));
//
//        // [2-2] modelMapper를 이용하여 DTO(SignUpForm) → Entity(Account) 자동 변환
//        Account account = modelMapper.map(signUpForm, Account.class);
//
//        // [2-3] 이메일 인증에 사용할 고유한 토큰 생성
//        // 보통 UUID 기반의 토큰으로, 이메일 클릭 시 이 토큰을 서버로 전달하여 인증 완료 처리
//        account.generateEmailCheckToken();
//
//        // [2-4] JPA를 통해 DB에 영속화
//        // 내부적으로는 insert into account(...) 쿼리 실행됨
//        return accountRepository.save(account);
//    }
//
//    /**
//     * [3] 회원 가입 후 이메일 인증을 위한 확인 메일을 발송하는 메서드
//     *
//     * - 이메일 인증은 스터디올래 서비스가 사용자 본인임을 검증하는 핵심 절차
//     * - 인증을 위해 생성한 토큰과 이메일 주소를 링크에 포함시켜 사용자에게 전달함
//     * - 이메일 템플릿(simple-link.html)을 기반으로 Thymeleaf 템플릿 엔진을 사용하여 HTML 메시지 생성
//     *
//     * - 실제 메일 전송은 EmailService를 통해 위임되며,
//     *   이는 외부 SMTP 서버 설정에 따라 메일을 발송함 (예: Gmail SMTP, Amazon SES 등)
//     */
//    public void sendSignUpConfirmEmail(Account newAccount) {
//        // [3-1] Thymeleaf에서 사용할 변수 설정 (HTML 템플릿에 바인딩됨)
//        Context context = new Context();
//        context.setVariable("link", "/check-email-token?token=" + newAccount.getEmailCheckToken() + "&email=" + newAccount.getEmail());
//        context.setVariable("nickname", newAccount.getNickname());
//        context.setVariable("linkName", "이메일 인증하기");           // 버튼 링크명
//        context.setVariable("message", "스터디올래 서비스를 이용하려면 링크를 클릭하세요.");
//        context.setVariable("host", appProperties.getHost());       // ex) https://studyolle.com
//
//        // [3-2] 'mail/simple-link.html' 템플릿을 바탕으로 실제 이메일 메시지 생성
//        // 해당 템플릿은 thymeleaf 구문을 이용한 HTML 구조이며,
//        // 링크 클릭 시 인증 처리 URL로 리다이렉트됨
//        String message = templateEngine.process("mail/simple-link", context);
//
//        // [3-3] 이메일 메시지 객체 생성
//        // builder 패턴을 이용하여 수신자, 제목, 본문 메시지를 설정
//        EmailMessage emailMessage = EmailMessage.builder()
//                .to(newAccount.getEmail())
//                .subject("스터디올래, 회원 인증")
//                .message(message)
//                .build();
//
//        // [3-4] EmailService를 통해 실제 메일 발송 처리
//        // 내부적으로 JavaMailSender 등의 구현체를 사용할 수 있음
//        emailService.sendEmail(emailMessage);
//    }
//
//
//    public void login(Account account) {
//        // ✅ 인증 토큰 생성 (사용자 정보, 비밀번호, 권한 목록)
//        // 여기서는 단순히 닉네임(String)을 principal로 설정했지만,
//        // UserDetails를 구현한 객체를 사용하는 것이 권장됨
//        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
//                new UserAccount(account), // principal
//                account.getPassword(), // credentials: 비밀번호 (보통은 null 처리함)
//                List.of(new SimpleGrantedAuthority("ROLE_USER")) // authorities: 권한 목록
//        );
//
//        // ✅ SecurityContext에 인증 정보 설정
//        // 이 설정은 현재 스레드에서만 유효함 (스레드 로컬 저장)
//        SecurityContextHolder.getContext().setAuthentication(token);
//
//        // ✅ 현재 HTTP 요청의 세션을 가져오기 위해 RequestContextHolder 사용
//        // Spring MVC에서 현재 요청 정보를 꺼낼 수 있는 표준 방법
//        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getSession();
//
//        // ✅ SecurityContext를 세션에 명시적으로 저장
//        // 이 작업이 있어야 이후 브라우저 요청에서 로그인 상태가 유지됨
//        session.setAttribute(
//                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, // "SPRING_SECURITY_CONTEXT"
//                SecurityContextHolder.getContext()
//        );
//    }
//
//    /**
//     * Spring Security 에서 사용자 인증을 처리하기 위한 메서드입니다.
//     *
//     * - 로그인 시도 시 호출되며, 사용자의 식별자(email 또는 nickname)를 기반으로 사용자 정보를 조회합니다.
//     * - 사용자 정보를 Spring Security 에서 사용할 수 있는 UserDetails 형태로 반환합니다.
//     * - 이 UserDetails 객체는 이후 SecurityContext에 저장되어 인가 처리 등에 사용됩니다.
//     *
//     * 이 메서드는 다음과 같은 흐름을 가집니다:
//     *  1. 이메일로 먼저 사용자를 조회하고,
//     *  2. 없으면 닉네임으로 다시 한 번 조회합니다.
//     *  3. 둘 다 없으면 UsernameNotFoundException 예외를 발생시킵니다.
//     *  4. 찾은 계정으로 UserDetails(UserAccount)를 생성해 반환합니다.
//     *
//     * @param emailOrNickname 사용자가 로그인 시 입력한 이메일 또는 닉네임
//     * @return 인증 대상 사용자의 UserDetails 객체
//     * @throws UsernameNotFoundException 해당 사용자가 존재하지 않을 경우 예외 발생
//     */
//    @Transactional(readOnly = true) // 읽기 전용 트랜잭션으로 조회 성능 최적화
//    @Override
//    public UserDetails loadUserByUsername(String emailOrNickname) throws UsernameNotFoundException {
//        // 1. 먼저 이메일(email) 기준으로 사용자 조회
//        Account account = accountRepository.findByEmail(emailOrNickname);
//
//        // 2. 이메일로 찾지 못한 경우, 닉네임(nickname) 기준으로 한 번 더 조회
//        if (account == null) {
//            account = accountRepository.findByNickname(emailOrNickname);
//        }
//
//        // 3. 이메일/닉네임 모두 일치하는 계정이 없다면 인증 실패 처리
//        //    - Spring Security는 이 예외를 Catch하여 로그인 실패로 처리함
//        if (account == null) {
//            throw new UsernameNotFoundException(emailOrNickname);
//        }
//
//        // 4. 조회한 Account 객체를 기반으로 UserDetails 구현체 반환
//        //    - UserAccount는 커스텀 UserDetails로, 내부에 Account를 그대로 포함하고 있음
//        return new UserAccount(account);
//    }
//
//    /**
//     * [📌 설명] 회원가입 이메일 인증을 완료 처리하는 서비스 메서드
//     *
//     * - 이 메서드는 컨트롤러에서 이메일 인증이 성공적으로 이루어졌을 때 호출된다.
//     * - 내부적으로는 사용자 상태를 '이메일 인증 완료'로 바꾸고,
//     *   이후 사용자를 자동 로그인 처리한다.
//     *
//     * ✅ 역할 분리:
//     * - 도메인 상태 변경 (completeSignUp())은 Account 객체에게 위임
//     * - 인증 흐름 제어 (login())은 서비스 계층의 책임
//     *
//     * ✅ 트랜잭션 처리:
//     * - 이 메서드는 트랜잭션 범위 내에서 실행되어야 하므로
//     *   AccountService 클래스는 @Transactional로 선언되어 있음
//     *
//     * ✅ 호출 예시 (Controller에서):
//     *   if (account.isValidToken(token)) {
//     *       accountService.completeSignUp(account);
//     *   }
//     */
//    public void completeSignUp(Account account) {
//        account.completeSignUp();  // [1] 도메인 상태 변경 (이메일 인증 완료)
//        login(account);            // [2] 자동 로그인 (SecurityContext에 인증 정보 설정)
//    }
//
//    public void updateProfile(Account account, @Valid Profile profile) {
//        // Account는 Detached 상태이므로 JPA의 변경 감지(dirty checking)가 자동으로 작동하지 않음
//        // 직접 필드 수정을 하고 accountRepository.save(account) 호출이 필요함
//
//        // Profile DTO의 값들을 Account 엔티티에 덮어쓰기
//        // - modelMapper는 필드명이 같은 값을 자동으로 매핑함
//
////        account.setUrl(profile.getUrl());
////        account.setBio(profile.getBio());
////        account.setLocation(profile.getLocation());
////        account.setOccupation(profile.getOccupation());
////        account.setProfileImage(profile.getProfileImage());
////        // 기존 account는 principal 객체이기 때문에, accountRepository.save(account)를 해야 db에 반영됨
//
//        modelMapper.map(profile, account);
//
//        // Detached 상태의 Account를 다시 영속 상태로 전환하여 DB에 반영
//        // - save(account)는 병합(merge) 기능 수행
//        // - 결과적으로 update 쿼리 발생
//        accountRepository.save(account);
//    }
//
//    /**
//     * 실제 비밀번호 변경을 처리하는 서비스 메서드
//     * <p>
//     * - account는 Detached 상태이므로 setPassword()만 해서는 DB에 반영되지 않음
//     * - 반드시 accountRepository.save(account)를 호출해야 merge가 발생하고 update 쿼리가 생성됨
//     * - 비밀번호는 평문이 아닌 암호화된 상태로 저장되어야 하므로 passwordEncoder.encode()를 사용
//     */
//    public void updatePassword(Account account, String newPassword) {
//        account.setPassword(passwordEncoder.encode(newPassword));
//
//        // Detached 상태 객체를 병합하여 영속 상태로 만들고 DB에 변경사항 반영
//        accountRepository.save(account);
//    }
//
//
//    /**
//     * 알림 설정을 Account 도메인 객체에 반영하고 DB에 저장하는 서비스 메서드
//     * <p>
//     * - account는 Detached 상태 객체 (AuthenticationPrincipal에서 추출되었기 때문)
//     * Detached 상태의 특징:
//     * 1) 영속성 컨텍스트(Persistence Context)에 연결되어 있지 않음
//     * 2) 필드 변경만으로는 DB에 반영되지 않음 (Dirty Checking이 작동하지 않음)
//     * <p>
//     * - JPA에서 Detached 객체를 DB에 반영하려면 save(account) 호출이 필요
//     * → 내부적으로 merge가 일어나서 새로운 영속 객체가 반환되고 이를 DB에 반영함
//     * <p>
//     * - modelMapper를 이용하여 Notifications → Account 매핑
//     * → 각 알림 관련 필드를 개별적으로 setter로 지정하는 대신 자동 매핑으로 간결하게 처리
//     */
//    public void updateNotifications(Account account, @Valid Notifications notifications) {
//
////        account.setStudyCreatedByWeb(notifications.isStudyCreatedByWeb());
////        account.setStudyCreatedByEmail(notifications.isStudyCreatedByEmail());
////        account.setStudyUpdatedByWeb(notifications.isStudyUpdatedByWeb());
////        account.setStudyUpdatedByEmail(notifications.isStudyUpdatedByEmail());
////        account.setStudyEnrollmentResultByWeb(notifications.isStudyEnrollmentResultByWeb());
////        account.setStudyEnrollmentResultByEmail(notifications.isStudyEnrollmentResultByEmail());
//        // modelMapper가 notifications DTO의 필드들을 account 객체의 대응 필드에 매핑
//        modelMapper.map(notifications, account);
//
//        // Detached 상태의 account를 merge하여 DB에 반영
//        accountRepository.save(account);
//    }
//
//
//    /**
//     * 닉네임 변경 비즈니스 로직
//     *
//     * - Detached 상태의 Account 객체를 받아 nickname 필드를 변경
//     * - 단순히 setNickname으로는 DB 반영이 되지 않음 (영속 상태가 아니므로 Dirty Checking 미작동)
//     * - save(account)를 호출하여 merge 발생 → DB에 업데이트 쿼리 전송
//     *
//     * - login(account)은 변경된 계정 정보를 SecurityContext에 다시 반영하기 위한 작업
//     * → Spring Security에서 로그인 시 사용자 정보를 세션에 저장하기 때문
//     */
//    public void updateNickname(Account account, String nickname) {
//        account.setNickname(nickname);               // 닉네임 필드만 수정
//        accountRepository.save(account);             // merge를 유발하여 DB 반영
//        login(account);                              // 현재 세션의 인증 객체를 업데이트
//    }
//
//
//    /**
//     * 이메일 로그인 링크를 생성하고, 사용자의 이메일로 전송합니다.
//     *
//     * 이 메서드는 다음의 과정을 수행합니다:
//     *  1. 템플릿 렌더링을 위한 Context 변수 설정
//     *  2. 로그인 URL 구성
//     *  3. 이메일 템플릿을 렌더링하여 본문 생성
//     *  4. 이메일 전송 객체 생성 및 실제 전송
//     *
//     * 주의: 이 메서드를 호출하기 전에 account 객체에 대해 토큰이 생성되어 있어야 합니다.
//     *
//     * @param account 로그인 링크를 받을 사용자 계정
//     */
//    public void sendLoginLink(Account account) {
//        // 1. 이메일 템플릿에 바인딩할 변수를 담을 Thymeleaf Context 생성
//        Context context = new Context();
//
//        // 2. 사용자 계정에서 이메일 확인 토큰과 이메일 주소를 추출
//        String token = account.getEmailCheckToken(); // 사전에 생성되어야 함
//        String email = account.getEmail();
//
//        // 3. 애플리케이션의 외부 노출 도메인(host)을 구성
//        String host = appProperties.getHost(); // 예: https://studyolle.com
//
//        // 4. 최종 로그인 URL 구성
//        //    - 이 URL은 사용자의 이메일에 포함되어, 클릭 시 GET /login-by-email 로 접속됨
//        //    - 보안 상 이 링크는 서버에 저장된 토큰과 일치해야 하며, 만료 기한도 함께 고려해야 함
//        String loginUrl = "/login-by-email?token=" + token + "&email=" + email;
//
//        // 5. 이메일 템플릿에 필요한 변수들을 Thymeleaf Context에 등록
//        context.setVariable("link", loginUrl);                        // 로그인 링크 (클릭 시 사용)
//        context.setVariable("nickname", account.getNickname());       // 수신자에게 보여줄 닉네임
//        context.setVariable("linkName", "스터디올래 로그인하기");       // 버튼이나 앵커 텍스트에 사용할 문구
//        context.setVariable("message", "로그인 하려면 아래 링크를 클릭하세요."); // 안내 메시지
//        context.setVariable("host", host);                            // 호스트 정보 (이미지 경로 등에도 활용 가능)
//
//        // 6. Thymeleaf 템플릿 엔진을 이용해 실제 HTML 이메일 본문 생성
//        //    - "mail/simple-link" 템플릿 파일을 로드하고 위 context 변수를 삽입하여 렌더링
//        //    - 결과는 HTML 또는 텍스트 형식으로 생성됨
//        String messageBody = templateEngine.process("mail/simple-link", context);
//
//        // 7. 이메일 전송 객체(EmailMessage)를 생성
//        //    - builder 패턴 사용: 수신자 이메일, 제목, 본문을 설정
//        EmailMessage emailMessage = EmailMessage.builder()
//                .to(account.getEmail())                          // 수신자 이메일
//                .subject("스터디올래, 로그인 링크")                   // 이메일 제목
//                .message(messageBody)                           // 본문 (렌더링된 템플릿)
//                .build();
//
//        // 8. EmailService에 위임하여 실제 이메일 전송 수행
//        //    - 내부적으로 SMTP 서버 혹은 이메일 API (예: SES, SendGrid 등)를 사용 가능
//        emailService.sendEmail(emailMessage);
//
//        // (보완 고려사항)
//        // - 토큰은 충분히 예측 불가능한 값이어야 하며
//        // - 만료 기한을 함께 기록해야 보안성을 확보할 수 있음
//        // - 토큰 발급 및 저장 로직은 호출자 혹은 Account 객체에서 책임져야 함
//    }
//
//
//    public Set<Tag> getTags(Account account) {
//        // [1] account 객체는 인증 객체에서 가져온 detached 상태일 수 있다.
//        //     이 상태에서는 연관 엔티티(tags)에 대한 lazy loading이 불가능하다.
//        //     즉, getTags() 호출 시 연관 데이터를 불러오지 못하거나 예외(LazyInitializationException)가 발생할 수 있다.
//        //
//        //     따라서 반드시 영속 상태로 다시 조회하여 연관 데이터도 함께 접근할 수 있게 해야 한다.
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        // [2] 조회된 Account는 JPA의 영속 상태이며,
//        //     getTags() 호출 시 JPA는 연관관계 매핑에 따라 필요한 경우에만 select 쿼리를 실행하여 데이터를 로딩한다.
//        //
//        //     만약 Account → Tag가 @ManyToMany(fetch = FetchType.LAZY)로 설정되어 있다면,
//        //     getTags() 호출 시 다음과 같은 쿼리가 발생한다:
//        //
//        //     SELECT t.* FROM tag t
//        //     JOIN account_tags at ON t.id = at.tag_id
//        //     WHERE at.account_id = ?;
//        //
//        //     이는 성능을 최적화하기 위한 lazy loading 전략이다.
//
//        // [3] 반면 fetch = FetchType.EAGER로 설정된 경우,
//        //     account를 조회할 때 모든 tag 정보도 함께 로딩되며, 다수의 account를 조회할 경우 N+1 문제로 이어질 수 있다.
//        //     이 경우에는 @EntityGraph("Account.withTags") 또는 JPQL fetch join을 고려할 수 있다.
//
//        // [4] 연관 컬렉션은 Set<Tag>로 구성되어 있으며,
//        //     중복된 태그를 방지하고 equals/hashCode 기준으로 논리적 동등성을 유지한다.
//
//        return byId.orElseThrow().getTags();
//    }
//
//    public void addTag(Account account, Tag tag) {
//        // [1] 전달된 account 객체는 인증된 사용자 정보를 기반으로 컨트롤러에서 주입된 principal 객체이다.
//        //     하지만 이 객체는 JPA 영속성 컨텍스트(persistence context)에서 관리되지 않는 detached 상태일 수 있다.
//        //     detached 상태에서는 객체의 상태 변경이나 연관관계 변경이 DB에 반영되지 않기 때문에,
//        //     반드시 repository를 통해 ID로 다시 조회하여 영속 상태로 만들어야 한다.
//        //     (이는 내부적으로 EntityManager.find(...)와 유사한 방식으로 동작한다.)
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        byId.ifPresent(a -> {
//            // [2] a는 영속 상태의 Account 객체이며, JPA는 이 객체의 상태 변화를 추적할 수 있다.
//            //     a.getTags().add(tag)는 단순한 컬렉션 조작처럼 보이지만,
//            //     JPA는 연관관계 매핑(@ManyToMany 등)을 통해 이 조작을 감지한다.
//            //
//            //     예: @ManyToMany
//            //         @JoinTable(name = "account_tags",
//            //             joinColumns = @JoinColumn(name = "account_id"),
//            //             inverseJoinColumns = @JoinColumn(name = "tag_id"))
//            //         private Set<Tag> tags = new HashSet<>();
//
//            a.getTags().add(tag);
//
//            // [3] 위와 같은 연관관계 설정에서 .add(tag)를 호출하면,
//            //     JPA는 다음과 같은 내부 과정을 거쳐 연관 테이블(account_tags)에 insert 쿼리를 발생시킨다:
//            //     (1) 영속 상태의 Account 엔티티에서 컬렉션(Set<Tag>)에 새로운 엔티티를 추가하면,
//            //         JPA는 이를 dirty checking 대상으로 등록한다.
//            //     (2) 트랜잭션 커밋 시점 또는 EntityManager.flush() 시점에 JPA는 변경 내용을 flush하면서
//            //         조인 테이블에 INSERT 쿼리를 자동으로 생성한다.
//            //
//            //     결과적으로, 다음과 같은 SQL이 실행된다:
//            //     INSERT INTO account_tags (account_id, tag_id) VALUES (?, ?);
//
//            // [4] 이때 Tag 객체는 이미 저장(persisted)된 상태여야 한다.
//            //     CascadeType.PERSIST 또는 CascadeType.MERGE가 설정되어 있지 않다면,
//            //     Tag는 미리 tagRepository.save(tag) 등을 통해 DB에 저장된 상태여야 한다.
//            //     그렇지 않으면 TransientObjectException 또는 foreign key 오류가 발생할 수 있다.
//
//            // [5] 연관 컬렉션의 자료구조로 Set<Tag>를 사용하는 이유는 중복 방지를 위함이다.
//            //     Tag는 @EqualsAndHashCode(of = "id")로 정의되어 있으므로,
//            //     동일한 ID를 가진 Tag는 중복 삽입되지 않는다.
//            //     만약 List<Tag>를 사용했다면 중복된 태그가 조인 테이블에 여러 번 insert 될 수 있다.
//
//            // [6] Fetch 전략에 주의할 것. 일반적으로 @ManyToMany(fetch = FetchType.LAZY)가 권장된다.
//            //     FetchType.EAGER일 경우, 모든 연관 태그를 즉시 로딩하게 되며
//            //     여러 Account를 불러오는 시나리오에서 N+1 문제를 유발할 수 있다.
//            //     해결책: fetch join 또는 @EntityGraph 활용.
//
//            // [7] 이 메서드는 반드시 트랜잭션 범위 내(@Transactional)에서 호출되어야 한다.
//            //     트랜잭션이 있어야 JPA의 flush가 발생하며, 변경 내용이 DB에 반영된다.
//            //     트랜잭션 없이 실행된다면 add(tag)를 호출해도 DB에는 저장되지 않는다.
//        });
//    }
//
//    public void removeTag(Account account, Tag tag) {
//        // [1] 전달된 account 객체는 컨트롤러에서 인증된 사용자 정보를 기반으로 주입된 principal 객체이다.
//        //     이 객체는 JPA 영속성 컨텍스트(persistence context)에서 관리되지 않는 detached 상태일 수 있다.
//        //     detached 상태에서는 연관관계 변경(예: 태그 제거)이 DB에 반영되지 않기 때문에,
//        //     반드시 repository를 통해 ID로 조회하여 영속 상태로 만든 후 처리해야 한다.
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        byId.ifPresent(a -> {
//            // [2] 조회된 a는 영속 상태의 Account 객체이므로,
//            //     JPA는 이 객체에 대한 연관관계 변경을 추적할 수 있다.
//            //     a.getTags().remove(tag)는 컬렉션(Set<Tag>)에서 요소를 제거하는 자바 코드지만,
//            //     JPA는 이를 연관 테이블에서의 DELETE 작업으로 해석한다.
//
//            a.getTags().remove(tag);
//
//            // [3] 위와 같은 연관관계 설정(@ManyToMany + @JoinTable)을 통해 다음과 같은 일이 벌어진다:
//            //     (1) tags 컬렉션에서 태그를 제거하면, JPA는 이를 dirty checking 대상으로 간주한다.
//            //     (2) 트랜잭션 커밋 또는 flush 시점에 조인 테이블(account_tags)에서
//            //         해당 (account_id, tag_id) 쌍을 삭제하는 DELETE 쿼리를 자동 생성한다.
//            //
//            //     예시:
//            //     DELETE FROM account_tags WHERE account_id = ? AND tag_id = ?;
//
//            // [4] Tag 객체 역시 DB에 저장된(persisted) 상태여야 하며,
//            //     equals/hashCode 비교는 tag.id를 기준으로 이루어진다.
//            //     즉, remove가 정상적으로 작동하려면 제거하려는 Tag와 동일한 ID를 가진 Tag 객체여야 한다.
//
//            // [5] 연관 컬렉션으로 Set<Tag>를 사용하는 이유는 중복 제거뿐만 아니라,
//            //     삭제 시 정확한 일치를 위한 equals/hashCode 기반 비교 때문이다.
//            //     List를 사용하면 index 기반으로 삭제가 이뤄져 예상치 못한 동작이 발생할 수 있다.
//
//            // [6] 이 연관관계가 FetchType.EAGER로 설정되어 있을 경우,
//            //     account를 조회할 때 모든 태그가 미리 로딩되므로 remove 호출 시 N+1 문제가 생길 수 있다.
//            //     따라서 일반적으로 FetchType.LAZY 설정 후 필요 시 fetch join 또는 @EntityGraph를 사용하는 것이 권장된다.
//
//            // [7] remove(tag) 호출 역시 트랜잭션 범위(@Transactional) 내에서 수행되어야 한다.
//            //     그래야 JPA가 변경 내용을 flush하고 DB에 DELETE 쿼리를 반영한다.
//        });
//    }
//
//    public Set<Zone> getZones(Account account) {
//        // [1] 전달된 account는 인증 객체에서 꺼낸 Principal 기반 객체로,
//        //     JPA의 영속성 컨텍스트(persistence context)에서 관리되지 않는 상태일 수 있다 (detached 상태).
//        //
//        //     이 상태에서는 연관관계 필드(zones)를 lazy 로딩하려고 할 때
//        //     LazyInitializationException이 발생할 수 있다.
//        //
//        //     => 따라서 accountRepository.findById(...)를 통해 다시 조회하여
//        //        영속성 컨텍스트에 포함된 객체로 만들어야 한다.
//
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        // [2] 영속 상태로 조회된 Account 객체에서 getZones()를 호출하면,
//        //     JPA는 필요 시 지연 로딩을 통해 zone 엔티티들을 SELECT 쿼리로 불러온다.
//        //
//        //     예시: SELECT * FROM account_zones WHERE account_id = ?
//        //           JOIN zone ON zone.id = account_zones.zone_id
//        //
//        //     트랜잭션이 열려 있는 상태에서만 lazy loading이 정상적으로 작동한다.
//        //     → 따라서 이 메서드는 @Transactional 범위 내에서 실행되어야 한다.
//
//        return byId.orElseThrow().getZones();
//    }
//
//
//    public void addZone(Account account, Zone zone) {
//        // [1] 전달된 account 객체는 컨트롤러에서 주입된 principal 객체이며,
//        //     JPA 영속성 컨텍스트에서 관리되지 않는 detached 상태일 수 있다.
//        //
//        //     이 상태에서 연관관계 변경 (zones.add(zone))을 해도
//        //     JPA는 이를 추적하거나 DB에 반영하지 않는다.
//        //
//        //     → 반드시 accountRepository.findById(...)를 통해 영속 상태로 다시 조회해야 한다.
//
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        byId.ifPresent(a -> {
//            // [2] 조회된 Account 객체는 영속 상태이므로,
//            //     연관관계 컬렉션(Set<Zone>)의 변경 사항은 JPA가 추적할 수 있다.
//            //
//            //     아래 코드는 단순한 컬렉션 조작처럼 보이지만,
//            //     내부적으로는 연관관계 변경을 추적하여 account_zones 테이블에 insert 쿼리를 발생시킨다.
//
//            a.getZones().add(zone);
//
//            // [3] 이때 연관관계는 보통 다음과 같이 설정되어 있음:
//            //     @ManyToMany
//            //     @JoinTable(name = "account_zones",
//            //         joinColumns = @JoinColumn(name = "account_id"),
//            //         inverseJoinColumns = @JoinColumn(name = "zone_id"))
//            //
//            //     따라서 JPA는 add()를 인식하여 다음 쿼리를 생성함:
//            //     INSERT INTO account_zones (account_id, zone_id) VALUES (?, ?)
//
//            // [4] zone은 반드시 이미 영속 상태(persisted)여야 하며,
//            //     CascadeType.PERSIST 또는 MERGE가 없으면 미리 save 되어 있어야 한다.
//            //
//            //     그렇지 않으면 TransientObjectException 발생 또는 FK 오류 발생 가능.
//
//            // [5] 중복 방지를 위해 연관 컬렉션은 Set<Zone>으로 선언되어 있음.
//            //     Zone은 @EqualsAndHashCode(of = "id")로 정의되어 있어
//            //     동일 ID의 Zone은 중복 추가되지 않음.
//        });
//    }
//
//
//    public void removeZone(Account account, Zone zone) {
//        // [1] account는 detached 상태일 수 있으므로
//        //     JPA가 상태 변경을 추적하지 못한다.
//        //     → 따라서 ID 기반으로 다시 조회하여 영속 상태로 만들어야 한다.
//
//        Optional<Account> byId = accountRepository.findById(account.getId());
//
//        byId.ifPresent(a -> {
//            // [2] 영속 상태의 Account 객체에서 연관관계 제거
//            //     → JPA는 이를 감지하여 account_zones 테이블에서 해당 조합을 삭제하는 쿼리를 생성한다.
//            //
//            //     예시: DELETE FROM account_zones WHERE account_id = ? AND zone_id = ?
//
//            a.getZones().remove(zone);
//
//            // [3] remove 시에도 equals/hashCode가 일치해야 동작한다.
//            //     → Zone은 id 기반으로 equals/hashCode가 정의되어 있어야 한다.
//            //     그렇지 않으면 컬렉션에서 원하는 엔티티를 찾지 못해 삭제되지 않음.
//
//            // [4] 이 메서드도 반드시 트랜잭션 범위 내에서 실행되어야 한다.
//            //     → 그래야 JPA가 flush 시점에 delete 쿼리를 DB에 반영한다.
//        });
//    }
//
//    /**
//     * 닉네임을 기준으로 사용자(Account) 정보를 조회하는 메서드
//     *
//     * ✅ 주요 목적:
//     * - URL 또는 비즈니스 로직 등에서 전달받은 `nickname` 값을 기반으로
//     *   해당 사용자의 계정 정보를 조회하여 반환한다.
//     *
//     * ✅ 예외 처리:
//     * - 해당 닉네임을 가진 사용자가 존재하지 않을 경우 `IllegalArgumentException` 예외를 발생시킨다.
//     *   → 예외 메시지에는 어떤 닉네임이 문제였는지를 명시하여, 디버깅에 도움이 되도록 한다.
//     *
//     * ✅ 호출 예시:
//     * - 사용자 프로필 보기: `/profile/{nickname}` 요청 시 사용됨
//     * - Spring MVC의 컨트롤러에서 전달받은 nickname을 기준으로 계정 정보를 꺼내고
//     *   조회된 Account를 모델에 담아서 View에 전달한다.
//     *
//     * ✅ 왜 닉네임으로 찾는가?
//     * - 이메일은 개인정보 보호 이슈가 있을 수 있고,
//     *   닉네임은 상대방에게 공유되어도 문제가 없는 공개 식별자 역할을 하기 때문.
//     *
//     * ✅ 예외 처리 이유:
//     * - 보통 `.orElseThrow()`를 사용하는 Optional 대신, 명시적으로 null을 체크하고
//     *   적절한 메시지를 담은 IllegalArgumentException을 던져 개발자가 빠르게 문제를 인식하도록 한다.
//     */
//    public Account getAccount(String nickname) {
//        Account account = accountRepository.findByNickname(nickname); // 닉네임으로 계정 조회
//
//        if (account == null) {
//            // 해당 닉네임을 가진 사용자가 존재하지 않음 → 예외 발생
//            throw new IllegalArgumentException(nickname + "에 해당하는 사용자가 없습니다.");
//        }
//
//        return account; // 조회된 계정 객체 반환
//    }
//}