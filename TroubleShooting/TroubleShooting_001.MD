# 🔧 트러블슈팅: ExceptionAdvice 파라미터 주입 실패 + MainController null 에러

## 📌 상황 요약

애플리케이션을 실행하고 홈 화면(`/`)에 접속했더니 에러가 발생했다.
로그에는 **두 개의 에러**가 출력되었다.

---

## 📋 에러 로그 전문

### 에러 ①: ExceptionAdvice 파라미터 주입 실패

```
java.lang.IllegalStateException: 
  Could not resolve parameter [0] in public java.lang.String 
  com.studyolle.modules.main.ExceptionAdvice.handleRuntimeException(
    com.studyolle.modules.account.entity.Account,
    jakarta.servlet.http.HttpServletRequest,
    java.lang.RuntimeException
  ): No suitable resolver
```

### 에러 ②: MainController에서 null 객체를 Model에 추가

```
java.lang.IllegalArgumentException: Model object must not be null
  at com.studyolle.modules.main.MainController.home(MainController.java:125)
```

---

## 🔍 디버깅 과정

### STEP 1: 에러가 2개? — 독립인가, 연쇄인가?

에러 로그를 처음 보면 당황스러울 수 있다. **에러가 2개**나 있으니까.

여기서 가장 먼저 판단해야 할 것은:

> ❓ "이 두 에러는 각각 독립적으로 발생한 건가, 아니면 하나가 다른 하나를 유발한 건가?"

이걸 판단하려면 **스택트레이스의 발생 순서**를 봐야 한다.

#### 📖 스택트레이스 읽는 법

스택트레이스는 **"호출 순서의 역순"**으로 출력된다.
맨 아래가 가장 먼저 실행된 코드이고, 맨 위가 최종적으로 에러가 터진 지점이다.

```
// 맨 위 = 에러가 최종적으로 터진 곳
java.lang.IllegalStateException: Could not resolve parameter [0]...
    at InvocableHandlerMethod.getMethodArgumentValues(...)   ← 여기서 터짐
    at ExceptionHandlerExceptionResolver.doResolve...(...)   ← 예외 처리기가 호출됨
    ...

// 이 에러가 발생하기 전에 먼저 발생한 원인 에러
Caused by 또는 별도 로그:
java.lang.IllegalArgumentException: Model object must not be null
    at MainController.home(MainController.java:125)          ← 여기가 최초 원인!
```

#### ✅ 판단 결과: 연쇄 에러다!

발생 순서를 정리하면:

```
1️⃣  MainController.home()에서 RuntimeException 발생 (원인)
         ↓
2️⃣  Spring이 예외를 처리하기 위해 ExceptionAdvice를 호출하려 함
         ↓
3️⃣  그런데 ExceptionAdvice 자체에서도 에러 발생! (연쇄)
         ↓
4️⃣  예외 처리기가 예외를 처리하다 또 예외가 발생한 상황
    → Spring이 최종 에러 페이지를 표시
```

> 💡 **핵심 교훈**: 에러가 여러 개 보이면, 먼저 **"원인 에러(Root Cause)"**를 찾자.
> 나머지는 원인 에러의 결과로 발생한 **"연쇄 에러"**일 가능성이 높다.

---

### STEP 2: 원인 에러(Root Cause) — MainController.home()

이제 원인 에러부터 분석해보자.

```
java.lang.IllegalArgumentException: Model object must not be null
    at MainController.home(MainController.java:125)
```

**"Model object must not be null"** — model에 null 객체를 넣으려 했다는 뜻이다.

MainController.java의 해당 라인(125줄 부근)을 보면:

```java
@GetMapping("/")
public String home(Account account, Model model) {
    if (account != null) {
        Account accountLoaded = accountRepository
            .findAccountWithTagsAndZonesById(account.getId());
        
        model.addAttribute(accountLoaded);  // ← 💥 여기서 터짐! (125번째 줄)
        ...
    }
}
```

