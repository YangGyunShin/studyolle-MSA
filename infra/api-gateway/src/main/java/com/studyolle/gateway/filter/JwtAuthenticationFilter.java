package com.studyolle.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// AbstractGatewayFilterFactory를 상속받아 Spring Cloud Gateway의 커스텀 필터로 등록
// application.yml의 filters: - JwtAuthenticationFilter 항목과 이름이 일치해야 인식됨
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    // application.yml의 jwt.secret 값을 주입받음
    // account-service에서 JWT를 발급할 때 사용한 secret과 반드시 동일해야 서명 검증 가능
    @Value("${jwt.secret}")
    private String secret;

    // 부모 클래스에 Config 타입 정보를 전달 (Spring이 필터 설정을 바인딩할 때 필요)
    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    // 실제 필터 로직을 정의하는 메서드
    // apply()가 반환하는 GatewayFilter가 요청마다 실행됨
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // 요청 헤더에서 Authorization 값 추출
            // 예: "Authorization: Bearer eyJhbGciOi..."
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // Authorization 헤더가 없거나 "Bearer "로 시작하지 않으면 인증 실패
            // 401 반환 후 체인 종료 → 하위 서비스까지 요청이 전달되지 않음
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // "Bearer " 이후의 실제 토큰 문자열만 추출
            String token = authHeader.substring(7);

            try {
                // application.yml의 jwt.secret으로 HMAC-SHA 서명 키 생성
                // account-service가 토큰 발급 시 사용한 키와 동일해야 함
                SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

                // JWT 파싱 + 서명 검증 + 만료 여부 확인을 한 번에 수행
                // 서명이 다르거나 만료된 토큰이면 예외 발생 → catch 블록에서 401 반환
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // JWT Claims에서 사용자 정보 추출
                // sub: account-service에서 발급 시 Account DB PK를 subject로 설정
                // nickname: JWT 발급 시 커스텀 클레임으로 추가한 닉네임
                String accountId = claims.getSubject();
                String nickname = claims.get("nickname", String.class);

                // 검증된 사용자 정보를 헤더에 추가해서 하위 서비스로 전달
                // 하위 서비스(study, event 등)는 JWT 없이 이 헤더만 꺼내서 사용자 식별
                // exchange는 불변 객체이므로 mutate()로 새 객체를 만들어 헤더를 추가해야 함
                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(r -> r.header("X-Account-Id", accountId)
                                .header("X-Account-Nickname", nickname))
                        .build();

                // 헤더가 추가된 새 exchange로 다음 필터 또는 라우팅 체인 실행
                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                // 서명 불일치, 토큰 만료, 형식 오류 등 모든 JWT 관련 예외를 401로 처리
                // 예외 메시지를 클라이언트에 노출하지 않아 보안 강화
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        };
    }

    // 필터에 전달할 설정값을 담는 내부 클래스
    // 현재는 설정값이 없어 비어있음
    // 추후 필터별로 다른 설정이 필요하면 여기에 필드 추가
    // 예: private boolean requireHttps; → yml에서 args로 값 전달 가능
    public static class Config {}
}