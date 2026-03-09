# 🔧 트러블슈팅: Thymeleaf @{...} 링크 표현식 파라미터 파싱 에러

## 📌 상황 요약

검색 페이지(`/search/study`)에 **필터 기능**(모집 중 / 진행 중)을 추가한 직후,
검색을 실행하면 **500 에러**가 발생했다.

필터 토글 버튼의 `th:href`에서 조건부로 쿼리 파라미터를 추가하기 위해
Thymeleaf의 `@{...}` 링크 표현식 안에서 삼항 연산자(`? :`)와 문자열 연결(`+`)을
함께 사용한 것이 원인이었다.

---

## 📋 에러 로그 전문

```
org.thymeleaf.exceptions.TemplateProcessingException: 
    Parameters in link expression 
    "@{'/search/study?sort=publishedDateTime,desc&keyword=' + ${keyword}
        + (${recruiting} ? '&recruiting=true' : '')
        + (${open} ? '&open=true' : '')}" 
    are incorrect: parameter name expression 
    "${open}? '&open=true' : ''" evaluated as null or empty string.
    
    (template: "search.html" - line XX, col XX)
```

에러 로그의 **WARN** 레벨에는 다음도 함께 찍혔다:

```
[THYMELEAF][WARNING] Deprecated unwrapped fragment expression "fragments.html :: head"
    found in template search.html, line X, col X. 
    Please use the complete syntax "~{fragments.html :: head}" instead.
```

---

## 🔍 디버깅 과정

### STEP 1: 에러 메시지를 정확히 해석하기

```
Parameters in link expression "@{...}" are incorrect: 
    parameter name expression "${open}? '&open=true' : ''" 
    evaluated as null or empty string.
```

이 에러를 해석하면:

| 부분 | 의미 |
|------|------|
| `Parameters in link expression` | `@{...}` 링크 표현식의 **파라미터 파싱**에서 에러 |
| `are incorrect` | 파라미터 이름/값이 올바르지 않다 |
| `parameter name expression` | Thymeleaf가 이 부분을 **파라미터 이름**으로 해석하려 했다 |
| `"${open}? '&open=true' : ''"` | 이 삼항 연산자를 파라미터 이름으로 인식 |
| `evaluated as null or empty string` | 파라미터 이름으로 평가했더니 null/빈 문자열 |

핵심은 이 부분이다:

> ❓ "왜 Thymeleaf가 삼항 연산자를 **파라미터 이름**으로 해석한 거지?"

---

### STEP 2: `@{...}` 링크 표현식의 파싱 규칙 이해하기

Thymeleaf의 `@{...}` 링크 표현식은 **두 가지 문법**을 지원한다:

#### 문법 A: 순수 문자열 방식

```html
@{/search/study?keyword=스프링&sort=publishedDateTime,desc}
```

이건 정적 URL이라 문제없다.

#### 문법 B: 파라미터 분리 방식

```html
@{/search/study(keyword=${keyword}, sort='publishedDateTime,desc')}
```

이건 Thymeleaf가 **괄호 `()` 안의 쉼표(`,`)를 파라미터 구분자**로 파싱한다.
결과적으로 `?keyword=값&sort=값` 형태의 URL을 생성해준다.

#### 핵심: `@{...}` 안에서 쉼표는 특별한 의미를 갖는다!

문제는 우리 코드가 **문법 A와 B를 혼용**했다는 점이다:

```html
<!-- 문제의 코드 -->
th:href="@{'/search/study?sort=publishedDateTime,desc&keyword=' 
    + ${keyword} 
    + (${recruiting} ? '&recruiting=true' : '')
    + (${open} ? '&open=true' : '')}"
```

Thymeleaf의 `@{...}` 파서는 이 코드를 볼 때:

1. `@{` 시작 → "링크 표현식이구나"
2. `...publishedDateTime` 까지 읽음
3. `,desc&keyword=...` → **","를 발견!** → "여기부터 파라미터 목록이구나"
4. `desc&keyword=' + ${keyword} + (${recruiting} ? '&recruiting=true' : '')` → 이걸 파라미터 이름으로 파싱
5. `, (${open} ? '&open=true' : '')` → **또 ","** → 이것도 파라미터 이름으로 파싱
6. `${open} ? '&open=true' : ''`를 파라미터 이름으로 평가 → **null/빈 문자열** → 💥 에러!

즉, `sort=publishedDateTime,desc`에서 **`,desc`의 쉼표**를 Thymeleaf가
URL 문자열의 일부가 아닌 **파라미터 구분자**로 오인한 것이다.