#### 🤔 왜 null이 됐을까?

코드를 위에서 아래로 따라가 보자:

1. `account != null` → 로그인 상태이므로 `if` 블록 안으로 들어온다.
2. `accountRepository.findAccountWithTagsAndZonesById(account.getId())` 실행
3. 이 메서드가 **null을 반환**한다.
4. `model.addAttribute(null)` → 💥 에러!

`model.addAttribute(Object)`는 키 이름 없이 객체만 전달하는 오버로딩인데,
**내부적으로 객체의 클래스명에서 키 이름을 자동 생성**한다.
null이면 클래스명을 알 수 없으므로 `IllegalArgumentException`을 던지는 것이다.

```java
// Spring의 ModelMap.addAttribute(Object) 내부 구현 (간략화)
public ModelMap addAttribute(Object attributeValue) {
    Assert.notNull(attributeValue, "Model object must not be null");  // ← 여기서 터짐
    // attributeValue가 Account 타입이면 키 이름을 "account"로 자동 생성
    ...
}
```

#### 💊 수정 방법: null 방어 코드 추가

```java
@GetMapping("/")
public String home(Account account, Model model) {
    if (account != null) {
        Account accountLoaded = accountRepository
            .findAccountWithTagsAndZonesById(account.getId());
        
        // ✅ null 체크 추가 — 조회 실패 시 비로그인 화면으로 fallback
        if (accountLoaded == null) {
            return "index";
        }
        
        model.addAttribute(accountLoaded);
        ...
    }
}
```

---

### STEP 3: 연쇄 에러 — ExceptionAdvice의 파라미터 주입 실패

원인 에러(MainController의 RuntimeException)가 발생하면,
Spring은 이 예외를 처리할 `@ExceptionHandler`를 찾는다.

우리 프로젝트에는 `ExceptionAdvice`가 있으므로 Spring이 이걸 호출하려 한다:

```java
@ControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler
    public String handleRuntimeException(Account account,         // ← [0] 💥
                                         HttpServletRequest req,  // ← [1]
                                         RuntimeException e) {    // ← [2]
        ...
    }
}
```

그런데 에러가 발생한다:

```
Could not resolve parameter [0]: No suitable resolver
```

**"파라미터 [0]번(`Account account`)을 주입할 방법을 찾을 수 없다"**는 뜻이다.

#### 🤔 왜 `Account`를 주입할 수 없는 걸까?

이 부분이 **이번 트러블슈팅의 핵심 학습 포인트**이다.

일반 `@Controller` 메서드와 `@ExceptionHandler` 메서드는
**파라미터 주입 규칙이 다르다.**

#### 📊 일반 컨트롤러 vs ExceptionHandler 비교

| 구분 | 일반 `@GetMapping` 등 | `@ExceptionHandler` |
|------|----------------------|---------------------|
| **파라미터 해석 체계** | `HandlerMethodArgumentResolver` 전체 사용 가능 | **제한된 리졸버만** 사용 가능 |
| **커스텀 Account 주입** | `@CurrentUser` + 커스텀 리졸버로 **가능** ✅ | **불가능** ❌ |
| **주입 가능한 것들** | Model, @RequestParam, @PathVariable, @CurrentUser 커스텀 타입 등 | Exception, HttpServletRequest, HttpServletResponse 등 **Spring이 기본 제공하는 것만** |

#### 📖 왜 이런 차이가 있을까?

Spring MVC의 요청 처리 흐름을 살펴보면 이해가 된다:

```
사용자 요청
    ↓
DispatcherServlet
    ↓
HandlerAdapter → ArgumentResolver 체인 실행
    ↓                                           ← 여기서 @CurrentUser, Account 등이 해석됨
Controller 메서드 실행
    ↓
💥 예외 발생!
    ↓
ExceptionHandlerExceptionResolver             ← 완전히 다른 처리 경로로 진입
    ↓
@ExceptionHandler 메서드 호출                   ← 여기서는 별도의 "제한된" 리졸버를 사용
```

