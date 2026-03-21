# 🔧 트러블슈팅: localStorage JWT로 인한 로그인 상태 유지 안 됨

## 📌 상황 요약

로그인에 성공했지만 홈 페이지(/)로 이동하면 여전히 비로그인 상태로 표시됐다.
우측 상단에 닉네임 대신 "로그인 / 회원가입" 버튼이 그대로 남아있었다.

---

## 📋 증상

```
① 로그인 페이지에서 이메일/비밀번호 입력 후 "로그인" 클릭
② 잠시 후 홈 페이지(/)로 이동됨
③ 화면 우측 상단: "로그인 | 회원가입" 버튼이 그대로 표시됨
④ 로그인이 된 상태가 아님
```

---

## 🔍 원인 분석

### localStorage란?

localStorage는 브라우저 안에 데이터를 저장하는 공간이다.
**JavaScript 코드만 접근할 수 있다.**

로그인이 성공하면 `login.html`의 JS가 이렇게 동작했다.

```javascript
// 로그인 성공 → JWT를 localStorage에 저장
localStorage.setItem('accessToken', result.data.accessToken);
localStorage.setItem('refreshToken', result.data.refreshToken);

// 홈으로 이동
window.location.href = '/';
```

---

### 문제: 브라우저의 페이지 이동은 localStorage를 포함하지 않는다

`window.location.href = '/'`로 홈 페이지로 이동할 때
브라우저는 `GET /` 요청을 서버에 보낸다.

**이 요청에는 localStorage가 포함되지 않는다.**

localStorage는 오직 JavaScript의 `fetch()` 호출에서만 수동으로 헤더에 붙일 수 있다.
브라우저가 페이지 이동 요청을 보낼 때는 자동으로 포함되지 않는다.

---

### 결과적으로 일어나는 일

```
① 로그인 성공 → localStorage에 JWT 저장 → window.location.href = '/'

② 브라우저가 GET / 요청 전송
   ↓ Authorization 헤더 없음 (localStorage는 브라우저가 자동으로 안 붙임)
   ↓
③ api-gateway 수신
   ↓ Authorization 헤더 없음 → JWT 검증 안 함 → X-Account-Id 헤더 추가 안 함
   ↓
④ HomeController 수신
   ↓ X-Account-Id 헤더 없음 → accountId == null
   ↓
⑤ Thymeleaf 렌더링
   th:if="${account == null}" → 로그인/회원가입 버튼 렌더링
   (로그인 상태 화면이 아닌 비로그인 화면을 반환)
```

localStorage에 토큰이 있어도 서버는 알 수 없고, 서버는 항상 비로그인 상태로 렌더링한다.

---

### localStorage vs 쿠키의 핵심 차이

| 저장소 | 브라우저 페이지 요청 시 자동 포함 | JS fetch() 에서 수동 추가 가능 |
|--------|------------------------------|-------------------------------|
| localStorage | ❌ 자동 포함 안 됨 | ✅ 가능 |
| 쿠키 | ✅ 자동으로 포함됨 | ✅ 가능 |

쿠키는 브라우저가 서버에 요청을 보낼 때 **자동으로 포함**된다.
따라서 페이지 이동 시에도 서버가 쿠키에서 JWT를 꺼내 검증할 수 있다.

---

## ✅ 해결 방법

### 1. login.html: 로그인 성공 시 쿠키에도 저장

localStorage에 저장하는 것에 더해 쿠키에도 저장한다.

```javascript
if (response.ok) {
    const accessToken  = result.data.accessToken;
    const refreshToken = result.data.refreshToken;

    // localStorage: JS fetch() 호출 시 Authorization 헤더에 사용
    localStorage.setItem('accessToken',  accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    // 쿠키: 브라우저 페이지 요청 시 자동으로 포함됨
    // path=/ → 모든 경로에서 전송
    // max-age=1800 → 30분 유효 (accessToken 만료 시간과 동일)
    document.cookie = 'accessToken=' + accessToken
        + '; path=/'
        + '; max-age=1800'
        + '; SameSite=Strict';

    window.location.href = '/';
}
```

### 2. JwtAuthenticationFilter.java: 쿠키에서도 JWT 읽기

기존 필터는 `Authorization` 헤더에서만 JWT를 읽는다.
쿠키에서도 읽도록 수정한다.

```java
@Override
public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {

        // 1. Authorization 헤더에서 JWT 추출 시도 (기존 방식 — fetch() 호출 시)
        String token = null;
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. Authorization 헤더가 없으면 쿠키에서 accessToken 추출 시도
        //    (브라우저 페이지 이동 시 — window.location.href 등)
        if (token == null) {
            HttpCookie cookie = exchange.getRequest()
                    .getCookies()
                    .getFirst("accessToken");
            if (cookie != null) {
                token = cookie.getValue();
            }
        }

        // 헤더에도 쿠키에도 없으면 401
        if (token == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 이후 기존 JWT 검증 로직 그대로...
    };
}
```

`import org.springframework.http.HttpCookie;` 추가 필요.

---

### 수정 후 흐름

```
① 로그인 성공
   → localStorage에 accessToken 저장 (fetch() 호출용)
   → 쿠키에도 accessToken 저장 (페이지 이동용)
   → window.location.href = '/'

② 브라우저가 GET / 요청 전송
   ↓ 쿠키가 자동으로 포함됨: Cookie: accessToken=eyJ...
   ↓
③ api-gateway JwtAuthenticationFilter
   ↓ Authorization 헤더 없음 → 쿠키에서 accessToken 읽기
   ↓ JWT 검증 성공 → X-Account-Id: 123 헤더 추가
   ↓
④ HomeController
   ↓ accountId = 123 → 로그인 상태
   ↓
⑤ Thymeleaf 렌더링
   th:if="${account != null}" → 닉네임, 새 스터디 버튼 등 로그인 화면 렌더링
```

---

## 🔎 핵심 개념 정리

```
[왜 두 곳에 모두 저장하는가?]

쿠키만 사용하면:
  - 페이지 이동(브라우저 요청)은 쿠키가 자동 포함 → OK
  - JS fetch() 호출 시 쿠키도 자동 포함되지만
    SameSite=Strict 설정이면 크로스 오리진 요청에서 포함 안 될 수 있음

localStorage만 사용하면:
  - JS fetch() 에서 수동으로 Authorization 헤더에 붙임 → OK
  - 페이지 이동(브라우저 요청)에는 자동 포함 안 됨 → 서버가 비로그인으로 처리

둘 다 저장하면:
  - 페이지 이동 → 쿠키로 인증
  - JS fetch() → localStorage로 Authorization 헤더 구성
  → 모든 상황에서 인증 가능
```

---

## 📎 관련 파일

- `frontend-service/src/main/resources/templates/login.html`
- `infra/api-gateway/src/main/java/com/studyolle/gateway/filter/JwtAuthenticationFilter.java`

---

## 📅 발생일

2026-03-21
