package com.studyolle.infra.dev;

import com.studyolle.modules.account.entity.Account;
import com.studyolle.modules.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 개발 환경 전용 초기 데이터 생성기
 *
 * [목적]
 * - 개발 서버 기동 시 관리자 계정이 없으면 자동으로 생성한다.
 * - 매번 DB에서 수동으로 role을 변경하는 번거로움 제거.
 * - 이미 계정이 존재하면 아무 작업도 하지 않으므로 멱등성 보장.
 *
 * [안전 장치]
 * - @Profile("local"): spring.profiles.active=local 일 때만 Bean 등록
 * - 운영/테스트 환경에서는 이 Bean 자체가 존재하지 않음
 *
 * [생성 계정]
 *   이메일:   admin@studyolle.com
 *   닉네임:   admin
 *   비밀번호: admin1234!
 *   역할:     ROLE_ADMIN
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.existsByEmail("admin@studyolle.com")) {
            log.info("[DevDataInitializer] 관리자 계정이 이미 존재합니다. 건너뜁니다.");
            return;
        }

        Account admin = Account.builder()
                .email("admin@studyolle.com")
                .nickname("admin")
                .password(passwordEncoder.encode("admin1234!"))
                .emailVerified(true)
                .joinedAt(LocalDateTime.now())
                .role("ROLE_ADMIN")
                .studyCreatedByWeb(true)
                .studyEnrollmentResultByWeb(true)
                .studyUpdatedByWeb(true)
                .build();

        accountRepository.save(admin);
        log.info("[DevDataInitializer] 관리자 계정 생성 완료 → admin@studyolle.com / admin1234!");
    }
}