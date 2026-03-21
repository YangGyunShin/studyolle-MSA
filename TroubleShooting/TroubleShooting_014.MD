# 🔧 트러블슈팅: 로그인 후 홈 이동 시 비로그인 화면이 표시되는 문제 (OptionalJwtFilter 필요)

## 📌 상황 요약

로그인에 성공하고 홈 페이지(/)로 이동했는데 우측 상단 nav 바에
"로그인 / 회원가입" 버튼이 그대로 표시됐다.
쿠키에 accessToken을 저장했음에도 HomeController가 로그인 상태를 인식하지 못했다.

---

## 📋 증상

```
① 로그인 성공 → 쿠키에 accessToken 저장 → window.location.href = '/'
② 홈 페이지 로드
③ 우측 상단: "로그인 | 회원가입" 버튼이 그대로 표시
④ 대시보드가 아닌 비로그인 랜딩 페이지가 표시됨
```

---

## 🔍 원인 분석

### 1단계: HomeController가 로그인 여부를 어떻게 판단하는가

`HomeController.java`를 보면 이렇게 되어 있다.

```java
@GetMapping("/")
public String home(
        @RequestHeader(value = "X-Account-Id", required = false) Long accountId,
        Model model) {

    if (accountId == null) {
        // X-Account-Id 헤더가 없으면 → 비로그인 화면
        model.addAttribute("account", null);
        return "index";
    }

    // X-Account-Id 헤더가 있으면 → 로그인 화면
    AccountSummaryDto account = accountInternalClient.getAccountSummary(accountId);
    model.addAttribute("account", account);
    return "index";
}
```

`HomeController`는 오직 `X-Account-Id` 헤더만 보고 판단한다.
이 헤더가 없으면 무조건 비로그인으로 처리한다.

---

### 2단계: X-Account-Id 헤더는 누가 추가하는가

`X-Account-Id` 헤더는 `JwtAuthenticationFilter`가 추가한다.

```java
// JwtAuthenticationFilter.java 내부
Claims claims = Jwts.parser()...parseSignedClaims(token).getPayload();

String accountId = claims.getSubject();
String nickname  = claims.get("nickname", String.class);

// X-Account-Id 헤더를 추가해서 하위 서비스로 전달
ServerWebExchange modifiedExchange = exchange.mutate()
        .request(r -> r.header("X-Account-Id", accountId)
                .header("X-Account-Nickname", nickname))
        .build();
```

---

### 3단계: api-gateway 라우팅과 필터의 관계

api-gateway의 라우팅 설정을 보면 이렇게 되어 있었다.

```yaml
routes:
  - id: account-service-private
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/accounts/**
    filters:
      - JwtAuthenticationFilter   ← 필터 있음

  - id: frontend-service
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/**
    # filters 없음 ← 문제
```

**필터는 해당 라우트에만 적용된다.**

브라우저가 `GET /`를 요청하면 `/**` 라우트를 타는데, 이 라우트에는 필터가 없다.
필터가 없으니 쿠키에서 JWT를 읽는 코드 자체가 실행되지 않는다.
`X-Account-Id` 헤더가 추가되지 않으니 HomeController는 비로그인으로 처리한다.

---

### 4단계: 왜 기존 JwtAuthenticationFilter를 그대로 쓸 수 없는가

"그러면 `/**` 라우트에 `JwtAuthenticationFilter`를 그냥 달면 되지 않는가?"라고 생각할 수 있다.

안 된다. `JwtAuthenticationFilter`의 동작을 보면 이렇다.

```java
// 헤더에도 쿠키에도 없으면 → 401 반환하고 종료
if (token == null) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
}
```

토큰이 없으면 **무조건 401을 반환**한다.

비로그인 사용자가 `/login`이나 `/sign-up`에 접근하면 쿠키에 토큰이 없다.
`/**` 라우트에 `JwtAuthenticationFilter`를 달면 이런 일이 생긴다.

```
비로그인 사용자 → GET /login
  → JwtAuthenticationFilter 실행
  → 토큰 없음 → 401 반환
  → 로그인 페이지에 접근할 수 없음 (무한 루프)
```

