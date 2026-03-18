# 🔧 트러블슈팅: account-service 이메일 템플릿 누락으로 인한 회원가입 500 오류

## 📌 상황 요약

CORS 문제를 해결한 직후(TroubleShooting_006 참고), 회원가입을 다시 시도했다.
이번엔 화면에 다른 오류 메시지가 표시되었다.

```
서버 내부 오류가 발생했습니다.
```

account-service 로그를 확인하니 Hibernate SQL이 찍혀 있었고,
`TemplateInputException`이 발생하며 서비스가 500을 반환하고 있었다.

---

## 📋 에러 로그 핵심 부분

전체 로그는 매우 길지만, 분석에 필요한 핵심 부분만 추려보면 다음과 같다.

```
Hibernate: select a1_0.id from account a1_0 where a1_0.email=?
Hibernate: select a1_0.id from account a1_0 where a1_0.nickname=?
Hibernate: select nextval('account_seq')

ERROR org.thymeleaf.TemplateEngine:
  Exception processing template "mail/simple-link":
  Error resolving template [mail/simple-link],
  template might not exist or might not be accessible
  by any of the configured Template Resolvers

ERROR GlobalExceptionHandler: Unhandled exception
  org.thymeleaf.exceptions.TemplateInputException:
    Error resolving template [mail/simple-link]
      at SignUpService.sendSignUpConfirmEmail(SignUpService.java:64)
      at SignUpService.processNewAccount(SignUpService.java:42)
      ...
```

---

## 🔍 원인 분석

### 1단계: 로그를 아래에서 위로 읽는다 (Caused by 추적)

로그에서 오류를 분석할 때 가장 중요한 규칙이 있다.

> **로그는 아래에서 위로 읽는다.**

맨 위에 찍히는 오류는 최종적으로 전파된 결과이고,
`Caused by:` 태그를 따라 맨 아래까지 내려가면 **진짜 원인(근본 원인)** 이 나온다.

```
[이번 로그의 오류 전파 구조]

BeanCreationException                         ← 최종 결과 (맨 위)
  └── Caused by: ServiceException             ← 중간 전파
        └── Caused by: TemplateInputException ← 진짜 원인 (맨 아래)
              "Error resolving template [mail/simple-link]"
```

따라서 분석의 시작점은 맨 아래 `Caused by:`다.

---

### 2단계: 오류 메시지 해석

```
Error resolving template [mail/simple-link],
template might not exist or might not be accessible
by any of the configured Template Resolvers
```

직역하면: **"mail/simple-link 템플릿을 찾을 수 없음. 파일이 없거나 접근 불가."**

Thymeleaf는 기본적으로 아래 경로에서 `.html` 파일을 탐색한다.

```
src/main/resources/templates/{템플릿명}.html
```

즉 이 오류는 아래 파일이 없다는 뜻이다.

```
src/main/resources/templates/mail/simple-link.html
```

실제로 account-service의 `resources` 디렉토리를 확인했더니 `templates` 폴더 자체가 없었다.

---

### 3단계: 스택 트레이스에서 내 코드 위치를 찾는다

스택 트레이스는 오류가 전파된 경로를 보여준다.
수십 줄의 라이브러리 코드 속에서 **내가 작성한 코드**가 등장하는 줄을 찾으면
정확히 어디서 오류가 발생했는지 알 수 있다.

```
at SignUpService.sendSignUpConfirmEmail(SignUpService.java:64)  ← 오류 발생 지점
at SignUpService.processNewAccount(SignUpService.java:42)       ← 호출한 곳
at AuthController.signUp(AuthController.java:31)               ← 그것을 호출한 컨트롤러
```

`SignUpService.java:64`가 오류가 발생한 줄이다.
해당 코드를 열어보면 `templateEngine.process("mail/simple-link", context)` 호출부다.

---

### 4단계: Hibernate 로그로 "어느 단계까지 성공했는가" 파악

Hibernate SQL 로그는 DB 작업이 실행됐다는 증거다.
로그 순서를 보면 실패 지점을 특정할 수 있다.

