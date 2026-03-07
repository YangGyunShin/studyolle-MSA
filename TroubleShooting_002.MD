# 🔧 트러블슈팅: 로그인 후에도 "회원 가입" 버튼이 보이는 문제 (@CurrentUser 누락)

## 📌 상황 요약

회원가입 후 로그인된 상태인데, 홈 화면(`/`)에 접속하면
**비로그인 사용자용 페이지(index.html)**가 렌더링되어 "회원 가입" 버튼이 보였다.

그런데 네비게이션 바에는 닉네임(`test`)이 표시되고,
"스터디 개설" 버튼도 정상적으로 보이는 **모순적인 상태**였다.

추가로, 이메일 인증을 하지 않은 상태인데
이메일 인증 안내 배너도 표시되지 않았다.

---

## 🖥️ 증상 분석

화면을 두 영역으로 나눠 보면 모순이 드러난다:

**네비게이션 바 (상단)**
- `test` 닉네임 표시됨
- "스터디 개설" 버튼 표시됨
- → **로그인 상태로 인식하고 있음** ✅

**메인 콘텐츠 (중앙)**
- "스터디올래 / 태그와 지역 기반으로 스터디를 찾고 참여하세요." 문구 표시
- "회원 가입" 버튼 표시
- → **비로그인 상태로 인식하고 있음** ❌

**모순 포인트:**
- 네비게이션 바 → 로그인 상태 (닉네임 + 스터디 개설 버튼 표시)
- 메인 콘텐츠 → 비로그인 상태 (회원 가입 버튼 표시)

---

## 🔍 디버깅 과정

### STEP 1: 홈 화면이 어떤 뷰를 반환하는지 확인

`MainController.java`의 `home()` 메서드를 살펴보자:

```java
@GetMapping("/")
public String home(Account account, Model model) {
    if (account != null) {
        // 로그인 상태 → index-after-login 뷰
        Account accountLoaded = accountRepository
            .findAccountWithTagsAndZonesById(account.getId());
        model.addAttribute(accountLoaded);
        ...
        return "index-after-login";
    }

    // 비로그인 상태 → index 뷰
    model.addAttribute("studyList", ...);
    return "index";  // ← 💥 여기로 빠지고 있다!
}
```

"회원 가입" 버튼이 보인다는 건 `return "index";`가 실행되었다는 뜻이다.
즉, `account != null` 조건이 `false`였다는 의미인데...

> ❓ 분명 로그인한 상태인데, 왜 `account`가 `null`로 판정되는 걸까?

---

### STEP 2: 네비게이션 바와 메인 콘텐츠가 다른 결과를 보여주는 이유

여기서 핵심적인 단서가 있다.

**네비게이션 바**는 로그인 상태를 정확하게 인식하고 있다 (닉네임 표시, 스터디 개설 버튼).
그런데 **메인 콘텐츠**는 비로그인으로 판단하고 있다.

네비게이션 바(`fragments.html`)는 **Thymeleaf의 Spring Security 통합**을 사용한다:

```html
<!-- fragments.html의 네비게이션 바 (일부) -->
<div sec:authorize="isAuthenticated()">
    <!-- Spring Security의 SecurityContext에서 직접 인증 정보를 확인 -->
    <!-- → 로그인 상태면 이 블록이 렌더링됨 -->
</div>
```

반면 `MainController.home()`은 **메서드 파라미터**로 `Account`를 받아서 판단한다:

```java
public String home(Account account, Model model) {
    if (account != null) { ... }  // ← 이 방식으로 로그인 여부를 판단
}
```

> 💡 **같은 요청인데 두 곳에서 인증 상태를 다르게 판단하고 있다!**
>
> - 네비게이션 바: SecurityContext에서 직접 확인 → ✅ 로그인 인식
> - MainController: 메서드 파라미터 `Account account` → ❌ 로그인 미인식

이 차이의 원인은 **`@CurrentUser` 어노테이션의 유무**에 있다.

---

### STEP 3: `@CurrentUser`가 없으면 무슨 일이 벌어지는가?

프로젝트의 다른 컨트롤러들과 비교해보자:

