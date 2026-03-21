# 🔧 트러블슈팅: @Profile("local") 미적용으로 EmailService 빈을 찾지 못하는 오류

## 📌 상황 요약

PostgreSQL Docker 문제를 해결하고 account-service를 기동했더니,
DB 연결과 테이블 생성은 성공했지만 곧바로 다시 실패했다.

---

## 📋 에러 로그 핵심 부분

```
APPLICATION FAILED TO START

Description:
Parameter 1 of constructor in com.studyolle.account.service.SignUpService
required a bean of type 'com.studyolle.account.infra.mail.EmailService'
that could not be found.

Action:
Consider defining a bean of type 'com.studyolle.account.infra.mail.EmailService'
in your configuration.
```

---

## 🔍 원인 분석

### "빈(Bean)을 찾을 수 없다"는 오류가 무슨 뜻인가?

Spring은 애플리케이션이 시작될 때 `@Service`, `@Component` 등이 붙은 클래스를 스캔해서
"빈(Bean)"이라는 관리 객체를 만들고 등록한다.

`SignUpService`는 생성자에서 `EmailService` 타입의 객체를 주입받아야 하는데,
Spring이 관리하는 빈 목록에 `EmailService` 구현체가 없어서 오류가 난 것이다.

---

### @Profile 이란?

`ConsoleEmailService.java`를 열어보면 이런 코드가 있다.

```java
@Profile("local")        ← 이 줄이 핵심
@Component
public class ConsoleEmailService implements EmailService {
    ...
}
```

`@Profile("local")`이 붙어 있으면 이 클래스는 **"local" 프로파일이 활성화된 상태에서만** 빈으로 등록된다.

프로파일이 활성화되지 않으면 Spring은 이 클래스를 완전히 무시한다.
아예 없는 것처럼 취급하기 때문에 `EmailService` 빈이 존재하지 않게 되고,
`SignUpService`가 주입받을 객체를 찾지 못해서 기동이 실패하는 것이다.

---

### @Profile이 왜 이렇게 설계되어 있는가?

account-service에는 이메일 서비스 구현체가 두 가지 있다.

| 파일 | 프로파일 | 동작 |
|------|----------|------|
| `ConsoleEmailService.java` | `local` | 실제 이메일 발송 안 함. 콘솔 로그에 내용 출력 |
| `HtmlEmailService.java` | `dev`, `prod` 등 | 실제 SMTP 서버를 통해 이메일 발송 |

개발 중에는 실제 이메일을 보내면 불편하므로 `local` 프로파일에서는 콘솔 출력으로 대체한다.
그런데 어떤 프로파일도 지정하지 않고 기동하면 두 구현체 모두 등록되지 않아 빈이 없게 된다.

---

## ✅ 해결 방법

IntelliJ의 Run Configuration에서 `local` 프로파일을 활성화한다.

### 설정 방법

1. IntelliJ 상단 실행 버튼 왼쪽 드롭다운 클릭
2. **Edit Configurations...** 선택
3. 왼쪽 목록에서 `AccountServiceApplication` 선택
4. **Active profiles** 입력란에 `local` 입력
   - 입력란이 없으면 **Modify options → Add VM options** 클릭 후
     VM options 란에 `-Dspring.profiles.active=local` 입력
5. **Apply → OK** 후 재기동

### 적용 확인

기동 로그 초반에 아래 메시지가 보이면 정상 적용된 것이다.

```
The following 1 profile is active: "local"
```

---

## 🔎 이 오류를 만났을 때 확인할 것

`No qualifying bean of type '...' available` 오류가 나왔을 때:

```
1. 해당 인터페이스의 구현체가 있는가? → 있다
2. 구현체에 @Component/@Service 가 붙어 있는가? → 있다
3. @Profile 이 붙어 있는가? → 있다면 해당 프로파일이 활성화되어 있는가?
```

@Profile이 붙어 있는 빈은 반드시 해당 프로파일을 활성화해야 등록된다.

---

## 📎 관련 파일

- `account-service/src/main/java/com/studyolle/account/infra/mail/ConsoleEmailService.java`

---

## 📅 발생일

2026-03-21
