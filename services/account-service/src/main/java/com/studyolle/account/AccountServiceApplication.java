package com.studyolle.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * account-service 진입점
 *
 * [역할]
 * - 회원 가입 / 로그인 / 토큰 재발급
 * - JWT 발급 전담 (검증은 api-gateway에서 수행)
 * - 이메일 인증 처리
 *
 * [포트] 8081
 * [DB]   postgres-account (5433)
 */
@SpringBootApplication
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}