```java
// ✅ ProfileController — @CurrentUser 있음
@GetMapping("/profile/{nickname}")
public String viewProfile(@CurrentUser Account account, ...) { ... }

// ✅ TagZoneController — @CurrentUser 있음
@GetMapping("/settings/tags")
public String updateTags(@CurrentUser Account account, ...) { ... }

// ❌ MainController — @CurrentUser 없음!
@GetMapping("/")
public String home(Account account, Model model) { ... }
```

`MainController`만 `@CurrentUser`가 빠져 있다!

#### `@CurrentUser`가 하는 일

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? null : account")
public @interface CurrentUser { }
```

이 어노테이션은 내부적으로 `@AuthenticationPrincipal`을 사용하여:

1. **SecurityContext**에서 현재 인증된 사용자(principal)를 꺼냄
2. principal이 `anonymousUser`(비로그인)이면 → `null` 반환
3. principal이 `UserAccount`이면 → `userAccount.getAccount()` 호출 → `Account` 반환

#### `@CurrentUser`가 없으면?

**Spring MVC는 `Account account` 파라미터를 전혀 다른 방식으로 해석한다.**

`@CurrentUser`(= `@AuthenticationPrincipal`)가 없으면,
Spring MVC의 기본 파라미터 처리 규칙이 적용된다:

```
Spring MVC의 파라미터 해석 순서:
    │
    ├─ @RequestParam이 붙어 있나? → No
    ├─ @PathVariable이 붙어 있나? → No
    ├─ @ModelAttribute가 붙어 있나? → No (명시적으로)
    ├─ @AuthenticationPrincipal이 붙어 있나? → No
    │
    └─ 어떤 어노테이션도 없는 복합 객체?
       → @ModelAttribute로 간주! (Spring의 기본 규칙)
```

즉, **`Account account`를 `@ModelAttribute Account account`처럼 취급**한다.

`@ModelAttribute`는 요청 파라미터에서 객체의 필드를 바인딩하는 방식이다:

```
GET / 요청 (파라미터 없음)
    │
    ▼
Spring: "Account를 @ModelAttribute로 처리해야지"
    │
    ├─ 1. new Account() 호출 → 빈 Account 객체 생성
    ├─ 2. 요청 파라미터에서 필드 바인딩 시도
    │      → 파라미터가 없으므로 모든 필드가 null/기본값
    ├─ 3. 결과: 빈 Account 객체 (id=null, email=null, nickname=null, ...)
    │
    ▼
account = new Account()  ← null이 아닌 빈 객체!
```

#### 핵심: `null`이 아니라 "빈 객체"

```java
public String home(Account account, Model model) {
    // @CurrentUser가 있을 때:
    //   로그인 O → SecurityContext에서 가져온 실제 Account (id=1, email="test@test.com", ...)
    //   로그인 X → null
    
    // @CurrentUser가 없을 때:
    //   항상 → new Account() (id=null, email=null, nickname=null, ...)
    //   ⚠️ null이 아님! 빈 객체!
    
    if (account != null) {  
        // @CurrentUser 없으면 항상 여기로 들어옴 (빈 객체니까 null이 아님)
        // 하지만 account.getId() == null이므로...
        
        Account accountLoaded = accountRepository
            .findAccountWithTagsAndZonesById(account.getId());  
        // → findAccountWithTagsAndZonesById(null) 
        // → 결과: null 반환
        
        model.addAttribute(accountLoaded);  // 💥 model.addAttribute(null) → 에러!
    }
}
```

---

### STEP 4: 그런데 왜 에러 페이지가 아니라 "회원 가입" 페이지가 보였을까?

TroubleShooting_001에서 이미 `ExceptionAdvice`와 `MainController`를 수정했다면,
다음 두 가지 수정이 적용된 상태일 수 있다:

**가능성 A: MainController에 null 방어 코드가 이미 적용된 경우**

```java
Account accountLoaded = accountRepository
    .findAccountWithTagsAndZonesById(account.getId());

