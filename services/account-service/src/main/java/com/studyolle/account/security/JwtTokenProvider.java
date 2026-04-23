package com.studyolle.account.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰을 발급하는 account-service 전담 컴포넌트.
 *
 * [역할 분담]
 * - JWT '발급'은 오직 account-service 의 이 클래스에서만 수행한다.
 * - JWT '검증'은 api-gateway / admin-gateway 의 JwtAuthenticationFilter 에서만 수행한다.
 *   → 검증 로직이 여기에 있을 필요는 없고, 검증 시 사용하는 secret 만 동일해야 한다.
 *
 * [JWT 에 담기는 claim 구조]
 * Phase 8 이전까지는 sub / nickname / role 세 가지만 담았다.
 * Phase 8 의 이메일 인증 접근 제한 기능을 위해 emailVerified claim 이 추가된다.
 *
 * sub            → X-Account-Id              (게이트웨이가 헤더로 변환)
 * nickname       → X-Account-Nickname
 * role           → X-Account-Role
 * emailVerified  → X-Account-Email-Verified  ← 신규 추가
 *
 * [왜 이 값을 JWT claim 으로 담는가 — 실시간 조회 vs 스냅샷]
 * 매 쓰기 요청마다 account-service 를 호출해서 emailVerified 를 실시간 조회할 수도 있다.
 * 그러나 그렇게 하면 모든 쓰기 요청에 네트워크 호출 한 번이 추가되어 성능이 희생된다.
 *
 * JWT claim 방식은 "발급 시점의 스냅샷"이라는 단점이 있다. 사용자가 방금 이메일 인증을
 * 완료했는데도 기존 JWT 에는 emailVerified=false 가 담겨 있어 여전히 막힐 수 있다.
 * 이 단점을 해결하기 위해 AuthController.checkEmailToken() 이 인증 성공 시점에
 * JWT 를 재발급해서 응답하도록 수정한다. 프론트는 받은 새 토큰으로 기존 토큰을 교체한다.
 *
 * 이 방식의 장점:
 * - 쓰기 요청마다 네트워크 호출 없음 (헤더 값만 확인)
 * - 다른 claim 들 (nickname, role) 과 동일한 패턴 — 구조 일관성
 * - 회원 권한 변경 기능에서 role 을 다루는 방식과 똑같음
 */
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

    /**
     * accessToken 을 발급한다.
     *
     * [메서드 시그니처 변경 — emailVerified 파라미터 추가]
     * 기존: createAccessToken(accountId, nickname, role)
     * 신규: createAccessToken(accountId, nickname, role, emailVerified)
     *
     * 이 메서드를 호출하는 모든 지점 (AccountAuthService.login / refresh / loginByEmailToken)
     * 에서 Account 엔티티의 emailVerified 를 함께 전달하도록 수정이 필요하다.
     *
     * @param accountId     사용자 DB PK. JWT 의 sub claim (subject) 에 담김
     * @param nickname      사용자 닉네임. nickname claim 에 담김
     * @param role          사용자 권한 (ROLE_USER / ROLE_ADMIN). role claim 에 담김
     * @param emailVerified 이메일 인증 완료 여부. emailVerified claim 에 담김  ← 신규
     * @return 서명된 JWT 문자열
     */
    public String createAccessToken(Long accountId, String nickname, String role, boolean emailVerified) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("nickname", nickname)
                .claim("role", role)
                .claim("emailVerified", emailVerified)  // ← 신규 claim
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * refreshToken 을 발급한다.
     *
     * refreshToken 은 단순 accountId 만 담는다. 새 accessToken 을 재발급할 때만 사용되며,
     * 비즈니스 정보(role, emailVerified 등) 는 재발급 시점의 DB 상태에서 다시 읽어야 정확하다.
     * 그러므로 이 토큰에는 emailVerified 를 담지 않는다.
     */
    public String createRefreshToken(Long accountId) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * refreshToken 에서 accountId 를 추출한다. 토큰 재발급(refresh) 엔드포인트에서만 사용.
     */
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