```
Hibernate: select ... where a1_0.email=?      ← 이메일 중복 체크 성공
Hibernate: select ... where a1_0.nickname=?   ← 닉네임 중복 체크 성공
Hibernate: select nextval('account_seq')       ← 회원 저장 직전 시퀀스 채번 성공
                                               ← 여기서 INSERT가 실행되고
TemplateInputException ←←←←←←←←←←←←←←←←←←  이메일 발송 단계에서 실패
```

회원 저장은 성공했으나, 그 다음 단계인 이메일 발송에서 오류가 발생했음을 알 수 있다.

> **이 패턴이 중요한 이유:**
> 만약 로그에 Hibernate SQL이 하나도 없었다면 DB 이전 단계(요청 파싱, 인증, 라우팅 등)에서 실패한 것이다.
> Hibernate SQL이 있다면 최소한 서비스 레이어까지는 도달했다는 뜻이다.

---

### 5단계: 왜 템플릿이 없었는가?

`SignUpService.java`의 `sendSignUpConfirmEmail` 메서드는 Thymeleaf `TemplateEngine`을 사용해
HTML 이메일 본문을 만든다.

```java
// SignUpService.java:64
String message = templateEngine.process("mail/simple-link", context);
```

모노리틱 프로젝트에서는 이미 `templates/mail/simple-link.html`이 존재했다.
MSA로 전환하면서 account-service를 새로 만들 때 이 파일을 누락한 것이 원인이다.

account-service의 디렉토리 구조:

```
account-service/
└── src/
    └── main/
        └── resources/
            └── application.yml   ← 이것만 존재
            (templates/ 폴더 자체가 없음)  ← 문제
```

---

## ✅ 해결 방법

account-service에 Thymeleaf 템플릿 디렉토리와 파일을 생성한다.

**생성 경로:**
```
account-service/src/main/resources/templates/mail/simple-link.html
```

**파일 내용:**

`SignUpService`가 `context`에 담아 넘기는 변수 목록은 다음과 같다.

| 변수명 | 값 예시 | 용도 |
|--------|---------|------|
| `link` | `/api/auth/check-email-token?token=abc&email=test@test.com` | 인증 URL 경로 |
| `nickname` | `양균` | 수신자 닉네임 |
| `linkName` | `이메일 인증하기` | 버튼/링크 텍스트 |
| `message` | `스터디올래 서비스를 이용하려면 링크를 클릭하세요.` | 안내 문구 |
| `host` | `http://localhost:8090` | 도메인 앞부분 (`app.host` 설정값) |

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"></head>
<body>
<div>
    <p>안녕하세요, <span th:text="${nickname}"></span>님.</p>
    <p th:text="${message}"></p>
    <div>
        <a th:href="${host + link}" th:text="${linkName}">링크</a>
    </div>
</div>
</body>
</html>
```

`th:href="${host + link}"`는 `http://localhost:8090/api/auth/check-email-token?token=...` 형태의
완전한 인증 URL을 생성한다.

---

## 🔎 이런 오류를 빠르게 파악하는 법 요약

```
[로그 분석 체크리스트]

Step 1. 서버 로그에 뭔가 찍혀 있는가?
         없다 → 요청이 서버에 도달하지 않음 → CORS/네트워크 문제 의심
         있다 → Step 2로

Step 2. Hibernate SQL 로그가 있는가?
         없다 → DB 이전 단계 실패 (인증, 파싱, 라우팅 등)
         있다 → 서비스 레이어까지 도달 → Step 3으로

Step 3. Caused by: 를 맨 아래까지 따라간다
         → 진짜 원인 오류 클래스와 메시지를 확인

Step 4. 스택 트레이스에서 내 코드(패키지명)가 등장하는 줄을 찾는다
         → 오류 발생 파일명과 줄 번호 확인
         → 해당 코드를 열어서 무엇을 하는 코드인지 확인

Step 5. 오류 메시지 + 발생 위치 조합으로 원인 특정
```

---

## 📎 관련 파일

- `account-service/src/main/resources/templates/mail/simple-link.html` (신규 생성)
- `account-service/src/main/java/com/studyolle/account/service/SignUpService.java` (64번 줄)

---

## 📅 발생일

2026-03-18
