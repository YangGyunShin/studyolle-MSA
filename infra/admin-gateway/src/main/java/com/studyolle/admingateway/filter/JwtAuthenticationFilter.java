package com.studyolle.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
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

            // 1. Authorization 헤더에서 JWT 추출 시도
            String token = null;

            // 요청 헤더에서 Authorization 값 추출
            // 예: "Authorization: Bearer eyJhbGciOi..."
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // 2. Authorization 헤더가 없으면 쿠키에서 accessToken 추출 시도
            // 브라우저가 페이지를 요청할 때는 Authorization 헤더 없이 쿠키만 포함된다.
            // login.html에서 로그인 성공 시 document.cookie로 저장한 accessToken을 여기서 읽는다.
            if (token == null) {
                HttpCookie cookie = exchange.getRequest()
                        .getCookies()
                        .getFirst("accessToken");
                if (cookie != null) {
                    token = cookie.getValue();
                }
            }

            // 3. 헤더에도 쿠키에도 없으면 401
            if (token == null) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

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
                // role: JWT 발급 시 커스텀 클레임으로 추가한 권한 (ROLE_USER / ROLE_ADMIN)
                String accountId = claims.getSubject();
                String nickname = claims.get("nickname", String.class);
                String role = claims.get("role", String.class);

                // 검증된 사용자 정보를 헤더에 추가해서 하위 서비스로 전달
                // 하위 서비스(study, event, admin 등)는 JWT 없이 이 헤더만 꺼내서 사용자 식별 및 권한 확인
                // X-Account-Role 은 admin-service 에서 2차 권한 검증에 사용된다
                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(r -> r.header("X-Account-Id", accountId)
                                .header("X-Account-Nickname", nickname)
                                .header("X-Account-Role", role))
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

/*
 * ============================================================
 * [1] 왜 Claims를 거쳐서 accountId, nickname을 추출하는가
 * ============================================================
 *
 * 클라이언트가 API Gateway에 전송하는 것은 오직 토큰 문자열 하나뿐이다.
 *   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMiLCJuaWNrbmFtZSI6InlhbmdneXVuIn0.서명값
 *
 * accountId와 nickname은 이 토큰 문자열 안에 인코딩되어 있다.
 * exchange 객체에는 이 토큰 문자열만 존재하며, accountId와 nickname이 별도로 존재하지 않는다.
 * 따라서 exchange에서 accountId와 nickname을 바로 꺼내는 것은 불가능하다.
 *
 * 토큰 문자열에서 값을 꺼내려면 반드시 파싱 과정이 필요하고,
 * 파싱 결과로 생성된 객체가 바로 Claims이다.
 *
 *   exchange가 가진 것  →  "eyJhbGci..." (인코딩된 문자열)
 *                                  ↓ 파싱 (Claims 생성)
 *   Claims가 가진 것    →  { sub: "123", nickname: "양균", role: "ROLE_USER" }
 *                                  ↓ 꺼내기
 *   최종 추출           →  accountId = "123", nickname = "양균"
 *
 *
 * ============================================================
 * [2] Claims 생성 과정
 * ============================================================
 *
 * JWT 토큰의 구조:
 *   eyJhbGciOiJIUzI1NiJ9          (헤더 - Base64 인코딩)
 *   .eyJzdWIiOiIxMjMiLCJuaWNrbmFtZSI6InlhbmdneXVuIn0  (페이로드 - Base64 인코딩)
 *   .서명값                         (헤더+페이로드를 secret으로 서명한 값)
 *
 * parseSignedClaims(token) 호출 시 내부적으로 아래 세 단계가 순서대로 실행된다.
 *
 *   단계 1. 토큰을 헤더 / 페이로드 / 서명 세 부분으로 분리
 *
 *   단계 2. 서명 검증
 *           헤더+페이로드를 jwt.secret으로 다시 서명한 값과 토큰의 서명값을 비교
 *           일치하지 않으면 SignatureException 발생 → catch에서 401 반환
 *           (토큰이 위변조된 경우 이 단계에서 걸림)
 *
 *   단계 3. 만료 검증
 *           페이로드의 exp 값(만료 시각)과 현재 시각을 비교
 *           만료된 경우 ExpiredJwtException 발생 → catch에서 401 반환
 *
 *   세 단계를 모두 통과하면 페이로드를 Map 형태로 꺼낼 수 있는 Claims 객체가 반환된다.
 *
 *
 * ============================================================
 * [3] 기존 exchange는 전달되는가
 * ============================================================
 *
 * 전달되지 않는다. 기존 exchange는 버리고 새로 만든 modifiedExchange만 전달한다.
 *
 * ServerWebExchange는 불변 객체(immutable)이기 때문에
 * 한 번 생성된 요청 객체의 헤더를 직접 추가하거나 수정할 수 없다.
 *
 * 따라서 mutate()로 기존 exchange의 모든 내용(URL, body, 기존 헤더 등)을 복사한 후
 * X-Account-Id, X-Account-Nickname 헤더만 추가한 새 객체(modifiedExchange)를 생성해서 전달한다.
 *
 *   exchange          →  원본 요청 (Authorization 헤더만 있음)
 *   modifiedExchange  →  원본 요청 복사 + X-Account-Id, X-Account-Nickname 헤더 추가
 *
 *   chain.filter(modifiedExchange) 호출 이후 기존 exchange는 사용되지 않는다.
 */