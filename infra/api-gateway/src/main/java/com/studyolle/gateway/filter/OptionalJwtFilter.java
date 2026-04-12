package com.studyolle.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// frontend-service 페이지 라우트 전용 필터.
// JwtAuthenticationFilter와 달리 토큰이 없어도 401을 반환하지 않는다.
// 토큰이 있고 유효하면 X-Account-Id 헤더를 추가하고,
// 없거나 유효하지 않으면 헤더 없이 그냥 통과시킨다.
// HomeController가 X-Account-Id 유무로 로그인/비로그인 화면을 분기한다.
@Component
public class OptionalJwtFilter extends AbstractGatewayFilterFactory<OptionalJwtFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    public OptionalJwtFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(OptionalJwtFilter.Config config) {
        return ((exchange, chain) -> {

            // 1. Authorization 헤더에서 토큰 추출 시도
            String token = null;
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // 2. 헤더에 없으면 쿠키에서 추출 시도
            if (token == null) {
                HttpCookie cookie = exchange.getRequest()
                        .getCookies()
                        .getFirst("accessToken");
                if (cookie != null) {
                    token = cookie.getValue();
                }
            }

            // 3. 토큰이 없으면 그냥 통과 (비로그인 상태로 처리)
            if (token == null) {
                return chain.filter(exchange);
            }

            // 4. 토큰이 있으면 검증 시도
            try {
                SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String accountId = claims.getSubject();
                String nickname = claims.get("nickname", String.class);
                String role = claims.get("role", String.class);

                // 검증 성공 → X-Account-Id, Nickname, Role 헤더 추가 후 통과
                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(r -> r.header("X-Account-Id", accountId)
                                .header("X-Account-Nickname", nickname)
                                .header("X-Account-Role", role))
                        .build();
                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                // 토큰이 만료됐거나 유효하지 않으면 그냥 통과 (비로그인 상태로 처리)
                return chain.filter(exchange);
            }
        } );
    }

    public static class Config {}
}