# 🔧 트러블슈팅: CORS 설정 누락으로 인한 회원가입 fetch 실패

## 📌 상황 요약

모든 서비스(Eureka, Config, API Gateway, account-service, study-service, frontend-service)를
정상 기동한 뒤 `http://localhost:8090/sign-up` 페이지에서 회원가입을 시도했다.

닉네임·이메일·비밀번호를 입력하고 "가입하기" 버튼을 눌렀더니
폼 상단에 아래 메시지가 표시되며 회원가입이 실패했다.

```
서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.
```

서버(account-service, api-gateway) 로그에는 **아무것도 찍히지 않았다.**

---

## 📋 증상 요약

| 항목 | 상태 |
|------|------|
| 브라우저 화면 오류 메시지 | "서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요." |
| api-gateway 로그 | 아무것도 없음 |
| account-service 로그 | 아무것도 없음 |
| 브라우저 개발자 도구 콘솔 | `Access to fetch ... has been blocked by CORS policy` |

---

## 🔍 원인 분석

### 핵심 개념: CORS와 Preflight 요청

**CORS(Cross-Origin Resource Sharing)** 란 브라우저가 "출처가 다른 서버에 요청을 보낼 때" 적용하는 보안 정책이다.

**출처(Origin)** 란 `프로토콜 + 도메인 + 포트` 세 가지의 조합이다.

```
http://localhost:8090  ← frontend-service (브라우저가 실행 중인 곳)
http://localhost:8080  ← api-gateway      (fetch 목적지)
```

포트가 8090 ≠ 8080으로 다르므로 브라우저는 이것을 **다른 출처**로 판단한다.

---

### Preflight 요청이란?

브라우저는 다른 출처로 요청을 보내기 전에 "이 서버가 우리 요청을 허용하는가?"를 먼저 확인한다.
이 사전 확인 요청을 **Preflight**라고 한다.

```
[브라우저의 실제 동작 순서]

1단계: OPTIONS /api/auth/signup 요청 전송 (Preflight)
       헤더: Origin: http://localhost:8090
             Access-Control-Request-Method: POST

2단계: 서버 응답 확인
       ✅ 허용 응답이 오면  → 실제 POST 요청 전송
       ❌ 허용 응답이 없으면 → 실제 POST 요청 차단 (서버에 도달하지 않음)

3단계 (허용 시): POST /api/auth/signup 실제 요청 전송
```

**중요한 점:** Preflight가 실패하면 실제 POST 요청은 **브라우저 단계에서 막힌다.**
서버까지 도달하지 않으므로 api-gateway나 account-service 로그에 아무것도 찍히지 않는다.

이것이 "서버 로그가 없다 = 서버에 요청이 도달하지 않았다 = CORS 문제일 가능성이 높다"는 판단의 근거다.

---

### 왜 서버 로그가 없으면 CORS를 의심하는가?

```
[서버 로그 유무로 문제 레이어 파악하기]

서버 로그가 전혀 없다
  ├── 요청이 서버에 도달하지 않았다는 뜻
  ├── 가능한 원인: CORS 차단, 네트워크 문제, 서비스 미기동, 포트 오류
  └── 브라우저 개발자 도구 → Network 탭에서 OPTIONS 요청 확인

서버 로그가 있다
  ├── 요청이 서버에 도달했다는 뜻
  ├── CORS는 이미 통과된 것
  └── 서버 내부 오류를 탐색 (Caused by: 추적)
```

---

### 이번 프로젝트의 구체적 원인

api-gateway의 `application.yml`에 CORS 설정이 없었다.

```yaml
# ❌ 수정 전 - CORS 설정 없음
spring:
  cloud:
    gateway:
      routes:
        - id: account-service-public
          uri: lb://ACCOUNT-SERVICE
          predicates:
            - Path=/api/auth/**
```

브라우저가 `http://localhost:8090`에서 `http://localhost:8080`으로 fetch를 보내면,
api-gateway가 OPTIONS(Preflight) 요청에 CORS 허용 헤더를 응답하지 않으므로
브라우저가 실제 POST 요청을 차단한다.

---

## ✅ 해결 방법

api-gateway의 `application.yml`에 `globalcors` 설정을 추가한다.

```yaml
# ✅ 수정 후 - CORS 설정 추가
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:8090"   # frontend-service 주소
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS                   # Preflight 요청 허용 필수
            allowedHeaders:
              - "*"
            allowCredentials: true
      routes:
        - id: account-service-public
          uri: lb://ACCOUNT-SERVICE
          predicates:
            - Path=/api/auth/**
```

설정 추가 후 api-gateway를 재시작하면 Preflight가 통과되고,
실제 POST 요청이 account-service까지 정상적으로 전달된다.

---

## 🔎 브라우저 개발자 도구에서 CORS 오류 확인하는 법

1. 브라우저에서 `F12` → **Network** 탭 클릭
2. 회원가입 버튼 클릭
3. 요청 목록에서 `signup` 또는 `auth` 관련 항목을 찾는다
4. `OPTIONS` 메서드 요청이 보이고 상태 코드가 `403` 또는 빨간색이면 CORS 문제
5. 콘솔(Console) 탭에 `has been blocked by CORS policy` 메시지가 있으면 확실

---

## 📎 관련 파일

- `infra/api-gateway/src/main/resources/application.yml`

---

## 📅 발생일

2026-03-18
