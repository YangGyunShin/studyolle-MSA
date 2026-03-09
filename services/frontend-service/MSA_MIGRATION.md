# 모노리틱 → MSA 전환 변경 사항 정리

> 대상 범위: 인증(Auth) 및 계정(Account) 관련 기능 — 프론트엔드 중심  
> 작성일: 2026-03-09  
> 작성자: StudyOlle MSA 전환 프로젝트

---

## 목차

1. [전체 구조 변화](#1-전체-구조-변화)
2. [인증 방식 변화 (세션 → JWT)](#2-인증-방식-변화-세션--jwt)
3. [프론트엔드 렌더링 패러다임 전환](#3-프론트엔드-렌더링-패러다임-전환)
4. [Thymeleaf 사용 방식의 변화](#4-thymeleaf-사용-방식의-변화)
5. [에러 처리 패턴의 변화](#5-에러-처리-패턴의-변화)
6. [페이지 전환 방식의 변화](#6-페이지-전환-방식의-변화)
7. [AuthPageController의 역할](#7-authpagecontroller의-역할)
8. [페이지별 상세 변경 사항](#8-페이지별-상세-변경-사항)
9. [이메일 인증 흐름 변화](#9-이메일-인증-흐름-변화)
10. [정적 자원(CSS/JS) 변경](#10-정적-자원cssjs-변경)
11. [변경되지 않은 것들](#11-변경되지-않은-것들)
12. [API Gateway를 통한 요청 흐름 상세](#12-api-gateway를-통한-요청-흐름-상세)
13. [서비스별 역할 분담 요약](#13-서비스별-역할-분담-요약)

---

## 1. 전체 구조 변화

### 모노리틱

하나의 Spring Boot 애플리케이션이 모든 것을 담당했다. 브라우저 요청이 바로 애플리케이션으로 들어오고, 컨트롤러가 비즈니스 로직을 처리한 뒤 Thymeleaf 템플릿을 렌더링해서 완성된 HTML을 내려주는 방식이었다.

```
[브라우저]
    ↓  HTTP 요청 (폼 제출 포함)
[StudyOlle 단일 애플리케이션 :8080]
    - Spring Security (인증/인가)
    - Controller (요청 처리)
    - Service (비즈니스 로직)
    - Repository (DB 접근)
    - Thymeleaf (HTML 렌더링)
    ↓
[PostgreSQL DB]
```

### MSA

역할이 명확하게 분리된 여러 서비스로 구성된다. 가장 중요한 변화는 **HTML을 내려주는 역할**과 **데이터를 처리하는 역할**이 완전히 다른 서비스로 나뉘었다는 점이다.

```
[브라우저]
    ↓  GET 요청 (페이지 이동)
[frontend-service :8090]  ← HTML 껍데기만 내려줌. DB/JWT 모름.
    ↓  JS fetch() (데이터 요청)
[api-gateway :8080]       ← JWT 검증 / 라우팅 전담
    ↓  내부 라우팅
[account-service :8081]   ← 회원 관리, JWT 발급. HTML 모름.
    ↓
[PostgreSQL DB]
```

이 분리의 핵심은 `frontend-service`가 데이터를 전혀 모른다는 것이다. DB 연결도 없고, 비즈니스 로직도 없고, 심지어 JWT도 모른다. 그냥 HTML 파일을 읽어서 브라우저에 내려주는 역할만 한다. 실제 데이터 처리는 브라우저의 JavaScript가 `api-gateway`를 통해 직접 수행한다.

---

## 2. 인증 방식 변화 (세션 → JWT)

이 변화가 프론트엔드 코드 전체에 영향을 미치는 근본적인 차이다.

### 모노리틱 - Spring Security 세션 기반

로그인 성공 시 Spring Security가 서버 메모리(세션)에 인증 정보를 저장하고, 브라우저에는 `JSESSIONID` 쿠키를 발급했다. 이후 모든 요청에서 브라우저가 이 쿠키를 자동으로 담아 보내면 서버가 세션에서 사용자를 찾아냈다.

개발자는 쿠키나 세션을 직접 다룰 필요가 없었다. Spring Security가 모든 것을 자동으로 처리해줬기 때문이다. 컨트롤러에서는 그냥 `@CurrentUser Account account` 파라미터를 선언하면 현재 로그인한 사용자 객체를 받을 수 있었다.

```java
// 모노리틱: 컨트롤러가 현재 로그인 사용자를 바로 받아쓸 수 있었음
@GetMapping("/")
public String home(@CurrentUser Account account, Model model) {
    if (account != null) {
        model.addAttribute("account", account);
        model.addAttribute("studyList", studyService.getStudyListForUser(account));
    }
    return "index";
}
```

HTML 측면에서도 CSRF 토큰이 모든 POST 폼에 자동으로 삽입되었다. 개발자는 이것도 신경 쓸 필요가 없었다.

```html
<!-- 모노리틱: Spring Security가 hidden input으로 CSRF 토큰을 자동 삽입 -->
<form th:action="@{/sign-up}" method="post">
    <!-- 실제 렌더링된 HTML에는 아래가 자동으로 추가됨 -->
    <!-- <input type="hidden" name="_csrf" value="abc123..."> -->
    <input type="text" name="nickname">
</form>
```

### MSA - JWT (JSON Web Token) Stateless

로그인 성공 시 `account-service`가 JWT를 발급한다. 이 토큰은 브라우저의 `localStorage`에 저장되며, 이후 모든 API 요청의 `Authorization: Bearer {token}` 헤더에 담아서 보내야 한다. 브라우저가 자동으로 해주는 일이 없기 때문에, JavaScript 코드가 이 역할을 담당한다.

```javascript
// MSA: 로그인 성공 후 JWT를 localStorage에 직접 저장
const result = await response.json();
localStorage.setItem('accessToken', result.data.accessToken);
localStorage.setItem('refreshToken', result.data.refreshToken);

// 이후 인증이 필요한 모든 API 요청에 헤더를 직접 추가해야 함
const response = await fetch(API_BASE + '/api/accounts/me', {
    headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('accessToken')
    }
});
```

JWT는 쿠키가 아니므로 CSRF 공격에 취약하지 않다. 따라서 모든 HTML 폼에서 CSRF 토큰이 완전히 제거되었다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| 인증 저장 위치 | 서버 세션 (메모리) | 클라이언트 localStorage |
| 인증 전달 방식 | 쿠키 (JSESSIONID, 자동) | HTTP 헤더 (Authorization: Bearer, 수동) |
| 인증 검증 위치 | 각 서비스 내부 (Spring Security) | api-gateway 한 곳에서 전담 |
| 서버 상태 | Stateful (세션 유지 필요) | Stateless (서버가 상태를 기억하지 않음) |
| CSRF 보호 | 필요 (Spring Security 자동 처리) | 불필요 (쿠키를 사용하지 않음) |
| 로그인 확인 방법 | 서버가 세션에서 확인 | 클라이언트가 localStorage에서 확인 |

---

## 3. 프론트엔드 렌더링 패러다임 전환

### 모노리틱 - 서버 사이드 렌더링 (SSR)

모노리틱에서 HTML 페이지의 생애 주기는 다음과 같았다.

1. 브라우저가 서버에 GET 요청을 보낸다.
2. 서버의 컨트롤러가 서비스를 호출해 DB에서 데이터를 가져온다.
3. 가져온 데이터를 Model에 담는다.
4. Thymeleaf가 Model의 데이터를 HTML 템플릿에 주입해서 완성된 HTML을 만든다.
5. 이 완성된 HTML이 브라우저에 도달한다.

브라우저에 도달하는 시점에 이미 모든 데이터가 HTML 안에 들어있는 상태다. JavaScript의 역할은 부수적이었다 (폼 유효성 검사, 애니메이션 등).

폼 제출의 경우:
1. 사용자가 폼을 작성하고 제출한다.
2. 브라우저가 POST 요청으로 데이터를 서버에 보낸다.
3. 서버가 검증하고 처리한 뒤, 성공이면 다른 페이지로 redirect, 실패면 에러가 담긴 같은 페이지를 다시 렌더링한다.
4. 브라우저가 새 페이지(또는 리렌더링된 같은 페이지)를 받는다.

이 방식에서는 모든 처리 결과가 서버에서 결정된다. 브라우저는 서버가 만들어준 HTML을 보여주는 역할만 했다.

### MSA - 클라이언트 사이드 렌더링 (CSR)

MSA에서 HTML 페이지의 생애 주기는 완전히 달라졌다.

1. 브라우저가 `frontend-service`에 GET 요청을 보낸다.
2. `frontend-service`는 HTML 껍데기를 내려준다. 데이터가 없는 상태다.
3. 브라우저가 HTML을 파싱하고 JavaScript를 실행한다.
4. JavaScript가 `api-gateway`에 별도의 HTTP 요청(fetch)을 보낸다.
5. 응답을 받은 JavaScript가 DOM을 직접 조작해서 데이터를 화면에 표시한다.

폼 제출의 경우:
1. 사용자가 버튼을 클릭한다 (`type="submit"` 폼이 없고 `type="button"`이다).
2. JavaScript의 `click` 이벤트 핸들러가 실행된다.
3. `fetch()`로 `api-gateway`에 JSON을 보낸다. 페이지 이동이 없다.
4. 응답 JSON을 받아서 성공이면 `location.href`로 이동하거나 화면을 전환하고, 실패면 에러 메시지를 DOM에 삽입한다.

이 방식에서는 모든 처리 결과가 클라이언트(브라우저)에서 결정된다. 서버는 데이터(JSON)만 줄 뿐, 화면을 어떻게 표시할지는 JavaScript가 결정한다.

```
[모노리틱 요청 흐름]
브라우저 → POST /sign-up (폼 데이터) → 서버 → 처리 → redirect or 에러 HTML 반환
                                          ↑ 서버가 결정

[MSA 요청 흐름]
브라우저 → (버튼 클릭) → JS가 fetch(POST /api/auth/signup, JSON) → 서버 → JSON 반환
                              ↓ JS가 결정
                         성공: location.href 변경
                         실패: DOM에 에러 메시지 삽입
```

---

## 4. Thymeleaf 사용 방식의 변화

모노리틱에서 Thymeleaf는 데이터를 화면에 표시하는 핵심 도구였다. MSA에서 Thymeleaf는 역할이 대폭 축소되었다.

### 제거된 Thymeleaf 패턴들

**폼 바인딩 패턴 제거**

모노리틱에서는 서버의 DTO(예: `SignUpForm`)와 HTML 폼을 Thymeleaf로 직접 연결했다. `th:object`가 DTO를 지정하고, `th:field`가 각 input의 `name`, `id`, `value`를 자동으로 렌더링했다. 검증 실패 시에는 DTO에 남아있는 입력값이 자동으로 폼에 채워져서 사용자가 다시 입력하지 않아도 됐다.

```html
<!-- 모노리틱: DTO와 폼이 서버에서 자동으로 연결 -->
<form th:action="@{/sign-up}" th:object="${signUpForm}" method="post">
    <!-- th:field가 name="nickname", id="nickname", value="${signUpForm.nickname}"를 자동 생성 -->
    <input type="text" th:field="*{nickname}" required>
    <!-- 검증 실패 시 이전에 입력했던 값이 자동으로 채워짐 -->
</form>
```

MSA에서는 서버가 폼 데이터를 다시 렌더링하지 않으므로 이 패턴이 완전히 사라졌다. 폼 필드는 그냥 일반 HTML input이다.

```html
<!-- MSA: 일반 input. 서버 DTO와 무관. -->
<input id="nickname" type="text" placeholder="whiteship"
       minlength="3" maxlength="20" data-validate="nickname">
```

**서버 검증 에러 렌더링 패턴 제거**

모노리틱에서는 `@Valid` 검증 실패 시 `BindingResult`에 에러가 담기고, Thymeleaf가 이를 읽어서 에러 메시지를 HTML에 주입했다.

```html
<!-- 모노리틱: 서버가 검증하고 에러 메시지를 HTML에 직접 주입 -->
<div class="field-error" th:if="${#fields.hasErrors('nickname')}"
     th:errors="*{nickname}">
    닉네임 에러 메시지
</div>
```

MSA에서는 서버가 에러 JSON을 응답하고, JavaScript가 그것을 읽어서 DOM을 조작한다.

```html
<!-- MSA: 처음에는 숨겨진 빈 div. JS가 에러 내용을 채우고 표시한다. -->
<div id="nicknameError" class="field-error" style="display:none;"></div>
```

```javascript
// JavaScript가 API 응답을 받아 에러 메시지를 삽입
if (errorCode === 'DUPLICATE_NICKNAME') {
    const el = document.getElementById('nicknameError');
    el.textContent = result.message;
    el.style.display = 'block';
}
```

**조건부 렌더링 패턴 제거**

모노리틱에서는 서버가 Model에 데이터를 담아서 보내면 Thymeleaf가 조건에 따라 다른 HTML 블록을 렌더링했다.

```html
<!-- 모노리틱: 서버가 성공/에러 블록을 결정해서 렌더링 -->
<div th:if="${error == null}">인증 성공 화면</div>
<div th:if="${error != null}">인증 실패 화면</div>
```

MSA에서는 두 블록을 모두 HTML에 작성해두고, JavaScript가 조건에 따라 `display` 속성을 전환한다.

```html
<!-- MSA: 두 블록이 모두 HTML에 존재하지만 초기에는 모두 숨겨짐 -->
<div id="successCard" style="display:none;">인증 성공 화면</div>
<div id="errorCard" style="display:none;">인증 실패 화면</div>
<div id="loadingCard">로딩 중...</div>
```

```javascript
// JavaScript가 API 응답을 받은 후 조건에 따라 카드를 전환
function showSuccess() {
    document.getElementById('loadingCard').style.display = 'none';
    document.getElementById('successCard').style.display = 'block';
}
```

**서버 리다이렉트 패턴 제거**

모노리틱에서는 처리 성공 후 서버가 `return "redirect:/check-email"` 한 줄로 브라우저를 다른 페이지로 보냈다.

```java
// 모노리틱: 서버가 리다이렉트를 결정
@PostMapping("/sign-up")
public String signUp(@Valid SignUpForm form, BindingResult result) {
    if (result.hasErrors()) {
        return "account/sign-up"; // 같은 페이지 재렌더링
    }
    signUpService.processNewAccount(form);
    return "redirect:/check-email"; // 서버가 리다이렉트
}
```

MSA에서는 JavaScript가 `location.href`로 페이지 이동을 직접 처리한다. 이메일 정보처럼 다음 페이지에서 필요한 데이터는 URL 쿼리 파라미터로 전달한다.

```javascript
// MSA: JavaScript가 리다이렉트를 결정
if (response.ok) {
    // 다음 페이지에서 이메일을 표시하기 위해 URL 파라미터로 전달
    window.location.href = '/check-email?email=' + encodeURIComponent(email);
}
```

### 여전히 사용되는 Thymeleaf 패턴들

Thymeleaf가 완전히 사라진 것은 아니다. MSA에서도 서버가 렌더링 시점에 HTML에 주입해야 하는 정보가 있다.

**정적 리소스 경로 표현식** (`@{...}`)은 그대로 유지된다. 개발/운영 환경에 따라 컨텍스트 경로가 달라질 수 있기 때문이다.

```html
<link rel="stylesheet" th:href="@{/css/auth-style.css}">
<script th:src="@{/js/glass-validation.js}"></script>
```

**JavaScript 인라인 주입** (`th:inline="javascript"`)은 MSA에서 새롭게 중요해진 패턴이다. `application.yml`의 설정값을 HTML을 통해 JavaScript 변수로 전달하는 용도로 사용한다.

```html
<script th:inline="javascript">
    // AuthPageController가 Model에 담은 apiBase 값을 JS 변수로 주입
    // 운영 배포 시 application.yml만 바꾸면 HTML 수정 없이 적용됨
    const API_BASE = [[${apiBase}]];
    // check-email.html에서 재발송 시 이메일 주소를 JS에 전달
    const USER_EMAIL = [[${email}]];
</script>
```

---

## 5. 에러 처리 패턴의 변화

에러 처리 방식이 서버 주도에서 클라이언트 주도로 완전히 바뀌었다. 이것이 HTML 구조에 가장 큰 영향을 미쳤다.

### 모노리틱 - 서버가 에러를 결정하고 렌더링

에러가 발생하면 서버가 에러 정보를 Model에 담아서 같은 페이지를 다시 렌더링했다. 브라우저 입장에서는 전체 페이지가 새로 로드된다.

```java
// 모노리틱 컨트롤러: 에러 시 같은 뷰로 에러를 담아 반환
@PostMapping("/sign-up")
public String signUp(@Valid SignUpForm form, BindingResult result, Model model) {
    if (signUpService.isDuplicateNickname(form.getNickname())) {
        result.rejectValue("nickname", "invalid.nickname", "이미 사용 중인 닉네임입니다.");
    }
    if (result.hasErrors()) {
        return "account/sign-up"; // 에러가 담긴 채로 같은 페이지 재렌더링
    }
    // ...
}
```

```html
<!-- 모노리틱: Thymeleaf가 서버의 에러 정보를 읽어서 렌더링 -->
<div class="field-error"
     th:if="${#fields.hasErrors('nickname')}"
     th:errors="*{nickname}">
</div>
```

로그인 에러는 Spring Security가 쿼리 파라미터로 전달했다.

```html
<!-- 모노리틱: Spring Security가 /login?error 로 리다이렉트하면 이게 표시됨 -->
<div th:if="${param.error}" class="alert alert-danger">
    이메일 또는 비밀번호가 올바르지 않습니다.
</div>
```

### MSA - JavaScript가 에러를 받아서 DOM에 표시

MSA에서는 API가 에러 응답(4xx)을 JSON으로 반환하고, JavaScript가 이를 파싱해서 DOM을 직접 조작한다. 페이지 전체가 새로 로드되지 않는다.

**에러 응답 구조 (account-service GlobalExceptionHandler)**

```json
{
    "errorCode": "DUPLICATE_NICKNAME",
    "message": "이미 사용 중인 닉네임입니다."
}
```

**JavaScript의 에러 처리 패턴**

```javascript
const response = await fetch(API_BASE + '/api/auth/signup', { ... });
const result = await response.json();

if (!response.ok) {
    const errorCode = result.errorCode || '';
    const message   = result.message   || '오류가 발생했습니다.';

    // errorCode에 따라 해당 필드의 에러 div에 메시지를 표시
    if (errorCode === 'DUPLICATE_NICKNAME') {
        showFieldError('nicknameError', message);
    } else if (errorCode === 'DUPLICATE_EMAIL') {
        showFieldError('emailError', message);
    } else {
        // 특정 필드에 귀속되지 않는 에러는 상단 공통 에러 박스에 표시
        showGeneralError(message);
    }
}

function showFieldError(elementId, message) {
    const el = document.getElementById(elementId);
    el.textContent = message;
    el.style.display = 'block';
}
```

**에러 초기화 패턴**

모노리틱에서는 페이지가 새로 렌더링될 때마다 에러가 자동으로 초기화됐다. MSA에서는 사용자가 버튼을 다시 클릭할 때마다 JavaScript가 이전 에러를 직접 초기화해야 한다.

```javascript
// 버튼 클릭 시 항상 이전 에러 메시지를 먼저 지운다
function clearAllErrors() {
    ['nicknameError', 'emailError', 'passwordError'].forEach(function (id) {
        const el = document.getElementById(id);
        el.textContent = '';
        el.style.display = 'none';
    });
    document.getElementById('errorGeneral').style.display = 'none';
}
```

### errorCode 기반 분기의 의미

모노리틱에서는 Spring MVC의 `BindingResult`가 어느 필드에 어떤 에러가 있는지 자동으로 추적했다. MSA에서는 이 역할을 `errorCode` 문자열이 대신한다. 프론트엔드와 백엔드가 `errorCode`를 기준으로 계약을 맺는 방식이다.

| errorCode | 의미 | 프론트엔드 처리 |
|-----------|------|----------------|
| `DUPLICATE_NICKNAME` | 닉네임 중복 | `#nicknameError` div에 메시지 표시 |
| `DUPLICATE_EMAIL` | 이메일 중복 | `#emailError` div에 메시지 표시 |
| `VALIDATION_ERROR` | Bean Validation 실패 | `#errorGeneral` 공통 에러 박스에 표시 |
| `EMAIL_NOT_VERIFIED` | 이메일 미인증 상태 로그인 시도 | 주황색 경고 박스 (`#errorDisabled`) 표시 |
| `ACCOUNT_NOT_FOUND` | 가입되지 않은 이메일 | `#errorBox`에 안내 메시지 표시 |
| `INVALID_EMAIL_TOKEN` | 만료되거나 잘못된 인증 토큰 | `#errorCard` 카드 전체 표시 |

---

## 6. 페이지 전환 방식의 변화

### 모노리틱 - 서버 리다이렉트

모든 성공/실패 후 페이지 전환은 서버가 결정했다. 서버가 `redirect:` 를 반환하면 브라우저가 새 URL로 이동했다.

```java
// 회원가입 성공 → 서버가 /check-email 로 리다이렉트
return "redirect:/check-email";

// 로그인 성공 → Spring Security가 / 로 리다이렉트
// (설정: defaultSuccessUrl("/"))

// 이메일 인증 성공 → 서버가 / 로 리다이렉트
return "redirect:/";
```

### MSA - JavaScript의 두 가지 전환 방식

MSA에서는 상황에 따라 두 가지 방법을 사용한다.

**전체 페이지 이동** (`location.href`): 처리 완료 후 완전히 다른 페이지로 넘어가야 할 때 사용한다. 모노리틱의 `redirect:`에 해당한다.

```javascript
// 회원가입 성공 → 이메일 확인 안내 페이지로 이동
// 이메일 주소를 URL 파라미터로 전달 (다음 페이지에서 표시용)
window.location.href = '/check-email?email=' + encodeURIComponent(email);

// 로그인 성공 → 홈으로 이동
window.location.href = '/';
```

**카드 전환** (display 속성 변경): 같은 페이지 안에서 화면 상태만 바꿀 때 사용한다. 페이지 이동 없이 더 부드러운 UX를 제공한다. 이 방식은 모노리틱에는 없던 패턴이다.

```javascript
// email-login.html: 이메일 발송 성공 후 안내 화면으로 전환
// 전체 페이지 이동 없이 카드만 바꿈
document.getElementById('inputCard').style.display = 'none';
document.getElementById('sentCard').style.display  = 'block';
document.getElementById('sentEmail').textContent   = email;
```

카드 전환 방식이 사용된 페이지는 `email-login.html`이다. 이메일을 입력하는 `#inputCard`와 발송 완료를 안내하는 `#sentCard`가 모두 HTML에 존재하지만, 초기에는 `#inputCard`만 보인다. 발송 성공 시 JavaScript가 두 카드의 display를 전환한다.

**로딩 상태 처리**: 모노리틱에서는 서버가 처리하는 동안 브라우저가 로딩 상태를 자동으로 표시했다 (주소창의 로딩 애니메이션 등). MSA에서는 fetch()가 진행 중인 동안 별도의 로딩 UI가 필요하다.

```javascript
// 버튼을 비활성화하고 텍스트를 변경해 로딩 중임을 알림
signUpBtn.disabled = true;
signUpBtn.textContent = '가입 중...';

try {
    const response = await fetch(...);
    // ...
} finally {
    // 성공/실패 관계없이 버튼 복구
    signUpBtn.disabled = false;
    signUpBtn.textContent = '가입하기';
}
```

`check-email-token.html`에서는 페이지 로드 직후 API 호출이 일어나므로 별도의 `#loadingCard`(스피너)를 보여주다가 응답이 오면 성공/실패 카드로 교체한다.

---

## 7. AuthPageController의 역할

모노리틱의 `AccountController`는 GET/POST 요청을 모두 처리하고, 서비스를 호출하고, 리다이렉트를 결정하는 복잡한 컨트롤러였다. MSA의 `AuthPageController`는 그것과 완전히 다르다.

### 모노리틱 AccountController (일부)

```java
// 모노리틱: GET과 POST를 모두 처리. 비즈니스 로직을 직접 호출.
@Controller
public class AccountController {

    @GetMapping("/sign-up")
    public String signUpForm(Model model) {
        model.addAttribute(new SignUpForm()); // DTO를 폼에 바인딩
        return "account/sign-up";
    }

    @PostMapping("/sign-up")
    public String signUp(@Valid SignUpForm signUpForm, BindingResult result) {
        if (result.hasErrors()) {
            return "account/sign-up"; // 에러 시 재렌더링
        }
        Account account = signUpService.processNewAccount(signUpForm); // 서비스 호출
        accountAuthService.login(account); // 로그인 처리
        return "redirect:/"; // 리다이렉트
    }
}
```

### MSA AuthPageController

```java
// MSA: GET 요청만 처리. 서비스 호출 없음. apiBase 주입이 전부.
@Controller
public class AuthPageController {

    @Value("${app.api-base-url}")
    private String apiBase;

    // 모든 메서드가 이 패턴을 따름: apiBase 주입 + 템플릿 이름 반환
    @GetMapping("/sign-up")
    public String signUpPage(Model model) {
        model.addAttribute("apiBase", apiBase);
        return "account/sign-up";
    }

    // 일부 페이지는 URL 파라미터를 받아 Model에 추가로 전달
    @GetMapping("/check-email")
    public String checkEmailPage(@RequestParam(required = false) String email,
                                 Model model) {
        model.addAttribute("apiBase", apiBase);
        model.addAttribute("email", email); // Thymeleaf가 ${email}로 렌더링
        return "account/check-email";
    }
}
```

`AuthPageController`가 하는 일은 정확히 두 가지다. 첫째, `application.yml`의 `app.api-base-url` 값을 `apiBase`라는 이름으로 Model에 담아서 모든 페이지의 JavaScript가 `API_BASE` 변수로 사용할 수 있게 한다. 둘째, 일부 페이지는 URL 파라미터(`email`, `token`)를 받아서 Thymeleaf가 페이지에 정적으로 렌더링할 수 있도록 Model에 전달한다.

비즈니스 로직 호출, 검증, 리다이렉트 결정 같은 역할은 모두 사라졌다.

---

## 8. 페이지별 상세 변경 사항

### login.html

가장 눈에 띄는 변화는 `errorCode`에 따른 두 가지 에러 박스다. 모노리틱에서는 Spring Security가 로그인 실패 이유를 `?error` 파라미터 하나로만 전달했다. MSA에서는 `account-service`가 구체적인 `errorCode`를 반환하기 때문에, 이메일 미인증과 비밀번호 오류를 시각적으로 구분해서 표시할 수 있다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| 폼 처리 | `th:action="@{/login}" method="post"` + Spring Security | `fetch(POST /api/auth/login, JSON)` |
| 에러 표시 | `th:if="${param.error}"` 단일 에러 박스 | `errorCode`에 따라 빨간 에러 / 주황 경고 두 가지 박스 분기 |
| 성공 처리 | Spring Security가 설정된 URL로 자동 리다이렉트 | JS가 `localStorage`에 JWT 저장 후 `location.href = '/'` |
| CSRF 토큰 | Spring Security 자동 삽입 | 제거 |
| 이메일 미인증 | 일반 로그인 실패와 동일하게 처리 | `EMAIL_NOT_VERIFIED` errorCode → 별도 주황 경고 박스 |

### sign-up.html

`th:object`와 `th:field`가 완전히 사라진 것이 핵심이다. 서버가 DTO와 폼을 바인딩해주는 것이 없기 때문에, 각 input은 그냥 `id`만 가진 일반 HTML 요소가 된다. JavaScript가 `getElementById`로 값을 직접 읽는다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| 폼 바인딩 | `th:object="${signUpForm}"`, `th:field="*{nickname}"` | 일반 `<input id="nickname">` |
| 서버 검증 에러 | `th:errors="*{nickname}"` 서버 렌더링 | JS가 `errorCode`에 따라 `#nicknameError` div를 직접 조작 |
| 성공 처리 | 서버가 `redirect:/check-email` | JS가 `location.href = '/check-email?email=' + encodeURIComponent(email)` |
| 중복 검사 | 서버 `SignUpService`가 검증 → `BindingResult`에 등록 → 재렌더링 | `DUPLICATE_NICKNAME` / `DUPLICATE_EMAIL` errorCode → 해당 필드 에러 div 표시 |
| 이전 입력값 유지 | Thymeleaf가 DTO의 값을 input에 자동으로 채워줌 | JS가 에러 div만 표시. input 값은 사용자가 입력한 그대로 유지됨. |

### check-email.html

이 페이지에서의 핵심 변화는 이메일 주소의 출처다. 모노리틱에서는 사용자가 이미 회원가입 과정에서 세션에 로그인되어 있었기 때문에, 서버가 세션에서 현재 사용자의 이메일을 가져올 수 있었다. MSA에서는 회원가입 직후 세션이 없으므로, `sign-up.html`의 JavaScript가 이메일을 URL 파라미터로 전달한다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| 이메일 출처 | 서버 세션 (`@CurrentUser Account account`) | URL 쿼리 파라미터 `?email=...` → `AuthPageController`가 Model에 주입 → `${email}` |
| 재발송 방식 | `<a th:href="@{/resend-confirm-email}">` GET 링크 → 페이지 리로드 | `fetch(POST /api/auth/resend-verification-email, { email })` → 페이지 리로드 없음 |
| 재발송 피드백 | 서버가 결과를 Model에 담아 페이지 재렌더링 | 5초 쿨다운 + `#resendMsg` div에 성공/실패 메시지 직접 삽입 |
| 에러 카드 | `th:if="${error != null}"` 로 에러 시 다른 카드 표시 | 제거. 이 페이지는 항상 회원가입 성공 후에만 도달하므로 에러 카드 불필요. |
| 중복 클릭 방지 | 서버 처리 후 페이지가 재로드되므로 자연스럽게 방지 | 5초 쿨다운 (`cooldown` 변수)으로 JavaScript에서 직접 방지 |

### check-email-token.html (모노리틱: checked-email.html)

이 페이지가 모노리틱과 MSA 간 구조적 차이가 가장 극명하게 드러나는 곳이다. 모노리틱에서는 서버가 이미 검증을 마치고 결과를 담아서 HTML을 내려줬다. MSA에서는 HTML이 먼저 내려오고, JavaScript가 페이지 로드 후에 API를 호출해서 검증한다. 이 때문에 "검증 중" 상태를 표시할 로딩 카드가 반드시 필요하다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| **렌더링 시점** | 서버가 토큰 검증 완료 후 결과 HTML 반환 | HTML 먼저 내려옴 → 페이지 로드 후 JS가 API 호출 → 결과에 따라 DOM 전환 |
| 성공/실패 분기 | `th:if="${error == null}"` / `th:if="${error}"` 서버가 결정 | `#loadingCard` → API 응답 후 `#successCard` 또는 `#errorCard` 표시 |
| 닉네임 표시 | `th:text="${nickname}"` 서버 주입 | API 응답 JSON의 `data.nickname`을 JS가 `#successNickname`에 `textContent`로 삽입 |
| 인증 성공 후 처리 | Spring Security `authenticationManager`로 강제 로그인 + 세션 등록 | JS가 응답의 `data.accessToken`, `data.refreshToken`을 `localStorage`에 저장 |
| 로딩 상태 | 없음 (서버가 모두 처리하고 완성된 HTML 반환) | `#loadingCard` (스피너)를 초기에 표시 |
| 파일명 변경 | `checked-email.html` | `check-email-token.html` (URL 패턴 `/check-email-token`과 일치) |

### email-login.html

모노리틱에서 이 페이지는 다른 인증 페이지들과 완전히 다른 스타일이었다. `fragments.html`의 헤더와 네비게이션 바를 가져다 쓰는 일반 Bootstrap 레이아웃이었다. MSA에서는 `fragments.html` 자체가 존재하지 않으므로 독립적인 레이아웃으로 재작성하고, 다른 인증 페이지들과 동일한 `auth-style.css`를 적용해서 시각적 통일성을 확보했다.

| 항목 | 모노리틱 | MSA |
|------|---------|-----|
| 레이아웃 | `fragments.html :: head`, `fragments.html :: main-nav` 사용 | 독립 레이아웃 (fragments.html 의존성 없음) |
| 스타일 | 일반 Bootstrap (다른 인증 페이지와 이질적인 스타일) | `auth-style.css` 적용 (모든 인증 페이지와 시각적으로 통일) |
| 성공 피드백 | 서버 페이지 재렌더링 + `th:if="${message}"` (페이지 전체 리로드) | `#inputCard` → `#sentCard` 카드 전환 (페이지 리로드 없음, 더 부드러운 UX) |
| 에러 피드백 | `th:if="${error}"` 서버 렌더링 | `#errorBox` div에 JS가 메시지 직접 삽입 |
| 재발송 | 링크나 버튼이 없었음 | `#resendBtn`으로 같은 API를 다시 호출, 5초 쿨다운 적용 |

---

## 9. 이메일 인증 흐름 변화

### 모노리틱

```
1. [브라우저] 회원가입 폼 제출 (POST /sign-up)
       ↓
2. [서버] 회원 저장 → Spring Security로 강제 로그인 → 세션 등록
         이메일 링크 생성: http://localhost:8080/check-email-token?token=UUID&email=xxx
         이메일 발송
       ↓ redirect:/check-email
3. [서버] /check-email 요청
         세션에서 현재 사용자 이메일 조회 → Model에 담아 렌더링
       ↓ (사용자가 이메일 링크 클릭)
4. [서버] GET /check-email-token?token=UUID&email=xxx
         토큰 검증 → Spring Security로 강제 로그인 → 세션 갱신
         닉네임을 Model에 담아 성공 HTML 렌더링
       ↓ 또는 에러 시
         에러 메시지를 Model에 담아 에러 HTML 렌더링
```

### MSA

```
1. [JS] fetch(POST api-gateway:8080/api/auth/signup, JSON)
       ↓ api-gateway가 account-service로 라우팅
2. [account-service] 회원 저장
         이메일 링크 생성: http://localhost:8090/check-email-token?token=UUID&email=xxx
         (app.host = http://localhost:8090, 즉 frontend-service 주소)
         이메일 발송 → 201 OK 반환
       ↓ JS: location.href = '/check-email?email=' + encodeURIComponent(email)
3. [frontend-service] GET /check-email?email=xxx
         AuthPageController가 email을 Model에 담아 HTML 반환
         (Thymeleaf가 ${email}을 렌더링)
       ↓ (사용자가 이메일 링크 클릭 → frontend-service:8090으로 이동)
4. [frontend-service] GET /check-email-token?token=UUID&email=xxx
         AuthPageController가 로딩 카드가 포함된 HTML 반환 (검증 안 함)
       ↓ (페이지 로드 후 JS 즉시 실행)
5. [JS] fetch(GET api-gateway:8080/api/auth/check-email-token?token=UUID&email=xxx)
       ↓ api-gateway가 account-service로 라우팅
6. [account-service] 토큰 검증 → JWT 발급 → 응답
         { data: { accessToken, refreshToken, nickname } }
       ↓
7. [JS] localStorage.setItem('accessToken', data.accessToken)
        localStorage.setItem('refreshToken', data.refreshToken)
        #successNickname.textContent = data.nickname
        #loadingCard 숨김 → #successCard 표시
```

`app.host` 설정이 핵심이다. 모노리틱에서는 이 값이 의미가 없었다 (서버 자신을 가리켰음). MSA에서는 반드시 `frontend-service` 주소로 설정해야 한다.

```yaml
# account-service application.yml
app:
  host: http://localhost:8090  # frontend-service 주소
  # 이 값이 8081(account-service 자신)이면 이메일 링크 클릭 시 404 발생
```

---

## 10. 정적 자원(CSS/JS) 변경

### CSS: glassmorphism.css → auth-style.css

| 항목 | 내용 |
|------|------|
| 구 파일명 | `glassmorphism.css` |
| 신 파일명 | `auth-style.css` |
| 적용 디자인 | Warm Flat Minimal |
| 내용 변화 | 없음. 이미 v3 리디자인에서 `backdrop-filter`(블러 효과)가 제거되고 Warm Flat Minimal로 전환된 상태였다. 파일명만 실제 내용을 반영하는 이름으로 변경. |
| 제거된 효과 | `backdrop-filter: blur()` (글래스모피즘 핵심 효과), 반투명 배경, 장식 구체(orb, `display:none !important`로 숨김) |
| 유지된 효과 | 입장 애니메이션 (`cardEntry`, `fieldFade`, `iconBounce`), 체크 아이콘 애니메이션, 얇은 보더 기반 카드 |

### JS: glass-validation.js

이 파일은 MSA 전환과 무관하게 그대로 유지된다. `data-validate` 속성을 읽어서 실시간 유효성 검사를 수행하고 체크 아이콘을 표시하는 로직은 서버나 Thymeleaf에 전혀 의존하지 않기 때문이다.

단, 모노리틱에서는 폼의 `submit` 이벤트와 연계해서 동작하는 부분이 있었다. MSA에서는 폼 자체가 없고 버튼 클릭 이벤트를 JS가 처리하므로, 해당 연동 코드가 제거되었다.

MSA에서 추가된 사용 방식은 `GlassValidation.validateNow('email')`이다. URL 파라미터로 이메일을 미리 채워줄 때, 값이 있음에도 체크 아이콘이 표시되지 않는 문제를 해결하기 위해 수동으로 검사를 트리거하는 API다.

---

## 11. 변경되지 않은 것들

MSA로 전환하면서도 유지된 부분들이다.

**CSS 클래스명과 시각적 결과물**은 변경이 없다. `.glass-card`, `.btn-glass`, `.btn-login`, `.glass-alert`, `.form-group`, `.input-wrapper`, `.check-icon` 등 모든 클래스명이 그대로 `auth-style.css`로 이전되었다. 개발자가 클래스명을 수정할 필요가 없었다.

**색상 팔레트**도 그대로다. 웜 그레이(`#f5f3ef`), 크림 화이트(`#fdfcfa`), 차분한 퍼플(`#8e85b0`), 웜 그린(`#7fac8e`), 웜 코랄(`#c4917e`)의 조합이 유지된다.

**이메일 본문 템플릿**(`mail/simple-link.html`)은 `account-service` 내부에 그대로 유지된다. 이 파일은 브라우저에 보여주는 게 아니라 `JavaMailSender`가 이메일 본문을 렌더링하는 용도이기 때문에, Thymeleaf 의존성이 남아 있어도 문제없다.

**`data-validate` 속성 기반 실시간 검사**는 그대로 동작한다. `glass-validation.js`가 HTML의 `data-validate` 속성만 읽어서 동작하기 때문에, 서버 렌더링 방식과 무관하다.

---

## 12. API Gateway를 통한 요청 흐름 상세

프론트엔드의 JavaScript가 `fetch()`를 호출하는 순간부터 `account-service`가 응답을 반환하기까지 실제로 어떤 일이 벌어지는지를 단계별로 설명한다.

### api-gateway는 HTTP 리버스 프록시다

api-gateway를 이해하는 가장 쉬운 방법은 **우편 집중국**으로 생각하는 것이다. 브라우저는 항상 `localhost:8080`이라는 주소 하나로만 편지(HTTP 요청)를 보낸다. api-gateway가 편지를 받아서 주소(경로)를 보고, 올바른 서비스로 전달해준다. 브라우저는 최종적으로 편지가 어느 서비스에 도달했는지 모른다.

이것이 가능한 이유는 Spring Cloud Gateway가 내부적으로 HTTP 프록시 역할을 하기 때문이다. 클라이언트 입장에서는 api-gateway와 통신하는 것처럼 보이지만, 실제로는 api-gateway가 그 요청을 그대로 복사해서 내부 서비스로 전달하고, 내부 서비스의 응답을 다시 클라이언트에게 돌려준다.

### 공개 API 흐름 - 회원가입을 예시로

`sign-up.html`에서 가입하기 버튼을 누르면 다음 JavaScript 코드가 실행된다.

```javascript
// 브라우저가 localhost:8080으로 HTTP POST 요청을 보낸다
// 특별한 기술이 전혀 없다. 그냥 평범한 HTTP 요청이다.
const response = await fetch(API_BASE + '/api/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname, email, password })
});
```

이 `fetch()` 호출이 만들어내는 것은 그냥 평범한 HTTP POST 요청이다. 브라우저가 이 요청을 `localhost:8080`으로 보내면, api-gateway가 받아서 `application.yml`의 라우팅 규칙을 확인한다.

```yaml
# api-gateway application.yml
routes:
  - id: account-service-public
    uri: lb://ACCOUNT-SERVICE   # 목적지: Eureka에 등록된 ACCOUNT-SERVICE
    predicates:
      - Path=/api/auth/**       # 조건: 경로가 /api/auth/ 로 시작하면
    # filters: 없음            # JWT 검증 없이 그냥 통과
```

`/api/auth/signup`이 `/api/auth/**` 패턴에 맞으므로, api-gateway는 이 요청을 `ACCOUNT-SERVICE`로 전달하기로 결정한다. 여기서 `lb://`의 의미가 중요하다. `lb`는 Load Balancer의 약자로, "Eureka에 등록된 서비스 이름으로 실제 주소를 찾아라"는 뜻이다.

api-gateway는 Eureka 서버(`localhost:8761`)에 "ACCOUNT-SERVICE의 실제 주소가 뭐야?"라고 물어본다. Eureka가 `http://localhost:8081`이라고 알려주면, api-gateway는 요청을 `http://localhost:8081/api/auth/signup`으로 그대로 전달한다. `account-service`가 처리하고 응답하면, api-gateway는 그 응답을 다시 브라우저에게 돌려준다.

브라우저 입장에서는 `localhost:8080`과 대화한 것처럼 보이지만, 실제로 요청을 처리한 것은 `localhost:8081`의 `account-service`다.

```
[브라우저]
  fetch("http://localhost:8080/api/auth/signup")
       ↓  일반 HTTP POST
[api-gateway :8080]
  1. /api/auth/** 패턴 매칭 확인
  2. 필터 없음 → JWT 검증 생략
  3. Eureka에 ACCOUNT-SERVICE 주소 질의
       ↓  Eureka 응답: localhost:8081
  4. 요청을 http://localhost:8081/api/auth/signup 으로 전달
       ↓
[account-service :8081]
  회원 저장 → 이메일 발송 → 201 응답
       ↓  응답 반환
[api-gateway :8080]
  account-service의 응답을 브라우저에게 그대로 전달
       ↓
[브라우저]
  fetch()의 response로 응답을 받음
```

### 인증이 필요한 API 흐름 - 내 정보 조회를 예시로

로그인 후 인증이 필요한 API를 호출할 때는 JWT를 헤더에 직접 담아야 한다.

```javascript
// 인증이 필요한 요청: localStorage에서 JWT를 꺼내 Authorization 헤더에 담는다
const response = await fetch(API_BASE + '/api/accounts/me', {
    headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('accessToken')
    }
});
```

api-gateway는 이 요청을 받아서 JWT가 필요한 경로인지 확인한다.

```yaml
routes:
  - id: account-service-private
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/accounts/**
    filters:
      - JwtAuthenticationFilter  # 이 필터가 실행됨
```

`JwtAuthenticationFilter`가 실행되면서 다음 과정이 순서대로 일어난다. 먼저 `Authorization` 헤더가 있는지 확인하고 없으면 즉시 401을 반환한다. 있다면 `Bearer ` 이후의 토큰 문자열을 추출해서 서명 검증과 만료 시간을 확인한다. 이 과정도 실패하면 401을 반환한다. 모두 통과하면 토큰 내부의 Claims를 읽어서 요청에 새 헤더를 붙인다.

```
[토큰 내부 Claims]          [api-gateway가 추가하는 헤더]
  sub: "123"          →     X-Account-Id: 123
  role: "ROLE_USER"   →     X-Account-Role: ROLE_USER
  nickname: "양균"    →     X-Account-Nickname: 양균
```

이 헤더들이 붙은 요청이 `account-service`로 전달된다. `account-service`는 JWT 라이브러리가 아예 없고, JWT를 읽을 줄도 모른다. 그냥 `X-Account-Id` 헤더만 꺼내서 "123번 사용자가 요청했구나"라고 처리한다.

```java
// account-service: JWT를 전혀 모른다. 헤더만 읽는다.
@GetMapping("/api/accounts/me")
public ResponseEntity<?> getMyInfo(
        @RequestHeader("X-Account-Id") Long accountId) {
    return ResponseEntity.ok(accountService.findById(accountId));
}
```

이 구조의 장점은 새로운 서비스(`study-service`, `event-service` 등)를 추가할 때 인증 코드를 전혀 작성하지 않아도 된다는 것이다. JWT 라이브러리 의존성도 추가할 필요가 없다. api-gateway가 이미 검증을 마쳤고, 그 결과가 헤더에 담겨 오기 때문이다.

### Eureka가 하는 역할

`lb://ACCOUNT-SERVICE`라는 주소에서 `ACCOUNT-SERVICE`는 실제 IP나 포트가 아니라 Eureka에 등록된 서비스의 이름이다. 각 서비스는 시작할 때 자신의 이름과 실제 주소(`localhost:8081`)를 Eureka에 등록한다.

```yaml
# account-service application.yml
spring:
  application:
    name: account-service  # Eureka에 이 이름으로 등록됨
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

api-gateway가 `lb://ACCOUNT-SERVICE`를 만나면 Eureka에 실제 주소를 질의하고, 그 주소로 요청을 전달한다. 덕분에 서비스의 포트나 주소가 바뀌어도 api-gateway의 설정을 수정할 필요가 없고, 같은 서비스를 여러 인스턴스로 띄우면 자동으로 로드밸런싱도 된다.

### 정리: 프론트엔드 개발자 관점

프론트엔드에서 API를 호출할 때 알아야 할 것은 딱 두 가지다. 첫째, 모든 `fetch()`는 `API_BASE`(api-gateway 주소)로 보낸다. `account-service`의 실제 주소인 `localhost:8081`로 직접 보내는 코드는 절대 작성하지 않는다. 둘째, 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 담는다. 그 이후는 api-gateway와 Eureka가 알아서 처리한다.

```javascript
// 올바른 패턴: 항상 API_BASE(api-gateway)로
fetch(API_BASE + '/api/auth/signup', { ... })       // 공개 API
fetch(API_BASE + '/api/accounts/me', {              // 인증 필요 API
    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('accessToken') }
})

// 잘못된 패턴: 서비스 직접 호출 (절대 하지 않음)
fetch('http://localhost:8081/api/auth/signup', { ... })  // X
```

---

## 13. 서비스별 역할 분담 요약

### frontend-service (:8090)

HTML 페이지를 서빙하는 역할만 한다. DB 연결이 없고, 비즈니스 로직이 없고, JWT를 처리하지 않는다. 모든 API 호출은 브라우저의 JavaScript가 `api-gateway`를 통해 직접 수행한다.

`AuthPageController`의 모든 핸들러는 `@GetMapping`만 존재하며, `apiBase`를 Model에 주입하고 템플릿 이름을 반환하는 일만 한다. 모노리틱의 컨트롤러가 했던 검증, 서비스 호출, 리다이렉트 결정 같은 역할은 모두 없다.

### api-gateway (:8080)

외부 요청의 단일 진입점이다. JWT 검증을 전담하며, 검증 성공 시 `X-Account-Id`, `X-Account-Role`, `X-Account-Nickname` 헤더를 붙여 각 서비스로 라우팅한다. 프론트엔드의 모든 `fetch()` 호출은 이 주소(`localhost:8080`)로 향한다.

### account-service (:8081)

회원 관리와 JWT 발급을 담당한다. JWT를 발급하지만 검증은 하지 않는다. 이메일 발송 시 링크의 호스트를 `app.host` 설정에서 가져오므로, 반드시 `frontend-service`의 주소(`http://localhost:8090`)로 설정되어야 한다.
