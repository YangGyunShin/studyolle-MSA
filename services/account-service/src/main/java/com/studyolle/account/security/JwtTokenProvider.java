package com.studyolle.account.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// account-service 전담: JWT를 '발급'하는 역할만 한다.
// JWT '검증'은 api-gateway의 JwtAuthenticationFilter에서만 수행한다.
@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // api-gateway가 헤더로 변환하는 Claims 구조:
    //   sub      → X-Account-Id
    //   nickname → X-Account-Nickname
    //   role     → X-Account-Role
    public String createAccessToken(Long accountId, String nickname, String role) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("nickname", nickname)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public String createRefreshToken(Long accountId) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    // 토큰 재발급(refresh) 시에만 사용 - refreshToken에서 accountId 추출
    public Long getAccountId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.parseLong(subject);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
    }

}