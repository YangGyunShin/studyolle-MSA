# 🔧 트러블슈팅: 이메일 인증 링크 오류 3가지

## 📌 상황 요약

회원가입 후 이메일 인증 링크를 클릭했을 때 여러 문제가 연속으로 발생했다.
문제는 총 세 가지였으며 모두 `SignUpService.java`의 인증 링크 생성 방식과 관련이 있었다.

---

## 📋 1번 문제: 인증 링크가 404 Whitelabel Error Page를 반환

### 증상

이메일 인증 링크를 클릭하면 아래와 같은 페이지가 나왔다.

```
Whitelabel Error Page
There was an unexpected error (type=Not Found, status=404).
```

주소창을 보면: `localhost:8090/api/auth/check-email-token?token=...`

포트가 **8090(frontend-service)** 이었다.

### 원인

`account-service`의 `application.yml`의 `app.host` 값이 잘못 설정되어 있었다.

```yaml
# ❌ 수정 전
app:
  host: http://localhost:8090  # frontend-service 주소
```

`SignUpService.java`는 이 `app.host`를 사용해서 인증 링크를 만든다.

```java
context.setVariable("link", "/api/auth/check-email-token?token=..." + "&email=...");
context.setVariable("host", appHost);  // ← app.host 값이 들어감
```

최종 링크: `http://localhost:8090` + `/api/auth/check-email-token?...`

`8090`은 frontend-service다. frontend-service에는 `/api/auth/**` 경로가 없으므로 404가 났다.

### 해결

인증 링크는 api-gateway(8080)를 통해야 한다.

```yaml
# ✅ 수정 후
app:
  host: http://localhost:8080  # api-gateway 주소
```

---

## 📋 2번 문제: email 파라미터 누락으로 500 오류

### 증상

`localhost:8080/api/auth/check-email-token?token=...&email=...` 링크를 복사해서
브라우저 주소창에 붙여넣으면 아래 오류가 났다.

```
MissingServletRequestParameterException:
Required request parameter 'email' for method parameter type String is not present
```

### 원인

이것은 **콘솔 로그에서 URL을 잘못 복사했기 때문**이다.

IntelliJ 콘솔에 찍히는 HTML 로그를 보면 이렇게 나온다.

```html
<a href="http://localhost:8080/api/auth/check-email-token?token=abc&amp;email=test@test.com">
```

`&`이 `&amp;`로 출력되어 있다.
이것은 Thymeleaf가 HTML 속성값 안에서 `&`를 안전하게 표현하기 위해 `&amp;`로 인코딩한 것이다.

이 텍스트를 그대로 복사해서 브라우저 주소창에 붙여넣으면
`email` 파라미터가 아닌 `amp;email`이라는 파라미터로 인식되어 `email`을 찾지 못한다.

**브라우저에서 `<a href="...">`를 클릭하면** 브라우저가 `&amp;`를 `&`로 자동 변환하므로 문제없다.
하지만 **로그에서 텍스트로 복사**하면 `&amp;`가 그대로 URL에 포함된다.

### 해결 방법 1 — 링크를 복사할 때 올바른 줄을 사용

`simple-link.html` 템플릿에 `th:utext`를 사용해 인코딩 없이 출력하는 줄을 추가했다.

```html
<!-- th:text는 &를 &amp;로 인코딩 → 콘솔 복사 시 문제 발생 -->
<!-- th:utext는 raw 문자열 그대로 출력 → &가 &로 나옴 -->
<p>직접 복사 링크: <span th:utext="${host + link}"></span></p>
```

콘솔에서 "직접 복사 링크:" 줄의 URL을 복사해서 사용하면 된다.

### 해결 방법 2 — 링크를 클릭하면 됨

실제 이메일에서는 `<a>` 태그를 클릭하면 브라우저가 자동으로 처리한다.
콘솔 로그에서 직접 복사할 때만 이 문제가 생긴다.

---

## 📋 3번 문제: 인증 링크가 check-email-token.html 대신 JSON을 반환

### 증상

`http://localhost:8080/api/auth/check-email-token?token=...&email=...` 를
브라우저 주소창에 입력하면 아래처럼 JSON이 그대로 화면에 표시됐다.

```json
{"success":true,"message":"이메일 인증이 완료되었습니다. 로그인해주세요."}
```

### 원인

이것은 **URL을 잘못 사용한 것**이다.

`/api/auth/check-email-token`은 account-service의 **REST API 엔드포인트**다.
브라우저 주소창에 이 URL을 입력하면 서버는 JSON을 반환한다.

올바른 흐름은 아래와 같다.

```
① 브라우저: http://localhost:8080/check-email-token?token=...&email=...
                                  ↑ /api/auth가 아닌 /check-email-token
② api-gateway: /check-email-token 경로를 frontend-service로 라우팅
③ frontend-service: check-email-token.html 반환
④ 브라우저에서 JS 실행: fetch('/api/auth/check-email-token?...') 호출
⑤ account-service: 토큰 검증 → 성공 응답
⑥ 브라우저: 성공 화면 표시
```

### 해결

`SignUpService.java`에서 링크 경로를 수정한다.

```java
// ❌ 수정 전 — account-service REST API를 직접 가리킴
context.setVariable("link", "/api/auth/check-email-token?token=..." + "&email=...");

// ✅ 수정 후 — frontend-service HTML 페이지를 가리킴
context.setVariable("link", "/check-email-token?token=..." + "&email=...");
```

그리고 api-gateway `application.yml`에 `/check-email-token` 라우팅을 추가한다.

```yaml
- id: frontend-service
  uri: lb://FRONTEND-SERVICE
  predicates:
    - Path=/**   # 모든 경로를 frontend-service로 (단, /api/** 라우트가 위에 먼저 선언되어야 함)
```

---

## 🔎 이메일 인증 흐름 전체 요약

```
회원가입 성공
  → account-service: 인증 이메일 생성 (host + link로 URL 조립)
  → ConsoleEmailService: 콘솔에 출력 (local 프로파일)

콘솔에서 확인
  → "직접 복사 링크:" 줄의 URL 복사
  → 반드시 http://localhost:8080/check-email-token?... 형태여야 함

브라우저 주소창에 붙여넣기
  → api-gateway(8080) → frontend-service → check-email-token.html 로드
  → JS가 자동으로 /api/auth/check-email-token 호출
  → 성공 화면 표시 → 로그인 페이지로 이동
```

---

## 📎 관련 파일

- `account-service/src/main/resources/application.yml` (`app.host` 설정)
- `account-service/src/main/java/com/studyolle/account/service/SignUpService.java` (link 경로)
- `account-service/src/main/resources/templates/mail/simple-link.html` (`th:utext` 추가)
- `infra/api-gateway/src/main/resources/application.yml` (라우팅 추가)

---

## 📅 발생일

2026-03-21