로그인 페이지 자체에 접근하려면 토큰이 필요한데, 토큰을 얻으려면 로그인이 필요한 상황이 된다.

---

### 5단계: 두 필터의 역할 차이

| 필터 | 토큰 없을 때 | 토큰 있을 때 | 사용 경로 |
|------|------------|------------|----------|
| `JwtAuthenticationFilter` | 401 반환 (접근 차단) | X-Account-Id 추가 | 반드시 로그인 필요한 API |
| `OptionalJwtFilter` | 그냥 통과 (비로그인 처리) | X-Account-Id 추가 | 비로그인도 접근 가능한 페이지 |

```
[JwtAuthenticationFilter가 필요한 경로]
GET /api/accounts/me     → 내 정보 조회 (로그인 안 하면 볼 수 없음)
POST /api/studies        → 스터디 생성 (로그인 안 하면 생성 불가)

[OptionalJwtFilter가 필요한 경로]
GET /                    → 홈 (비로그인: 랜딩, 로그인: 대시보드)
GET /login               → 로그인 페이지 (비로그인도 접근 가능)
GET /sign-up             → 회원가입 페이지 (비로그인도 접근 가능)
GET /css/auth-style.css  → 정적 파일 (누구나 접근 가능)
```

---

## ✅ 해결 방법

### 1. OptionalJwtFilter.java 신규 생성

```java
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
@Component
public class OptionalJwtFilter extends AbstractGatewayFilterFactory<OptionalJwtFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    public OptionalJwtFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

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
            //    JwtAuthenticationFilter와 달리 401을 반환하지 않는다.
            if (token == null) {
                return chain.filter(exchange);
            }

            // 4. 토큰이 있으면 검증 시도
            try {
                SecretKey key = Keys.hmacShaKeyFor(
                        secret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String accountId = claims.getSubject();
                String nickname  = claims.get("nickname", String.class);

                // 검증 성공 → X-Account-Id 헤더 추가 후 통과
                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(r -> r.header("X-Account-Id", accountId)
                                .header("X-Account-Nickname", nickname))
                        .build();
                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                // 토큰 만료 또는 유효하지 않으면 그냥 통과 (비로그인 상태로 처리)
                return chain.filter(exchange);
            }
        };
    }

    public static class Config {}
}
```

### 2. application.yml 수정

```yaml
routes:
  # 공개 API - 필터 없음
  - id: account-service-public
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/auth/**

  # 인증 필요 API - 토큰 없으면 401
  - id: account-service-private
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/accounts/**
    filters:
      - JwtAuthenticationFilter   ← 기존 필터 유지

  # frontend-service - 비로그인도 접근 가능, 토큰 있으면 X-Account-Id 추가
  - id: frontend-service
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/**
    filters:
      - OptionalJwtFilter         ← 새 필터 추가
```

---

## 🔎 수정 후 흐름

```
[비로그인 사용자 → GET /]

브라우저: GET / (쿠키 없음)
  ↓
api-gateway: OptionalJwtFilter 실행
  ↓ 토큰 없음 → 그냥 통과
  ↓
HomeController: X-Account-Id 없음 → 비로그인 랜딩 화면 반환 (정상)


[로그인 사용자 → GET /]

브라우저: GET / (쿠키에 accessToken 있음)
  ↓
api-gateway: OptionalJwtFilter 실행
  ↓ 쿠키에서 accessToken 읽기 → JWT 검증 → X-Account-Id: 123 추가
  ↓
HomeController: X-Account-Id = 123 → 로그인 대시보드 화면 반환 (정상)


[비로그인 사용자 → GET /login]

브라우저: GET /login (쿠키 없음)
  ↓
api-gateway: OptionalJwtFilter 실행
  ↓ 토큰 없음 → 401 반환 안 함, 그냥 통과
  ↓
frontend-service: login.html 반환 (정상 접근 가능)
```

---

## 📎 관련 파일

- `infra/api-gateway/src/main/java/com/studyolle/gateway/filter/OptionalJwtFilter.java` (신규 생성)
- `infra/api-gateway/src/main/resources/application.yml` (frontend-service 라우트에 필터 추가)

---

## 📅 발생일

2026-03-21
