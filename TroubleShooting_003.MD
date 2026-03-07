# 🔧 트러블슈팅: 로그인 후 status 999 에러 + /error?continue 리다이렉트

## 📌 상황 요약

로그인 폼에서 로그인 버튼을 누르면, 대시보드가 나오지 않고
**브라우저에 JSON 에러 응답**이 표시되었다.

```json
{"timestamp":"2026-02-17T14:35:35.559+00:00","status":999,"error":"None","message":"No message available"}
```

URL 주소창에는 `http://localhost:8080/error?continue`가 표시되었다.

서버 로그에는 **에러 스택트레이스가 전혀 없고**, SQL 쿼리만 정상 실행된 것처럼 보였다.

---

## 🔍 디버깅 과정

### STEP 1: status 999는 무엇인가?

HTTP 표준 상태 코드에 999는 존재하지 않는다.
이건 **Spring Boot가 자체적으로 반환하는 비표준 코드**로,
다음과 같은 의미를 갖는다:

> **"에러 페이지 자체를 렌더링하는 데 실패했다"**

일반적인 에러 흐름과 비교하면:

```
[정상적인 에러 처리]
  요청 → 컨트롤러 → 예외 발생 → /error 페이지 렌더링 → 사용자에게 에러 안내

[status 999가 발생하는 경우]
  요청 → 컨트롤러 → 예외 발생 → /error 접근 시도 → /error도 실패! → 999 반환
```

즉, "에러가 발생했는데, 에러를 보여주는 것조차 실패한" 상황이다.

---

### STEP 2: /error?continue의 ?continue는 어디서 온 것인가?

`?continue` 파라미터는 Spring Security의 `RequestCacheAwareFilter`가 붙이는 것이다.

Spring Security는 **인증이 필요한 페이지에 비인증 상태로 접근하면**,
그 원래 요청 URL을 세션에 저장(캐시)해둔다.
나중에 로그인에 성공하면, 저장해둔 URL로 자동 리다이렉트한다.

이때 Spring Security는 원래 URL에 `?continue` 파라미터를 추가하여
"이건 RequestCache에서 복원된 요청이다"라는 것을 표시한다.

```
[RequestCache 동작 흐름]

1. 비인증 상태에서 /error 접근 시도
2. Spring Security: "인증 필요!" → /login 으로 리다이렉트
3. 동시에 원래 요청("/error")을 세션에 저장
4. 사용자가 로그인 성공
5. Spring Security: "아까 /error에 가려고 했었지?" → /error?continue 로 리다이렉트
6. /error가 여전히 permitAll()에 없으면 → 또 차단 → 무한 루프 → status 999
```

---

### STEP 3: 왜 /error가 차단되었는가? — SecurityConfig 분석

프로젝트의 `SecurityConfig.java`를 확인해보자:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/",
        "/login",
        "/sign-up",
        "/check-email-token",
        // ... 기타 공개 URL ...
        "/search/study"
    ).permitAll()

    .anyRequest().authenticated()  // ← 나머지 요청은 모두 인증 필요
);
```

`/error`가 `permitAll()` 목록에 **포함되어 있지 않다.**
따라서 `.anyRequest().authenticated()` 규칙에 의해 인증이 필요한 URL로 분류된다.

Spring Boot의 `BasicErrorController`는 `/error` 경로에서 에러 페이지를 처리하는데,
이 경로가 인증을 요구하면:

- **비인증 상태에서 에러 발생** → /error 접근 → 인증 필요 → /login 리다이렉트 → 무한 루프
- **인증 상태에서 에러 발생** → RequestCache에 /error가 남아있으면 → /error?continue → 999

---

### STEP 4: 왜 서버 로그에 에러 스택트레이스가 안 보였는가?

이것이 이 문제의 가장 까다로운 부분이다.

로그를 보면 SQL 쿼리는 정상 실행되었고, Thymeleaf 경고(WARN)만 있었다.
실제로 `index-after-login.html`은 **정상적으로 렌더링에 성공**했다.

그런데 왜 /error로 리다이렉트된 걸까?

원인은 **RequestCache에 이전 실패 요청이 남아있었기 때문**이다:

```
[시나리오 재현]

1. 브라우저에서 비인증 상태로 어떤 보호된 URL에 접근 (또는 이전 세션의 잔여 요청)
2. Spring Security가 /login으로 리다이렉트하면서 원래 URL을 RequestCache에 저장
3. 사용자가 로그인 성공
4. Spring Security가 "/"가 아닌, RequestCache에 저장된 URL로 리다이렉트
5. 그 URL이 /error 관련이었다면 → /error?continue → 999
```

즉, `index-after-login.html` 렌더링 자체는 문제가 없었지만,
**이전에 캐시된 /error 요청** 때문에 로그인 후 /error?continue로 보내진 것이다.

이런 이유로 서버 로그에 현재 요청의 에러는 찍히지 않았던 것이다.
에러는 "지금" 발생한 게 아니라 "이전에" 발생한 것의 잔재였기 때문.

---

### STEP 5: 추가 함정 — ExceptionAdvice의 에러 삼킴(Swallowing)

만약 실제로 컨트롤러에서 예외가 발생하는 경우에도 status 999가 나올 수 있다.

프로젝트에는 `ExceptionAdvice`라는 `@ControllerAdvice`가 있다.
이 클래스의 `@ExceptionHandler`가 예외를 잡아서 "error" 뷰를 반환하는데,
만약 이 메서드 자체에서 에러가 발생하면:

```
[에러 삼킴(Swallowing) 시나리오]