if (accountLoaded == null) {
    return "index";  // ← 비로그인 화면으로 fallback
}
```

이 경우 `accountLoaded`가 null이면 에러 대신 `"index"` 뷰를 반환한다.
결과적으로 로그인한 사용자에게 비로그인 화면("회원 가입" 버튼)이 보이게 된다.

**가능성 B: `account.getId()`가 null이어도 JPA 쿼리가 빈 결과를 반환한 경우**

JPA의 `findById(null)` 동작은 구현에 따라 다르다.
일부 경우 예외를 던지지 않고 `null` 또는 빈 `Optional`을 반환할 수 있다.

어느 쪽이든, **근본 원인은 `@CurrentUser` 누락으로 인해 빈 Account 객체가 주입된 것**이다.

---

## 📊 정상 흐름 vs 버그 흐름 비교

### ✅ 정상 흐름 (@CurrentUser가 있을 때)

```
사용자가 / (홈) 접속 (로그인 상태)
    │
    ▼
@CurrentUser Account account
    │
    ├─ SecurityContext에서 인증 정보 조회
    ├─ UserAccount.getAccount() 호출
    ├─ 결과: Account(id=1, email="test@test.com", nickname="test", ...)
    │
    ▼
account != null → true (실제 Account 객체)
    │
    ▼
accountRepository.findAccountWithTagsAndZonesById(1)
    │
    ├─ 결과: 태그/지역이 함께 로딩된 Account 객체
    │
    ▼
return "index-after-login"  ✅ 올바른 뷰!
```

### ❌ 버그 흐름 (@CurrentUser가 없을 때)

```
사용자가 / (홈) 접속 (로그인 상태)
    │
    ▼
Account account (@ModelAttribute로 처리됨)
    │
    ├─ new Account() 생성
    ├─ 요청 파라미터에서 바인딩 시도 → 파라미터 없음
    ├─ 결과: Account(id=null, email=null, nickname=null, ...)
    │
    ▼
account != null → true (빈 객체이므로 null이 아님!)
    │
    ▼
accountRepository.findAccountWithTagsAndZonesById(null)
    │
    ├─ 결과: null (id가 null이니 조회 실패)
    │
    ▼
accountLoaded == null → fallback
    │
    ▼
return "index"  ❌ 비로그인 화면이 보임!
```

---

## ✅ 수정 사항

### MainController.java — `@CurrentUser` 추가

```java
// 변경 전 (문제 코드)
@GetMapping("/")
public String home(Account account, Model model) { ... }

// 변경 후 (수정 코드)
@GetMapping("/")
public String home(@CurrentUser Account account, Model model) { ... }
```

**이 한 줄의 변경**으로:
- 로그인 상태 → SecurityContext에서 실제 Account 주입 → `index-after-login` 렌더링
- 비로그인 상태 → null 주입 → `index` 렌더링
- 이메일 미인증 경고 배너도 정상 표시

---

## 🎓 이번 트러블슈팅에서 배운 것

### 1. `@CurrentUser` 누락은 에러가 아니라 "잘못된 동작"을 만든다

`@CurrentUser`가 없어도 컴파일 에러가 나지 않고, 런타임 에러도 (수정 후에는) 나지 않는다.
대신 **조용히 잘못된 동작**을 한다 — 빈 Account 객체가 주입되어 로직이 엉뚱하게 흘러간다.
이런 버그가 가장 찾기 어렵다.

### 2. "null이 아닌 빈 객체" 함정

많은 개발자가 `if (account != null)`로 충분하다고 생각하지만,
Spring MVC의 `@ModelAttribute` 기본 동작은 **빈 객체를 생성**하므로
`null` 체크만으로는 "유효한 데이터가 있는지"를 판별할 수 없다.

### 3. 같은 화면에서 다른 결과가 보이면 — 데이터 소스를 의심하자

네비게이션 바(SecurityContext 직접 참조)와 메인 콘텐츠(컨트롤러 파라미터)가
**서로 다른 소스에서 인증 정보를 가져오고 있었기 때문에** 모순이 발생했다.
UI의 서로 다른 영역이 다른 결과를 보여주면, 각 영역이 어디서 데이터를 가져오는지 확인하자.

### 4. 어노테이션 하나가 전체 동작을 바꿀 수 있다

Spring MVC에서 파라미터에 붙이는 어노테이션(`@CurrentUser`, `@RequestParam`,
`@PathVariable`, `@ModelAttribute` 등)은 **파라미터를 어디서 가져올지**를 결정한다.
어노테이션이 없으면 Spring은 자체 규칙에 따라 추론하는데,
이 추론이 개발자의 의도와 다를 수 있다. **명시적으로 어노테이션을 붙이는 습관**이 중요하다.