일반 컨트롤러 메서드를 호출할 때는 `HandlerAdapter`가 등록된 모든 `ArgumentResolver`를 사용한다.
여기에는 우리가 만든 커스텀 리졸버(@CurrentUser용)도 포함되어 있다.

하지만 `@ExceptionHandler` 메서드는 `ExceptionHandlerExceptionResolver`라는
**별도의 경로**에서 호출된다. 이 경로에서는 커스텀 `ArgumentResolver`가 적용되지 않는다.

#### 📋 @ExceptionHandler에서 주입 가능한 파라미터 목록

Spring 공식 문서 기준, `@ExceptionHandler` 메서드가 받을 수 있는 파라미터는 다음과 같다:

- `Exception` (또는 그 하위 타입) — 발생한 예외 객체
- `HttpServletRequest`, `HttpServletResponse` — HTTP 요청/응답
- `WebRequest`, `NativeWebRequest` — Spring의 요청 래퍼
- `Locale` — 현재 로케일
- `OutputStream`, `Writer` — 응답 스트림에 직접 쓰기
- `Model` — 에러 뷰에 데이터 전달 (단, 뷰 이름을 반환할 때만)

**이 목록에 `Account`(커스텀 도메인 객체)는 없다!**
그래서 "No suitable resolver"(적절한 리졸버 없음) 에러가 발생한 것이다.

#### 💊 수정 방법: SecurityContextHolder에서 직접 꺼내기

`@ExceptionHandler`에서는 파라미터로 `Account`를 받을 수 없으니,
**SecurityContextHolder에서 직접** 인증 정보를 꺼내야 한다.

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.studyolle.modules.account.security.UserAccount;

@Slf4j
@ControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler
    public String handleRuntimeException(HttpServletRequest req, RuntimeException e) {
        // ✅ SecurityContextHolder에서 직접 인증 정보를 꺼냄
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null 
                && authentication.getPrincipal() instanceof UserAccount userAccount) {
            Account account = userAccount.getAccount();
            log.info("'{}' requested '{}'", account.getNickname(), req.getRequestURI());
        } else {
            log.info("requested '{}'", req.getRequestURI());
        }

        log.error("bad request", e);
        return "error";
    }
}
```

**변경 포인트:**

- `Account account` 파라미터 **제거**
- 메서드 내부에서 `SecurityContextHolder.getContext().getAuthentication()`으로 직접 조회
- `getPrincipal()`이 `UserAccount` 타입인지 확인 후 `Account`를 추출

---

### STEP 4: 왜 기존 코드는 동작했었을까? (추가 배경 지식)

여기서 의문이 생길 수 있다:

> "기존에 이 코드가 잘 동작했다면, 왜 지금은 안 되는 거지?"

가능한 원인들:

1. **기존에는 `findAccountWithTagsAndZonesById()`가 null을 반환한 적이 없었다.**
   즉, MainController.home()에서 `RuntimeException`이 발생한 적이 없었기 때문에
   ExceptionAdvice가 호출될 일이 없었다. 에러가 숨어있었던 것이다.

2. **Spring Boot 또는 Spring Security 버전 업그레이드.**
   일부 버전에서는 `@ExceptionHandler`에서 커스텀 파라미터 해석이 관대하게 동작했을 수 있다.
   버전이 올라가면서 엄격해진 경우도 있다.

> 💡 **핵심 교훈**: 예외 처리 코드(`@ExceptionHandler`, `@ControllerAdvice`)는
> 평소에는 실행되지 않다가 **예외가 발생했을 때만** 실행된다.
> 그래서 예외 처리 코드 자체에 버그가 있어도 오랫동안 발견되지 않을 수 있다.
> **예외 처리 코드도 반드시 테스트해야 하는 이유!**

---

## 📊 전체 에러 흐름 다이어그램

```
사용자가 / (홈) 접속
    │
    ▼