---

### STEP 3: 왜 기존 코드(필터 추가 전)는 문제가 없었는가?

필터 추가 **전** 코드를 보면:

```html
<!-- 필터 추가 전 — 정상 동작 -->
th:href="@{'/search/study?sort=publishedDateTime,desc&keyword=' + ${keyword}}"
```

이 코드에도 `,desc`가 있다. 그런데 왜 에러가 안 났을까?

이유는 **삼항 연산자가 없었기 때문**이다.

Thymeleaf의 `@{...}` 파서는 단순한 문자열 연결(`'...' + ${var}`)에서는
쉼표를 그대로 URL의 일부로 처리한다.
하지만 **삼항 연산자(`? :`)나 괄호 `()`가 등장하면**
파서가 "이건 파라미터 목록이다"라고 판단할 수 있는 여지가 생긴다.

정확히 말하면, `@{...}` 내부의 파싱 규칙은 다음과 같다:

```
@{URL부분(파라미터1=값1, 파라미터2=값2)}
```

파서가 괄호 `(`를 만나면 "여기부터 파라미터 목록"이라고 판단하고,
쉼표 `,`를 파라미터 구분자로 사용한다.

삼항 연산자의 `(`가 이 규칙을 트리거한 것이다:

```
@{'/search/study?sort=publishedDateTime,desc...' + (${open} ? '...' : '...')}
                                                    ^
                                        이 괄호가 파라미터 목록 시작으로 오인됨
```

---

### STEP 4: 해결 방법 — `@{...}` 대신 일반 문자열 연결 사용

`@{...}` 링크 표현식을 사용하지 않고,
`th:href`에 **일반 문자열 표현식**을 직접 작성한다:

```html
<!-- 변경 전 (에러) -->
th:href="@{'/search/study?sort=publishedDateTime,desc&keyword=' 
    + ${keyword} 
    + (${recruiting} ? '&recruiting=true' : '')
    + (${open} ? '&open=true' : '')}"

<!-- 변경 후 (정상) -->
th:href="'/search/study?sort=publishedDateTime,desc&amp;keyword=' 
    + ${keyword} 
    + (${recruiting} ? '&amp;recruiting=true' : '') 
    + (${open} ? '&amp;open=true' : '')"
```

#### 변경 포인트:

| 항목 | 변경 전 | 변경 후 |
|------|--------|--------|
| 표현식 종류 | `@{...}` (링크 표현식) | `'...'` (일반 문자열 표현식) |
| `&` 처리 | `&` 그대로 사용 | `&amp;`로 이스케이프 필수 |
| 파라미터 파싱 | Thymeleaf 파서가 개입 | 파서 개입 없음, 순수 문자열 |

---

### STEP 5: `&` vs `&amp;` — 왜 바꿔야 하는가?

`@{...}` 안에서는 `&`를 그대로 쓸 수 있었다.
Thymeleaf가 링크 표현식을 처리하면서 **자동으로 HTML 이스케이프**를 해주기 때문이다.

하지만 일반 문자열 표현식에서는 결과가 **HTML 속성값에 직접 삽입**된다.
HTML에서 `&`는 특수문자이므로 `&amp;`로 이스케이프해야 한다.

```html
<!-- HTML 속성값에서의 & 처리 -->

<!-- 잘못된 HTML -->
<a href="/search?keyword=스프링&sort=date">  ← 브라우저가 &sort를 HTML 엔티티로 오해할 수 있음

<!-- 올바른 HTML -->
<a href="/search?keyword=스프링&amp;sort=date">  ← 브라우저가 정확히 &를 URL의 일부로 인식
```

Thymeleaf 일반 문자열에서도 마찬가지:

```html
<!-- Thymeleaf가 렌더링하면 -->
th:href="'/search?a=1&amp;b=2'"
<!-- 결과 HTML: href="/search?a=1&b=2" ← 정상! -->

th:href="'/search?a=1&b=2'"
<!-- 결과: 예측 불가능 — &b가 HTML 엔티티 참조로 해석될 수 있음 -->
```

---

### STEP 6: 추가 수정 — Deprecated fragment 표현식 경고

에러와 함께 WARN 레벨로 찍힌 경고도 있었다:

```
[THYMELEAF][WARNING] Deprecated unwrapped fragment expression 
    "fragments.html :: head" found in template search.html
    Please use the complete syntax "~{fragments.html :: head}" instead.
```

