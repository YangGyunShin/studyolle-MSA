# TroubleShooting 015 — 로그아웃 불가 + 로그인 유지 안됨

발생일: 2026-03-29
관련 서비스: api-gateway, frontend-service
증상: 로그아웃 버튼 클릭 시 쿠키가 삭제되지 않아 로그인 상태 유지됨

---

## 문제 1 — localhost:8090 직접 접속으로 인한 로그인 유지 불가

### 증상
로그인 후 홈 화면에 접속해도 비로그인 화면(랜딩 페이지)이 표시됨.
헤더에 "로그인", "회원가입" 버튼이 보임.

### 원인
브라우저 주소창이 `localhost:8090` (frontend-service 직접 접속)이었음.

```
[브라우저 localhost:8090]
    → frontend-service 직접 도달
    → api-gateway의 OptionalJwtFilter를 거치지 않음
    → 쿠키의 accessToken을 아무도 읽지 않음
    → X-Account-Id 헤더 없음
    → HomeController: accountId == null → 비로그인 화면 렌더링
```

반드시 `localhost:8080` (api-gateway)으로 접속해야 함.

```
[브라우저 localhost:8080]
    → api-gateway OptionalJwtFilter
    → 쿠키에서 accessToken 읽기 → JWT 검증
    → X-Account-Id 헤더 추가
    → frontend-service HomeController: accountId 존재 → 로그인 화면 렌더링
```

### 해결
항상 `http://localhost:8080`으로 접속한다.
`localhost:8090`은 개발/디버깅 목적 외에는 사용하지 않는다.

---

## 문제 2 — study-service InternalRequestFilter ALLOWED_SERVICES에 frontend-service 누락

### 증상
스터디 상세 페이지(`/study/{path}`) 접속 시 500 에러 발생.

```
403 on GET request for "lb://STUDY-SERVICE/internal/studies/testtest/page-data"
{"error": "Internal access only"}
```

### 원인
`study-service`의 `InternalRequestFilter.java`에서 허용 목록에 `frontend-service`가 빠져있었음.

```java
// 잘못된 설정 (frontend-service 누락)
private static final List<String> ALLOWED_SERVICES =
        List.of("admin-service", "event-service", "notification-service");
```

frontend-service가 `X-Internal-Service: frontend-service` 헤더를 정상적으로 보내도
허용 목록에 없어 403 반환.

### 해결
`study-service`의 `InternalRequestFilter.java` 수정:

```java
private static final List<String> ALLOWED_SERVICES =
        List.of("frontend-service", "admin-service", "event-service", "notification-service");
```

### 재발 방지
신규 서비스가 다른 서비스의 `/internal/**`를 호출해야 할 때,
**호출 대상 서비스의 `InternalRequestFilter.ALLOWED_SERVICES`에 반드시 추가**해야 함.

---

## 문제 3 — 로그아웃 버튼 클릭 시 아무 동작도 하지 않음 (handleLogout is not defined)

### 증상
로그아웃 버튼 클릭 시 아무 반응이 없음.
브라우저 콘솔:

```
SyntaxError: Identifier 'API_BASE' has already been declared  (line 600)
ReferenceError: handleLogout is not defined
```

### 원인
`index.html`과 `fragments.html`의 footer 양쪽에 `const API_BASE = ...`가 선언되어 있었음.

Thymeleaf가 페이지를 렌더링하면 두 선언이 하나의 HTML에 합쳐지는데,
`const`는 같은 스코프에서 중복 선언 시 `SyntaxError`를 발생시킨다.

```
[index.html]
    <script th:inline="javascript">
        const API_BASE = [[${apiBase}]];   ← 1번 선언
    </script>

[fragments.html footer - th:replace로 삽입됨]
    <script th:inline="javascript">
        const API_BASE = [[${apiBase}]];   ← 2번 선언 → SyntaxError!
    </script>
```