MainController.home() 실행
    │
    ├─ account != null (로그인 상태)
    │
    ▼
accountRepository.findAccountWithTagsAndZonesById(id)
    │
    ├─ 결과: null 반환 (어떤 이유로 조회 실패)
    │
    ▼
model.addAttribute(null)
    │
    ├─ 💥 IllegalArgumentException: "Model object must not be null"
    │
    ▼
Spring이 예외를 잡아서 ExceptionAdvice로 전달하려 함
    │
    ▼
ExceptionAdvice.handleRuntimeException(Account, HttpServletRequest, RuntimeException)
    │
    ├─ Spring이 첫 번째 파라미터 Account를 주입하려고 시도
    │
    ├─ @ExceptionHandler에서는 커스텀 타입 주입 불가!
    │
    ├─ 💥 IllegalStateException: "Could not resolve parameter [0]"
    │
    ▼
예외 처리기마저 실패 → Spring 기본 에러 응답 반환
```

---

## ✅ 수정 사항 요약

### 수정 1: MainController.java — null 방어 코드 추가

```java
// 변경 전
Account accountLoaded = accountRepository.findAccountWithTagsAndZonesById(account.getId());
model.addAttribute(accountLoaded);

// 변경 후
Account accountLoaded = accountRepository.findAccountWithTagsAndZonesById(account.getId());
if (accountLoaded == null) {
    return "index";  // 조회 실패 시 비로그인 화면으로 안전하게 fallback
}
model.addAttribute(accountLoaded);
```

### 수정 2: ExceptionAdvice.java — Account 파라미터 제거 + SecurityContextHolder 사용

```java
// 변경 전 (문제 코드)
@ExceptionHandler
public String handleRuntimeException(Account account, 
                                     HttpServletRequest req, 
                                     RuntimeException e) {
    if (account != null) {
        log.info("'{}' requested '{}'", account.getNickname(), req.getRequestURI());
    }
    ...
}

// 변경 후 (수정 코드)
@ExceptionHandler
public String handleRuntimeException(HttpServletRequest req, 
                                     RuntimeException e) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null 
            && authentication.getPrincipal() instanceof UserAccount userAccount) {
        Account account = userAccount.getAccount();
        log.info("'{}' requested '{}'", account.getNickname(), req.getRequestURI());
    } else {
        log.info("requested '{}'", req.getRequestURI());
    }
    
    log.error("bad request", e);
    return "error";
}
```

추가 import:

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.studyolle.modules.account.security.UserAccount;
```

---

## 🎓 이번 트러블슈팅에서 배운 것

### 1. 에러 로그가 여러 개일 때 — Root Cause를 먼저 찾자

에러가 여러 개 보이면 당황하지 말고, **발생 순서**를 파악하자.
대부분 하나의 원인 에러가 나머지를 연쇄적으로 유발한다.

### 2. `@ExceptionHandler`는 일반 컨트롤러와 파라미터 규칙이 다르다

일반 `@GetMapping` 메서드에서 되는 것이 `@ExceptionHandler`에서는 안 될 수 있다.
커스텀 도메인 객체(Account 등)는 `@ExceptionHandler` 파라미터로 주입할 수 없다.

### 3. 예외 처리 코드에도 버그가 숨어있을 수 있다

`@ExceptionHandler`는 예외가 발생해야만 실행되므로,
정상 동작 중에는 버그를 발견할 수 없다.
예외 처리 코드도 테스트 대상이다!

### 4. null 방어는 습관이다

Repository에서 반환된 값을 바로 사용하지 말고,
null 체크를 먼저 하는 습관을 들이자.
특히 `model.addAttribute(Object)`처럼 null을 허용하지 않는 API를 사용할 때는 필수이다.