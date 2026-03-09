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
| `X-Internal-Service` | 요청하는 서비스 | `admin-service` | 내부 서비스 간 요청 식별자 |

> **규칙**: 각 서비스는 `X-Internal-Service` 헤더가 없는 `/internal/**` 요청을 403으로 차단한다.
> 외부에서 `/internal/**`로 직접 접근하는 것은 API Gateway에서 전면 차단한다.

---

## 3. 요청 유형별 상세 흐름

### 유형 1 - 공개 API (인증 불필요)

인증 없이 누구나 접근 가능한 엔드포인트.

```
[클라이언트]
     │
     │  POST /api/auth/login     { email, password }
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
```

**화이트리스트 경로 목록:**
```
POST /api/auth/login
POST /api/auth/signup
POST /api/auth/refresh
```

---

### 유형 2 - 일반 사용자 요청 (JWT 인증 필요)

로그인한 사용자가 각 서비스에 접근하는 일반적인 흐름.

```
[클라이언트]
     │
     │  GET /api/studies/
     │  Authorization: Bearer {accessToken}
     ▼
[API Gateway - JwtAuthenticationFilter]
     │  1. Authorization 헤더 존재 여부 확인
     │     없으면 → 401 Unauthorized 반환 (서비스까지 도달하지 않음)
     │
     │  2. Bearer {token} 파싱
     │
     │  3. JWT 서명 검증 (secret key 사용)
     │     실패하면 → 401 Unauthorized 반환
     │
     │  4. 토큰 만료 여부 확인
     │     만료되면 → 401 Unauthorized 반환
     │
     │  5. 검증 성공 → Claims에서 정보 추출 후 헤더 추가
     │     X-Account-Id: 123
     │     X-Account-Role: ROLE_USER
     │     X-Account-Nickname: 양균
     ▼
[study-service]
     │  - JWT 라이브러리 사용하지 않음
     │  - X-Account-Id 헤더만 꺼내서 사용자 식별
     │  - 비즈니스 로직 처리 후 응답 반환
     ▼
[클라이언트]
     응답: 스터디 목록 등 요청한 데이터
```

**각 서비스에서 X-Account-Id 사용 예시 (Java):**
```java
@GetMapping("/api/studies")
public ResponseEntity<?> getStudies(
        @RequestHeader("X-Account-Id") Long accountId) {
    // accountId로 바로 비즈니스 로직 처리
}
```

---

### 유형 3 - 관리자 요청 (JWT 인증 + ROLE_ADMIN 확인)

관리자 전용 기능에 접근하는 흐름. 두 단계 검증으로 보안 강화.

```
[관리자 브라우저]
     │
     │  GET /api/admin/members
     │  Authorization: Bearer {accessToken}
     ▼
[API Gateway - JwtAuthenticationFilter]
     │  1. JWT 검증 (유형 2와 동일)
     │     실패하면 → 401 반환
     ▼
[API Gateway - AdminRoleFilter]
     │  2. X-Account-Role 이 ROLE_ADMIN 인지 확인
     │     ROLE_USER 이면 → 403 Forbidden 반환
     │     ROLE_ADMIN 이면 → 통과
     ▼
[admin-service]
     │  3. X-Account-Role: ROLE_ADMIN 한 번 더 확인 (이중 검증)
     │     헤더가 없거나 ROLE_ADMIN 이 아니면 → 403 반환
     │  4. 관리자 비즈니스 로직 처리
     ▼
[관리자 브라우저]
     응답: 회원 목록 등 관리자 데이터
```

> **이중 검증을 하는 이유**: API Gateway가 유일한 방어선이 되면, Gateway 우회 시 모든 서비스가 무방비 상태가 된다.
> 각 서비스에서도 2차 검증을 수행함으로써 내부 네트워크에서의 비정상 접근도 차단한다.

---

### 유형 4 - 서비스 간 내부 요청

admin-service가 account-service나 study-service의 데이터를 조회해야 할 때.
JWT 없이 `X-Internal-Service` 헤더로 식별한다.

```
[admin-service]
     │
     │  GET /internal/accounts/123
     │  X-Internal-Service: admin-service
     │  (Authorization 헤더 없음)
     ▼
[account-service - InternalRequestFilter]
     │  1. /internal/** 경로 감지
     │  2. X-Internal-Service 헤더 존재 여부 확인
     │     헤더 없으면 → 403 Forbidden 반환
     │  3. 헤더 값이 허용된 서비스인지 확인
     │     (admin-service, study-service 등 내부 서비스 목록)
     │  4. 통과 → 내부 전용 데이터 반환
     ▼
[admin-service]
     응답: 요청한 계정 정보
```