`SyntaxError`가 발생하면 해당 `<script>` 블록 전체가 실행되지 않는다.
`handleLogout` 함수가 footer 스크립트 안에 정의되어 있으므로 함수 자체가 등록되지 않음.
결과적으로 로그아웃 버튼 클릭 시 `handleLogout is not defined` 에러 발생.

### 해결
`const` → `var`로 변경. `var`는 중복 선언을 허용하므로 SyntaxError가 발생하지 않음.

`index.html`:
```javascript
// 변경 전
const API_BASE = [[${apiBase}]];

// 변경 후
var API_BASE = [[${apiBase}]];
```

`fragments.html` footer:
```javascript
// 변경 전
const API_BASE = [[${apiBase}]];

// 변경 후
var API_BASE = [[${apiBase}]];
```

### 재발 방지
Thymeleaf `th:replace`로 삽입되는 fragment(특히 footer)와
각 페이지가 동일한 전역 변수를 선언할 때는 반드시 `var`를 사용한다.

`const`/`let`은 블록 스코프이며 중복 선언이 불가능하므로,
여러 파일에서 같은 이름의 전역 변수를 선언할 경우 `var`가 안전하다.

---

## 문제 4 — 로그아웃 후 쿠키가 삭제되지 않음

### 증상
로그아웃 버튼 클릭 후 홈으로 이동해도 여전히 로그인 상태로 표시됨.
브라우저 개발자 도구 → Application → Cookies에서 `accessToken` 쿠키가 남아있음.

### 원인 분석 과정
처음에는 `handleLogout` 함수에서 JS로 쿠키를 삭제하려 했음:

```javascript
document.cookie = 'accessToken=; path=/; max-age=0; SameSite=Strict';
```

이 방식이 작동하지 않은 이유는 **문제 3의 SyntaxError** 때문에
`handleLogout` 함수 자체가 정의되지 않았기 때문이었음.

즉, 문제 3(const 중복 선언)을 해결하자 문제 4도 함께 해결됨.

### 최종 해결 방법
두 가지 방식을 함께 적용:

**방법 1 — JS에서 쿠키 삭제 (fragments.html)**

```javascript
function handleLogout(e) {
    e.preventDefault();
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = '/logout';   // 서버 사이드 쿠키 삭제로 위임
}
```

**방법 2 — 서버 사이드 쿠키 만료 (HomeController.java)**

JS의 `document.cookie` 삭제는 브라우저마다 동작이 불안정할 수 있음.
서버가 `Set-Cookie: accessToken=; max-age=0` 응답 헤더를 보내면
브라우저가 반드시 해당 쿠키를 삭제하므로 더 확실함.

```java
@GetMapping("/logout")
public String logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("accessToken", "");
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
    return "redirect:/";
}
```

---

## 핵심 교훈 요약

| # | 문제 | 원인 | 해결 |
|---|------|------|------|
| 1 | 로그인 유지 안됨 | localhost:8090 직접 접속 | localhost:8080으로 접속 |
| 2 | 스터디 페이지 403 | InternalRequestFilter 허용목록 누락 | frontend-service 추가 |
| 3 | 로그아웃 버튼 무반응 | const API_BASE 중복 선언 → SyntaxError | var로 변경 |
| 4 | 로그아웃 후 쿠키 잔존 | 문제 3으로 인해 handleLogout 미정의 | 문제 3 해결 + 서버 사이드 쿠키 삭제 |

### 디버깅 방법 — 브라우저 콘솔 확인이 핵심

JS 관련 문제는 브라우저 개발자 도구 Console 탭에서 바로 확인 가능.
`SyntaxError`, `ReferenceError` 등 명확한 에러 메시지를 먼저 확인하는 습관이 중요함.

```
F12 → Console 탭 → 에러 메시지 확인
```

특히 `SyntaxError`가 있으면 해당 스크립트 블록 전체가 실행되지 않으므로
"왜 함수가 없지?" 같은 문제의 근본 원인일 가능성이 높다.