이 경고는 에러의 직접적인 원인은 아니지만,
**Thymeleaf 3.x에서 향후 제거될 문법**을 사용하고 있다는 뜻이다.

```html
<!-- 변경 전 (Deprecated — 언젠가 제거될 문법) -->
<head th:replace="fragments.html :: head"></head>
<div th:replace="fragments.html :: main-nav"></div>
<div th:replace="fragments.html :: footer"></div>

<!-- 변경 후 (권장 — 정식 문법) -->
<head th:replace="~{fragments.html :: head}"></head>
<div th:replace="~{fragments.html :: main-nav}"></div>
<div th:replace="~{fragments.html :: footer}"></div>
```

`~{...}`는 Thymeleaf 3.x에서 도입된 **fragment 표현식** 공식 문법이다.
`~{...}` 없이 쓰는 건 Thymeleaf 2.x 호환 모드이며,
향후 버전에서 제거될 예정이라는 경고이다.

이번에 search.html의 모든 fragment 참조를 `~{...}` 문법으로 통일했다.

---

## 📊 전체 에러 흐름 다이어그램

```
검색 필터 기능 추가 (search.html에 recruiting/open 파라미터 토글 버튼 추가)
    │
    ▼
th:href="@{'/search/study?sort=publishedDateTime,desc&keyword=' 
    + ${keyword} + (${open} ? '&open=true' : '')}" 코드 작성
    │
    ▼
Thymeleaf 파서가 @{...} 안의 내용을 파싱
    │
    ├─ 삼항 연산자의 괄호 () 발견
    │
    ├─ "파라미터 목록이구나!" → 쉼표(,)를 파라미터 구분자로 인식
    │
    ├─ sort=publishedDateTime 까지 URL로 인식
    │
    ├─ ,desc&keyword=... 부분부터 파라미터 이름으로 파싱 시도
    │
    ├─ ${open}? '&open=true' : '' → 파라미터 이름으로 평가 → null/빈 문자열
    │
    ▼
💥 "parameter name expression evaluated as null or empty string"
```

---

## 🔍 `@{...}` vs 일반 문자열 — 언제 어떤 걸 써야 하는가?

### `@{...}` 링크 표현식을 쓰면 좋은 경우:

```html
<!-- 단순 URL + 파라미터 분리 방식 → 깔끔하고 안전 -->
<a th:href="@{/search/study(keyword=${keyword}, sort='publishedDateTime,desc')}">
<!-- 결과: /search/study?keyword=스프링&sort=publishedDateTime,desc -->
```

**장점:**
- `&` 이스케이프를 자동으로 해준다
- context path를 자동으로 붙여준다 (`server.servlet.context-path` 설정 시)
- 파라미터 이름/값을 깔끔하게 분리할 수 있다

### `@{...}`를 피해야 하는 경우:

```html
<!-- 조건부 파라미터가 필요할 때 → @{...} 내부에서 삼항 연산자 사용 시 파싱 충돌 -->
<!-- ❌ 위험 -->
<a th:href="@{'/path?a=1,2' + (${flag} ? '&b=3' : '')}">

<!-- ✅ 안전 → 일반 문자열 사용 -->
<a th:href="'/path?a=1,2' + (${flag} ? '&amp;b=3' : '')">
```

### 판단 기준 정리:

| 상황 | 추천 방식 |
|------|----------|
| 정적 URL (`/path`) | `@{/path}` |
| 고정 파라미터 | `@{/path(key=${val})}` |
| URL에 쉼표가 포함 (`sort=date,desc`) | 일반 문자열 또는 파라미터 분리 방식 |
| 조건부 파라미터 (삼항 연산자) | **일반 문자열** (th:href="'...' + ...") |
| 복잡한 URL 조합 | **일반 문자열** |

---

## 📁 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `search.html` | 모든 `th:href`의 `@{...}` → 일반 문자열 연결로 변경, `&` → `&amp;` 이스케이프 |
| `search.html` | 모든 `th:replace`에 `~{...}` fragment 표현식 문법 적용 |

---

## ✅ 수정 사항 요약

### 수정 1: 필터 토글 버튼의 URL

```html
<!-- 변경 전 (에러) -->
<a th:href="@{'/search/study?sort=publishedDateTime,desc&keyword=' + ${keyword}
    + (${recruiting} ? '&recruiting=true' : '')
    + (${open} ? '&open=true' : '')}">

<!-- 변경 후 (정상) -->
<a th:href="'/search/study?sort=publishedDateTime,desc&amp;keyword=' + ${keyword}
    + (${recruiting} ? '&amp;recruiting=true' : '')
    + (${open} ? '&amp;open=true' : '')">
```