**외부에서 /internal/** 직접 접근 시:**
```
[악의적인 클라이언트]
     │
     │  GET /internal/accounts/123
     ▼
[API Gateway]
     │  /internal/** 경로는 전면 차단
     │  → 403 Forbidden 반환
     │  (서비스까지 도달하지 않음)
```

---

## 4. API Gateway 라우팅 규칙

### 경로별 필터 적용 규칙

| 경로 패턴 | 대상 서비스 | 적용 필터 | 설명 |
|----------|-----------|---------|------|
| `POST /api/auth/**` | account-service | 없음 | 공개 API (로그인, 회원가입) |
| `GET /api/accounts/**` | account-service | JwtAuthenticationFilter | 내 정보 조회 등 인증 필요 |
| `POST /api/accounts/**` | account-service | JwtAuthenticationFilter | 내 정보 수정 등 인증 필요 |
| `/api/admin/**` | admin-service | JwtAuthenticationFilter + AdminRoleFilter | 관리자 전용 |
| `/api/studies/**` | study-service | JwtAuthenticationFilter | 스터디 관련 |
| `/api/events/**` | event-service | JwtAuthenticationFilter | 이벤트 관련 |
| `/internal/**` | - | 전면 차단 (403) | 외부 접근 불가 |

### application.yml 구조

```yaml
spring:
  cloud:
    gateway:
      routes:
        # 공개 API - JWT 검증 없이 통과
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

        # 관리자 전용 - JWT + ROLE_ADMIN 확인
        - id: admin-service
          uri: lb://ADMIN-SERVICE
          predicates:
            - Path=/api/admin/**
          filters:
            - JwtAuthenticationFilter
            - AdminRoleFilter

        # 스터디 서비스
        - id: study-service
          uri: lb://STUDY-SERVICE
          predicates:
            - Path=/api/studies/**
          filters:
            - JwtAuthenticationFilter

        # 내부 API 외부 접근 전면 차단
        - id: block-internal
          uri: no://op
          predicates:
            - Path=/internal/**
          filters:
            - SetStatus=403
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
  - `GET /api/accounts/me` - 내 정보 조회
  - `GET /internal/accounts/{id}` - 내부 전용 계정 조회

### api-gateway
- **역할**: 외부 진입점 + JWT 검증 + 라우팅
- **JWT 관련**: 검증만 담당 (발급은 account-service에서)
- **필터**:
  - `JwtAuthenticationFilter` - JWT 검증 + X-Account-* 헤더 추가
  - `AdminRoleFilter` - ROLE_ADMIN 여부 확인

### study-service / event-service / 기타 서비스
- **역할**: 각 도메인 비즈니스 로직
- **JWT 관련**: 직접 검증하지 않음 (JWT 라이브러리 의존성 없음)
- **인증 처리**: `X-Account-Id` 헤더만 꺼내서 사용

### admin-service
- **역할**: 플랫폼 관리자 전용 기능
- **인증 처리**:
  - 외부 요청: `X-Account-Role: ROLE_ADMIN` 헤더 2차 확인
  - 내부 요청: Feign Client로 각 서비스의 `/internal/**` 호출

---

## 6. 서비스 내부 API 설계 원칙

### URL 구조

```
# 외부 접근용 (JWT 인증 필요)
/api/{도메인}/**

# 서비스 간 내부 통신 전용 (외부 접근 불가)
/internal/{도메인}/**
```

### 내부 API 보호 - 각 서비스 구현 방법

각 서비스에서 `/internal/**` 경로를 보호하는 필터를 추가한다.

```java
// 각 서비스의 InternalRequestFilter.java
@Component
public class InternalRequestFilter implements HandlerInterceptor {

    private static final List<String> ALLOWED_SERVICES =
            List.of("admin-service", "study-service", "event-service");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        if (uri.startsWith("/internal/")) {
            String internalService = request.getHeader("X-Internal-Service");
            if (internalService == null || !ALLOWED_SERVICES.contains(internalService)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
        }
        return true;
    }
}
```

### Feign Client 내부 호출 예시 (admin-service에서 account-service 호출)

```java
// admin-service의 AccountFeignClient.java
@FeignClient(name = "account-service")
public interface AccountFeignClient {

    @GetMapping("/internal/accounts/{id}")
    AccountDto getAccount(@PathVariable Long id,
                          @RequestHeader("X-Internal-Service") String serviceName);
}

// 사용 시
AccountDto account = accountFeignClient.getAccount(id, "admin-service");
```

---

## 7. 필터 구조

### API Gateway 필터 처리 순서

```
요청 수신
    │
    ▼
JwtAuthenticationFilter
    │  - Authorization 헤더 파싱
    │  - JWT 서명/만료 검증
    │  - 실패 → 401 반환
    │  - 성공 → X-Account-Id, X-Account-Role, X-Account-Nickname 헤더 추가
    ▼
AdminRoleFilter (관리자 경로만 적용)
    │  - X-Account-Role == ROLE_ADMIN 확인
    │  - 실패 → 403 반환
    │  - 성공 → 통과
    ▼
각 서비스로 라우팅
```

### JWT Claims 구조 (account-service에서 발급)

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
| `role` | 권한 | → `X-Account-Role` 헤더 |
| `exp` | 만료 시각 | 만료 여부 검증 |

---

## 8. 신규 서비스 추가 시 체크리스트

새로운 마이크로서비스를 추가할 때 반드시 확인해야 할 항목.

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

### Feign Client (다른 서비스 호출 시)
- [ ] 호출할 서비스의 `/internal/**` 경로 사용
- [ ] `X-Internal-Service: {현재-서비스-이름}` 헤더 포함

---

*최종 확정일: 2026-03-09*
*작성: StudyOlle MSA 전환 프로젝트*
