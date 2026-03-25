# StudyOlle MSA - 인증 및 요청 처리 흐름 가이드

## 목차
1. [전체 아키텍처 개요](#1-전체-아키텍처-개요)
2. [헤더 규칙 (전 서비스 공통)](#2-헤더-규칙-전-서비스-공통)
3. [요청 유형별 상세 흐름](#3-요청-유형별-상세-흐름)
4. [API Gateway 라우팅 규칙](#4-api-gateway-라우팅-규칙)
5. [각 서비스의 역할 분담](#5-각-서비스의-역할-분담)
6. [서비스 내부 API 설계 원칙](#6-서비스-내부-api-설계-원칙)
7. [필터 구조](#7-필터-구조)
8. [신규 서비스 추가 시 체크리스트](#8-신규-서비스-추가-시-체크리스트)

---

## 1. 전체 아키텍처 개요

```
[클라이언트 (브라우저)]
        │
        ▼
[API Gateway :8080]  ←── 모든 외부 요청의 단일 진입점
        │                 JWT 검증 / 권한 확인 / 헤더 변환 담당
        │
   ┌────┴────────────────────────┐
   ▼                             ▼
[account-service :8081]    [admin-service :8082]
[study-service   :8083]    [event-service  :8084]
[notification-service ...]
        │
        ▼
[Eureka Server :8761]  ←── 서비스 디스커버리 (lb://SERVICE-NAME 라우팅)
[Config Server :8888]  ←── 중앙 설정 관리
```

**핵심 원칙:**
- 외부 요청은 반드시 API Gateway를 통해서만 들어온다.
- JWT 검증은 API Gateway에서만 수행한다. 각 서비스는 JWT를 직접 검증하지 않는다.
- 각 서비스는 API Gateway가 추가한 헤더(`X-Account-Id` 등)를 신뢰한다.
- 서비스 간 내부 통신은 `/internal/**` 경로를 통해서만 이루어진다.
- 로그인 상태는 **쿠키(accessToken)**로 유지된다 (브라우저 페이지 이동 시 자동 포함).

---

## 2. 헤더 규칙 (전 서비스 공통)

모든 서비스에서 아래 헤더 규칙을 동일하게 적용한다.

### 외부 요청 (API Gateway가 추가)

| 헤더 이름 | 추가 주체 | 값 예시 | 설명 |
|----------|----------|--------|------|
| `X-Account-Id` | API Gateway | `123` | 인증된 사용자의 DB PK |
| `X-Account-Role` | API Gateway | `ROLE_USER` / `ROLE_ADMIN` | 사용자 권한 |
| `X-Account-Nickname` | API Gateway | `양균` | 사용자 닉네임 |

### 내부 요청 (서비스가 추가)

| 헤더 이름 | 추가 주체 | 값 예시 | 설명 |
|----------|----------|--------|------|
| `X-Internal-Service` | 요청하는 서비스 | `frontend-service` | 내부 서비스 간 요청 식별자 |

> **규칙**: 각 서비스는 `X-Internal-Service` 헤더가 없는 `/internal/**` 요청을 403으로 차단한다.
> 외부에서 `/internal/**`로 직접 접근하는 것은 API Gateway에서 전면 차단한다.

---

## 3. 요청 유형별 상세 흐름

### 유형 1 - 공개 API (인증 불필요)

인증 없이 누구나 접근 가능한 엔드포인트.

```
[클라이언트]
     │
     │  POST /api/auth/login     { emailOrNickname, password }
     │  POST /api/auth/signup    { email, password, nickname }
     │  POST /api/auth/refresh   { refreshToken }
     ▼
[API Gateway]
     │  - 화이트리스트 경로이므로 JWT 검증 없이 그냥 통과
     │  - 헤더 추가 없음
     ▼
[account-service]
     │  - 로그인: 이메일/패스워드 검증 → JWT 발급 → 반환
     │  - 회원가입: 회원 저장 → 이메일 인증 토큰 발송
     │  - 토큰 재발급: refreshToken 검증 → 새 accessToken 반환
     ▼
[클라이언트]
     응답: { accessToken, refreshToken }
     → JS가 localStorage + 쿠키(accessToken, max-age=1800) 모두 저장
```

---

### 유형 2 - 일반 사용자 요청 (JWT 인증 필요)

로그인한 사용자가 각 서비스에 접근하는 일반적인 흐름.

```
[클라이언트]
     │
     │  GET /api/studies/
     │  Authorization: Bearer {accessToken}   ← JS fetch() 호출 시
     ▼
[API Gateway - JwtAuthenticationFilter]
     │  1. Authorization 헤더에서 JWT 추출 시도
     │  2. 없으면 쿠키(accessToken)에서 추출 시도
     │  3. 둘 다 없으면 → 401 반환
     │  4. JWT 서명 검증 + 만료 확인
     │     실패하면 → 401 반환
     │  5. 검증 성공 → Claims에서 정보 추출 후 헤더 추가
     │     X-Account-Id: 123
     │     X-Account-Nickname: 양균
     ▼
[study-service]
     │  - JWT 라이브러리 사용하지 않음
     │  - X-Account-Id 헤더만 꺼내서 사용자 식별
     ▼
[클라이언트]
     응답: 스터디 목록 등 요청한 데이터
```

---

### 유형 3 - 페이지 이동 요청 (OptionalJwt — 로그인 여부 선택적)

브라우저가 페이지를 이동할 때 (a 태그 클릭, window.location.href 등).
JWT가 없어도 접근 가능하지만, 있으면 X-Account-Id가 추가되어 로그인 상태로 처리된다.

```
[브라우저]
     │
     │  GET /  (또는 /login, /sign-up, /css/main.css 등)
     │  Cookie: accessToken=eyJ...  ← 자동 포함
     ▼
[API Gateway - OptionalJwtFilter]
     │  1. Authorization 헤더에서 JWT 추출 시도
     │  2. 없으면 쿠키(accessToken)에서 추출 시도
     │
     │  [토큰 없음]        [토큰 있음 + 유효]    [토큰 있음 + 만료]
     │  그냥 통과           X-Account-Id 추가     그냥 통과
     │  (비로그인)          (로그인 상태)          (비로그인)
     ▼
[frontend-service]
     │  HomeController: X-Account-Id 없음 → 랜딩 페이지
     │  HomeController: X-Account-Id 있음 → 대시보드
     ▼
[브라우저]
     응답: HTML 페이지
```

---

### 유형 4 - 관리자 요청 (JWT 인증 + ROLE_ADMIN 확인)

```
[관리자 브라우저]
     │
     │  GET /api/admin/members
     │  Authorization: Bearer {accessToken}
     ▼
[API Gateway - JwtAuthenticationFilter → AdminRoleFilter]
     │  1. JWT 검증 실패 → 401
     │  2. X-Account-Role != ROLE_ADMIN → 403
     │  3. 통과 → admin-service로 라우팅
     ▼
[admin-service]
     │  X-Account-Role: ROLE_ADMIN 2차 확인
     ▼
[관리자 브라우저]
```

---

### 유형 5 - 서비스 간 내부 요청

```
[frontend-service]
     │
     │  GET /internal/accounts/123
     │  X-Internal-Service: frontend-service
     │  (Authorization 헤더 없음)
     ▼
[account-service - InternalRequestFilter]
     │  X-Internal-Service 헤더 확인 → 없으면 403
     │  통과 → AccountInternalController
     ▼
[frontend-service]
     응답: AccountSummaryResponse
```

**외부에서 /internal/** 직접 접근 시:**
```
api-gateway의 OptionalJwtFilter는 /** 라우팅으로
frontend-service로 보내지만,
각 서비스는 InternalRequestFilter로 자체 보호한다.
※ api-gateway에서 /internal/** 별도 차단 라우트를 추가하는 것이 권장됨
```

---

## 4. API Gateway 라우팅 규칙 (2026-03-21 현재)

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ["http://localhost:8090"]
            allowedMethods: [GET, POST, PUT, DELETE, OPTIONS]
            allowedHeaders: ["*"]
            allowCredentials: true
      routes:
        # 공개 API - 필터 없음
        - id: account-service-public
          uri: lb://ACCOUNT-SERVICE
          predicates:
            - Path=/api/auth/**

        # 인증 필요 - account-service
        - id: account-service-private
          uri: lb://ACCOUNT-SERVICE
          predicates:
            - Path=/api/accounts/**
          filters:
            - JwtAuthenticationFilter

        # frontend-service (모든 페이지 + 정적 파일)
        # /api/** 라우트가 위에 먼저 선언되어 있으므로 API 요청은 각 서비스로 간다
        - id: frontend-service
          uri: lb://FRONTEND-SERVICE
          predicates:
            - Path=/**
          filters:
            - OptionalJwtFilter
```

---

## 5. 각 서비스의 역할 분담

### account-service
- **역할**: 회원 관리 + JWT 발급
- **JWT 관련**: 발급만 담당 (검증은 API Gateway에서)
- **주요 API**:
  - `POST /api/auth/signup` - 회원가입
  - `POST /api/auth/login` - 로그인 (JWT 발급)
  - `POST /api/auth/refresh` - 토큰 재발급
  - `GET /api/auth/check-email-token` - 이메일 인증
  - `GET /api/accounts/me` - 내 정보 조회
  - `GET /internal/accounts/{id}` - 내부 전용: 계정 요약 정보 (frontend-service용)

### api-gateway
- **역할**: 외부 진입점 + JWT 검증 + 라우팅
- **JWT 관련**: 검증만 담당 (발급은 account-service에서)
- **필터**:
  - `JwtAuthenticationFilter` - JWT 검증 (Authorization 헤더 + 쿠키) + X-Account-* 헤더 추가
  - `OptionalJwtFilter` - 토큰 없어도 통과, 있으면 X-Account-Id 추가 (frontend 페이지용)
  - `AdminRoleFilter` - ROLE_ADMIN 여부 확인 (admin 경로용, 미구현)

### frontend-service
- **역할**: Thymeleaf HTML 서빙 (DB 없음)
- **인증 처리**: X-Account-Id 헤더로 로그인 여부 판단
- **내부 통신**: RestTemplate + InternalHeaderHelper로 account-service, study-service 호출

### study-service / event-service / 기타 서비스
- **역할**: 각 도메인 비즈니스 로직
- **JWT 관련**: 직접 검증하지 않음 (JWT 라이브러리 의존성 없음)
- **인증 처리**: `X-Account-Id` 헤더만 꺼내서 사용

---

## 6. 서비스 내부 API 설계 원칙

### URL 구조

```
# 외부 접근용 (JWT 인증 필요)
/api/{도메인}/**

# 서비스 간 내부 통신 전용 (외부 접근 불가)
/internal/{도메인}/**
```

### 내부 API 보호

```java
// InternalRequestFilter.java (각 서비스 공통)
@Component
public class InternalRequestFilter implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        if (request.getRequestURI().startsWith("/internal/")) {
            String internalService = request.getHeader("X-Internal-Service");
            if (internalService == null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }
        return true;
    }
}
```

### frontend-service 내부 호출 패턴 (RestTemplate)

```java
// InternalHeaderHelper.java
public static HttpEntity<Void> build(Long accountId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Internal-Service", "frontend-service");
    if (accountId != null) {
        headers.set("X-Account-Id", String.valueOf(accountId));
    }
    return new HttpEntity<>(headers);
}

// 사용 예시
restTemplate.exchange(
    "lb://ACCOUNT-SERVICE/internal/accounts/" + id,
    HttpMethod.GET,
    InternalHeaderHelper.build(accountId),
    AccountSummaryDto.class
);
```

---

## 7. 필터 구조

### JwtAuthenticationFilter (인증 필수 경로용)

```
요청 수신
    │
    ▼
1. Authorization 헤더에서 Bearer 토큰 추출 시도
    │ 없으면
    ▼
2. 쿠키(accessToken)에서 토큰 추출 시도
    │ 둘 다 없으면 → 401 반환
    ▼
3. JWT 서명 검증 + 만료 확인
    │ 실패 → 401 반환
    ▼
4. X-Account-Id, X-Account-Nickname 헤더 추가
    ▼
하위 서비스로 라우팅
```

### OptionalJwtFilter (frontend 페이지용)

```
요청 수신
    │
    ▼
1. Authorization 헤더 또는 쿠키에서 토큰 추출 시도
    │
    ├── 토큰 없음 → 그냥 통과 (비로그인)
    │
    ├── 토큰 있음 + 유효 → X-Account-Id 헤더 추가 후 통과 (로그인)
    │
    └── 토큰 있음 + 만료/오류 → 그냥 통과 (비로그인)
    ▼
frontend-service로 라우팅
```

### JWT Claims 구조

```json
{
  "sub": "123",
  "nickname": "양균",
  "role": "ROLE_USER",
  "iat": 1700000000,
  "exp": 1700001800
}
```

| 필드 | 설명 | API Gateway 처리 |
|-----|------|----------------|
| `sub` | Account DB PK | → `X-Account-Id` 헤더 |
| `nickname` | 닉네임 | → `X-Account-Nickname` 헤더 |
| `role` | 권한 | → `X-Account-Role` 헤더 (AdminRoleFilter용) |
| `exp` | 만료 시각 | 만료 여부 검증 |

---

## 8. 신규 서비스 추가 시 체크리스트

### API Gateway 수정 (application.yml)
- [ ] 새 서비스 라우트 추가 (`lb://NEW-SERVICE`)
- [ ] 공개 API 경로는 필터 제외
- [ ] 인증 필요 경로에 `JwtAuthenticationFilter` 추가

### 새 서비스 구현
- [ ] JWT 라이브러리 의존성 추가하지 않기 (api-gateway에서 검증 완료)
- [ ] `X-Account-Id` 헤더로 사용자 식별
- [ ] `X-Account-Role` 헤더로 권한 확인 (필요 시)
- [ ] `/internal/**` 경로에 `InternalRequestFilter` 적용
- [ ] 내부 API는 `X-Internal-Service` 헤더 검증

### 내부 API 제공 시 (다른 서비스가 호출)
- [ ] `/internal/{도메인}/{경로}` 형태로 엔드포인트 설계
- [ ] `@RequestHeader("X-Internal-Service") String internalService` 파라미터 추가

### 내부 API 호출 시 (다른 서비스를 호출)
- [ ] `InternalHeaderHelper.build(accountId)` 로 헤더 구성
- [ ] `lb://SERVICE-NAME/internal/...` URL 형태 사용
- [ ] `@LoadBalanced RestTemplate` 또는 `@FeignClient` 사용

---

*최종 확정일: 2026-03-23*
*변경 이력: account-service /internal/accounts/{id}/full|tags|zones 엔드포인트 추가,
 study-service /internal/studies/** 8개 엔드포인트 전체 완성*
*작성: StudyOlle MSA 전환 프로젝트*
