# 🔧 트러블슈팅: api-gateway 라우팅 미설정으로 인한 CSS 누락 및 페이지 오류

## 📌 상황 요약

이메일 인증 링크 문제를 해결하고 로그인 페이지에 접근했더니
화면이 CSS 없이 날것의 HTML로 표시됐다.
또한 api-gateway를 통해 특정 페이지에 접근하면 404가 발생하기도 했다.

---

## 📋 증상

로그인 페이지(`/login`)에 접근하면 아래처럼 스타일이 전혀 없는 상태로 표시됐다.

```
StudyOlle
로그인
이메일 또는 닉네임
[입력칸]
패스워드
[입력칸]
[로그인 버튼]
```

(정상이라면 배경 이미지, 카드 디자인, 색상 등이 있어야 한다)

---

## 🔍 원인 분석

### 브라우저가 페이지를 로드하는 순서

브라우저가 `/login` 페이지를 로드할 때 단순히 HTML 한 파일만 받는 게 아니다.
HTML 안에 포함된 CSS, JS, 이미지 파일들도 별도로 요청한다.

```
① GET /login              → HTML 반환
② GET /css/auth-style.css → CSS 반환
③ GET /js/glass-validation.js → JS 반환
```

`login.html` 안에 이런 줄이 있다.

```html
<link rel="stylesheet" href="/css/auth-style.css">
```

브라우저는 이 CSS를 현재 페이지와 같은 서버(`http://localhost:8080`)에서 요청한다.
즉 `http://localhost:8080/css/auth-style.css`로 요청을 보낸다.

---

### api-gateway 라우팅에 정적 파일 경로가 없었다

기존 api-gateway 라우팅:

```yaml
# ❌ 수정 전
routes:
  - id: account-service-public
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/auth/**

  - id: frontend-service-pages
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/check-email-token,/check-email,/sign-up,/login,/email-login
```

`/css/auth-style.css`는 이 목록 어디에도 없다.
api-gateway가 이 경로를 처리할 규칙을 모르니 404를 반환하고, CSS가 로드되지 않는다.

---

### 해결: Path=/** 로 변경

api-gateway가 `/api/**` 이외의 모든 요청을 frontend-service로 보내도록 변경한다.

```yaml
# ✅ 수정 후
routes:
  # /api/auth/** 는 account-service로 (먼저 선언)
  - id: account-service-public
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/auth/**

  # /api/accounts/** 는 account-service로 (먼저 선언)
  - id: account-service-private
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/accounts/**
    filters:
      - JwtAuthenticationFilter

  # 위에서 매칭되지 않은 나머지 모든 요청 → frontend-service로
  # /css/**, /js/**, /images/**, /login, /sign-up 등 모두 포함
  - id: frontend-service
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/**
```

**중요:** Gateway는 라우팅 규칙을 **선언된 순서대로** 매칭한다.
`/api/auth/**`가 위에 있으면 `/api/auth/login` 같은 요청은 account-service로 가고,
그 외 나머지(`/login`, `/css/auth-style.css` 등)는 `/**`에 걸려 frontend-service로 간다.

---

## 🔎 정적 파일 404가 났을 때 확인할 것

```
브라우저 개발자 도구 (F12) → Network 탭

페이지 로드 후 빨간색(404)으로 표시된 요청을 찾는다.
  /css/... → CSS 누락 → 스타일 없음
  /js/...  → JS 누락  → 버튼 동작 안 함
  /images/... → 이미지 누락

이런 요청들이 api-gateway를 통해 frontend-service로 라우팅되고 있는지 확인한다.
```

---

## 📎 관련 파일

- `infra/api-gateway/src/main/resources/application.yml`

---

## 📅 발생일

2026-03-21