1. MainController.home() → RuntimeException 발생
2. ExceptionAdvice.handleRuntimeException() 호출
3. ExceptionHandler 메서드 내부에서도 에러 발생!
   (예: Account 파라미터 주입 실패 — TroubleShooting_001 참조)
4. 두 번째 에러가 첫 번째 에러를 "삼킴" → 원래 스택트레이스가 로그에 안 찍힘
5. Spring Boot → /error 포워드 → /error 차단 → 999
```

이 프로젝트의 ExceptionAdvice는 이미 SecurityContextHolder 방식으로 수정되어
이 문제는 해결되었지만 (TroubleShooting_001 참조),
비슷한 패턴이 다른 곳에서 재발할 수 있으므로 알아두면 좋다.

---

## 💊 수정 방법

### SecurityConfig.java에 /error 추가

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(
        "/",
        "/login",
        "/sign-up",
        "/check-email-token",
        "/email-login",
        "/check-email-login",
        "/login-link",
        "/index",
        "/css/**", "/images/**", "/js/**",
        "/login-by-email",
        "/search/study",
        "/error",  // ← 추가: Spring Boot 에러 페이지 접근 허용

        "/check-email",
        "/resend-confirm-email"
    ).permitAll()

    .requestMatchers(HttpMethod.GET, "/profile/*").permitAll()
    .anyRequest().authenticated()
);
```

---

## ❓ /error를 permitAll()로 여는 것이 보안상 문제되지 않는가?

문제없다. 이유:

1. **`/error` 페이지는 "에러가 발생했다"는 안내만 보여주는 페이지**이다.
   민감한 사용자 데이터나 시스템 내부 정보를 노출하지 않는다.

2. **스택트레이스 노출은 별도 설정으로 제어**된다.
   `server.error.include-stacktrace=never` (기본값)이면
   에러 페이지에 스택트레이스가 포함되지 않는다.

3. **Spring Boot 공식 문서에서도 /error는 permitAll()을 권장**한다.
   에러 페이지에 접근할 수 없으면 사용자는 아무런 안내도 받지 못하고
   status 999 같은 의미 없는 응답만 보게 된다.

4. **에러 페이지를 차단하는 것은 보안이 아니라 UX 문제**이다.
   공격자가 /error에 접근해도 얻을 수 있는 정보가 없다.
   오히려 정상 사용자가 에러 안내를 받지 못하는 피해가 더 크다.

**비유하자면:**
화재 경보기(에러 페이지)에 자물쇠를 걸어두는 것과 같다.
화재(에러)가 발생했을 때 경보기가 울려야 하는데,
자물쇠 때문에 경보기 자체에 접근할 수 없어서 아무도 화재를 모르게 되는 상황이다.

---

## 🧠 핵심 교훈

### 1. status 999는 "에러의 에러"를 의미한다

에러 페이지 자체가 접근 불가능하면 발생하는 비표준 코드이다.
이 코드를 만나면 먼저 `/error` 경로의 접근 권한을 확인하자.

### 2. RequestCache의 존재를 인지하라

Spring Security는 인증 전 요청을 캐시해두었다가 로그인 성공 후 복원한다.
이 메커니즘 때문에 "로그인 성공 → 예상치 못한 URL로 이동"하는 현상이 발생할 수 있다.

### 3. 서버 로그에 에러가 없다고 에러가 없는 것이 아니다

다음 상황에서 에러가 로그에 안 찍힐 수 있다:
- **에러 핸들러 자체가 에러** → 원래 에러가 삼켜짐 (TroubleShooting_001)
- **이전 세션의 잔여 요청** → 현재 요청은 정상이지만 캐시된 요청이 문제
- **/error가 차단됨** → Spring Boot가 에러 로깅을 완료하기 전에 Security가 차단

### 4. SecurityConfig의 permitAll()에는 인프라 경로도 포함해야 한다

비즈니스 URL뿐 아니라, Spring Boot가 내부적으로 사용하는 경로도 고려하자:
- `/error` — 에러 페이지
- `/actuator/health` — 헬스체크 (사용 시)
- 정적 리소스 (`/css/**`, `/js/**` 등) — 이미 설정됨

---

## 📁 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `SecurityConfig.java` | permitAll() 목록에 "/error" 추가 |

---

## 🔗 관련 트러블슈팅

- **TroubleShooting_001**: ExceptionAdvice 파라미터 주입 실패 + MainController null 에러
  → @ExceptionHandler에서 Account 파라미터 주입이 불가능한 이유와 해결 방법
  → ExceptionAdvice가 에러를 삼키면 status 999로 이어질 수 있음 (STEP 5 참조)