### 수정 2: 정렬 드롭다운의 URL

```html
<!-- 변경 전 (에러) -->
<a th:href="@{'/search/study?sort=memberCount,desc&keyword=' + ${keyword}
    + (${recruiting} ? '&recruiting=true' : '')
    + (${open} ? '&open=true' : '')}">

<!-- 변경 후 (정상) -->
<a th:href="'/search/study?sort=memberCount,desc&amp;keyword=' + ${keyword}
    + (${recruiting} ? '&amp;recruiting=true' : '')
    + (${open} ? '&amp;open=true' : '')">
```

### 수정 3: 페이지네이션의 URL (동일 패턴)

```html
<!-- 변경 전 (에러) -->
<a th:href="@{'/search/study?keyword=' + ${keyword} + '&sort=' + ${sortProperty} + ',desc&page=' + ${i}
    + (${recruiting} ? '&recruiting=true' : '')
    + (${open} ? '&open=true' : '')}">

<!-- 변경 후 (정상) -->
<a th:href="'/search/study?keyword=' + ${keyword} + '&amp;sort=' + ${sortProperty} + ',desc&amp;page=' + ${i}
    + (${recruiting} ? '&amp;recruiting=true' : '')
    + (${open} ? '&amp;open=true' : '')">
```

### 수정 4: Fragment 표현식 문법 통일

```html
<!-- 변경 전 (Deprecated 경고) -->
<head th:replace="fragments.html :: head"></head>
<div th:replace="fragments.html :: main-nav"></div>
<div th:replace="fragments.html :: study-list (studyList=${studyPage.content})"></div>
<div th:replace="fragments.html :: footer"></div>

<!-- 변경 후 (권장 문법) -->
<head th:replace="~{fragments.html :: head}"></head>
<div th:replace="~{fragments.html :: main-nav}"></div>
<div th:replace="~{fragments.html :: study-list (studyList=${studyPage.content})}"></div>
<div th:replace="~{fragments.html :: footer}"></div>
```

---

## 🎓 이번 트러블슈팅에서 배운 것

### 1. `@{...}` 링크 표현식은 내부에서 쉼표와 괄호를 특별하게 처리한다

Thymeleaf의 `@{...}`는 단순한 문자열 래퍼가 아니다.
내부적으로 **URL 파싱 규칙**이 적용되며, 쉼표(`,`)를 파라미터 구분자로,
괄호(`()`)를 파라미터 목록의 시작으로 해석할 수 있다.

### 2. 조건부 파라미터가 필요하면 `@{...}`을 피하라

삼항 연산자(`? :`)나 동적 문자열 조합이 필요한 URL은
`@{...}` 대신 **일반 문자열 표현식**을 사용하는 것이 안전하다.

### 3. `&amp;`를 잊지 마라

`@{...}` 밖에서 URL을 조합할 때는 `&` 대신 `&amp;`를 써야 한다.
HTML 속성값 안에서 `&`는 HTML 엔티티 참조의 시작으로 해석될 수 있기 때문이다.
이건 Thymeleaf 문제가 아니라 **HTML 표준 규칙**이다.

### 4. Deprecated 경고는 미리 대응하라

`th:replace="fragments.html :: head"` 같은 구식 문법은 현재는 동작하지만
향후 Thymeleaf 버전에서 **제거될 예정**이다.
`~{...}`로 미리 통일해두면 나중에 일괄 수정할 필요가 없다.

### 5. 에러가 "파라미터 이름이 null"이라고 하면, 파싱 규칙을 의심하라

에러 메시지만 보면 "파라미터 이름이 비어있다"로 보이지만,
실제 원인은 **파서가 의도하지 않은 부분을 파라미터로 해석한 것**이다.
에러 메시지의 표면적 의미와 실제 원인이 다를 수 있다.

---

## 🔗 관련 트러블슈팅

- **TroubleShooting_004**: Lazy Loading + PostgreSQL LOB 에러
  → 같은 search.html에서 발생한 또 다른 에러 (이번에는 프론트엔드 문제, 004는 백엔드 문제)
  → 검색 기능 개선 작업 중 연속으로 발생한 에러 시리즈

- **TroubleShooting_001**: ExceptionAdvice 파라미터 주입 실패
  → "에러 메시지의 표면적 의미와 실제 원인이 다를 수 있다"는 교훈이 동일
