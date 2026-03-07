# Spring Security 인증 & 세션 캐싱 흐름

> StudyOlle 프로젝트 기준으로 작성된 문서입니다.
> 관련 파일: `UserAccount.java`, `Account.java`, `SecurityConfig.java`, `DevDataInitializer.java`

---

## 목차

1. [전체 흐름 개요](#1-전체-흐름-개요)
2. [로그인 시점 — 세션 생성](#2-로그인-시점--세션-생성)
3. [이후 요청 — 세션에서 권한 조회](#3-이후-요청--세션에서-권한-조회)
4. [왜 세션에 캐싱하는가 (성능)](#4-왜-세션에-캐싱하는가-성능)
5. [트레이드오프 — 권한 변경이 즉시 반영되지 않는 이유](#5-트레이드오프--권한-변경이-즉시-반영되지-않는-이유)
6. [보완 방법 3가지](#6-보완-방법-3가지)
7. [StudyOlle의 선택과 이유](#7-studyolle의-선택과-이유)
8. [관련 클래스 구조 요약](#8-관련-클래스-구조-요약)

---

## 1. 전체 흐름 개요

```
[사용자 로그인]
      |
      v
loadUserByUsername()          -- DB에서 Account 조회 (딱 1번)
      |
      v
new UserAccount(account)      -- Account -> UserDetails 변환
      |                          이 시점에 account.getRole() 읽어서 권한 결정
      v
SecurityContext 저장
      |
      v
HttpSession 직렬화 (캐싱)     -- 이후 요청은 여기서 꺼내 씀 (DB 재조회 없음)


[이후 모든 요청]
      |
      v
HttpSession에서 UserAccount 역직렬화
      |
      v
SecurityContext에 복원
      |
      v
권한 확인 (@RequestMatchers, hasRole 등)
      |
      v
허용 or 거부
```

---

## 2. 로그인 시점 — 세션 생성

### 2-1. loadUserByUsername() 호출

사용자가 로그인 폼을 제출하면 Spring Security가 자동으로 `loadUserByUsername()`을 호출한다.
이 메서드는 DB에서 Account를 조회하는 **유일한 시점**이다.

```java
// AccountAuthService.java (UserDetailsService 구현체)
@Override
public UserDetails loadUserByUsername(String emailOrNickname) throws UsernameNotFoundException {
    Account account = accountRepository.findByEmail(emailOrNickname);
    if (account == null) {
        account = accountRepository.findByNickname(emailOrNickname);
    }
    if (account == null) {
        throw new UsernameNotFoundException(emailOrNickname);
    }
    return new UserAccount(account);  // <-- 여기서 Account 감싸서 반환
}
```

### 2-2. UserAccount 생성 — 권한이 결정되는 순간

`new UserAccount(account)` 가 호출될 때 `account.getRole()` 을 읽어서 권한 목록을 확정한다.
**이 순간 이후로는 DB를 다시 보지 않는다.**

```java
// UserAccount.java
public UserAccount(Account account) {
    super(
        account.getNickname(),
        account.getPassword(),
        true, true, true, true,
        List.of(new SimpleGrantedAuthority(account.getRole()))  // <-- 권한 결정
    );
    this.account = account;
}
```

### 2-3. 세션에 직렬화 (캐싱)

생성된 `UserAccount` 객체는 `SecurityContext` 에 저장되고,
`HttpSession` 을 통해 서버 메모리(또는 Redis 등)에 직렬화된다.

```
세션 저장소:
{
  SPRING_SECURITY_CONTEXT: {
    authentication: {
      principal: UserAccount {
        nickname: "admin",
        role: "ROLE_ADMIN",   <-- 로그인 시점의 스냅샷
        account: Account { ... }
      }
    }
  }
}
```

---

## 3. 이후 요청 — 세션에서 권한 조회

로그인 이후 모든 요청은 아래 순서로 처리된다.

```
요청 수신
  |
  v
SecurityContextPersistenceFilter (또는 SecurityContextHolderFilter)
  |  HttpSession에서 SecurityContext 꺼냄 (DB 조회 없음)
  v
FilterSecurityInterceptor
  |  SecurityContext 안의 authorities 확인
  v
hasRole("ADMIN") 검사 -> UserAccount.getAuthorities() 반환값 사용
  |
  v
허용 or 거부
```

DB는 전혀 관여하지 않는다.
세션이 살아있는 한 로그인 시점의 권한 스냅샷이 계속 사용된다.

---

## 4. 왜 세션에 캐싱하는가 (성능)

매 요청마다 DB에서 권한을 조회한다고 가정해보자.

```
사용자 100명, 각자 초당 5회 요청
  -> 초당 500회 권한 조회 쿼리 발생

사용자 1,000명
  -> 초당 5,000회 권한 조회 쿼리 발생
```

권한이 바뀌는 일은 매우 드물다.
매 요청마다 DB를 조회하는 것은 명백한 낭비이므로,
**"로그인 시 1회 조회 후 세션에 캐싱"** 전략을 택한다.

트레이드오프 비교:

| 항목                | 세션 캐싱 방식 (현재)     | 매 요청마다 DB 조회    |
|---------------------|---------------------------|------------------------|
| DB 조회 횟수        | 로그인 시 1회             | 요청마다 매번          |
| 성능                | 좋음                      | 나쁨                   |
| 권한 변경 즉시 반영 | 재로그인 필요             | 즉시 반영              |
| 적합한 경우         | 권한 변경이 드문 경우     | 권한이 자주 바뀌는 경우|

---

## 5. 트레이드오프 — 권한 변경이 즉시 반영되지 않는 이유

DB에서 role을 변경해도 세션이 살아있는 동안에는 이전 권한이 유지된다.

```
[예시 시나리오]

1. 홍길동이 ROLE_USER로 로그인
   세션: { role: "ROLE_USER" }

2. 관리자가 DB에서 홍길동의 role을 ROLE_ADMIN으로 변경
   DB: role = "ROLE_ADMIN"
   세션: { role: "ROLE_USER" }  <-- 아직 그대로

3. 홍길동이 /admin 접근 시도
   Spring Security: 세션에서 ROLE_USER 확인 -> 거부

4. 홍길동이 재로그인
   loadUserByUsername() -> DB에서 ROLE_ADMIN 읽음
   세션: { role: "ROLE_ADMIN" }  <-- 이제 반영됨

5. 홍길동이 /admin 접근 시도
   Spring Security: 세션에서 ROLE_ADMIN 확인 -> 허용
```

---

## 6. 보완 방법 3가지

### 방법 1. 세션 강제 만료 (가장 현실적)

권한을 변경하는 시점에 해당 사용자의 세션을 서버에서 강제로 무효화한다.
사용자가 다음 요청을 보내면 세션이 없으므로 자동으로 재로그인 화면으로 이동하게 되고,
재로그인 시 새 권한이 반영된다.

```java
// AdminService.java - changeRole() 메서드에 세션 만료 로직 추가
@Autowired
private SessionRegistry sessionRegistry;

public void changeRole(Long id, String role) {
    Account account = accountRepository.findById(id).orElseThrow(...);
    account.setRole(role);

    // 해당 사용자의 모든 세션을 즉시 만료
    sessionRegistry.getAllPrincipals().stream()
        .filter(p -> p instanceof UserAccount ua && ua.getAccount().getId().equals(id))
        .flatMap(p -> sessionRegistry.getAllSessions(p, false).stream())
        .forEach(SessionInformation::expireNow);
}
```

SecurityConfig에 SessionRegistry Bean 등록도 필요하다:

```java
@Bean
public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
}

// securityFilterChain 내부에 추가
http.sessionManagement(session -> session
    .maximumSessions(-1)
    .sessionRegistry(sessionRegistry())
);
```

---

### 방법 2. 주기적 재검증

매 요청마다 DB를 조회하는 대신, **일정 시간(예: 5분)마다 한 번씩만** DB에서 권한을 다시 읽는 방식이다.
성능과 실시간성을 절충한 방법이다.

`SecurityContextRepository` 를 커스터마이징하거나,
Filter를 추가해서 마지막 권한 갱신 시각을 세션에 저장하고 비교하는 방식으로 구현한다.

```
실시간성: 최대 5분 지연
성능:     DB 조회가 5분에 1회로 제한됨
구현 복잡도: 중간
```

---

### 방법 3. JWT + 짧은 만료 시간

세션 방식 대신 JWT(JSON Web Token)를 사용하는 구조로 전환한다.
액세스 토큰의 만료 시간을 짧게(예: 15분) 설정하면,
만료 후 토큰 갱신 시 새 권한이 자동으로 반영된다.

```
실시간성: 최대 15분(토큰 만료 시간) 지연
성능:     DB 조회 없음 (토큰 자체에 권한 포함)
구현 복잡도: 높음 (아키텍처 전체 변경 수반)
           Stateless 서버로 전환, Refresh Token 관리 필요
```

---

## 7. StudyOlle의 선택과 이유

StudyOlle은 현재 **세션 캐싱 방식 + 재로그인** 을 사용한다.

이유:
- 관리자 권한 변경은 매우 드문 이벤트다.
- 재로그인 한 번의 불편함이 실질적으로 크지 않다.
- 방법 1(세션 강제 만료)은 필요 시 비교적 적은 코드로 추가할 수 있다.

향후 사용자 수가 늘거나, 권한 변경이 잦아지면 방법 1을 먼저 도입하는 것을 권장한다.

---

## 8. 관련 클래스 구조 요약

```
Account.java
  - role 필드 (String, 기본값: "ROLE_USER")
  - DB에 저장되는 실제 권한 값
  - 변경 시 재로그인 전까지 세션에 반영되지 않음

UserAccount.java (extends User, implements UserDetails)
  - Account를 감싸는 Spring Security 어댑터
  - 생성자에서 account.getRole()을 읽어 SimpleGrantedAuthority 생성
  - 로그인 시점의 스냅샷이 세션에 저장됨

SecurityConfig.java
  - /admin/** -> hasRole("ADMIN") 접근 제어
  - hasRole("ADMIN") 내부적으로 "ROLE_ADMIN" 체크
    (Spring Security가 "ROLE_" 접두사를 자동으로 붙임)

AccountAuthService.java (implements UserDetailsService)
  - loadUserByUsername(): 로그인 시 딱 1번 DB 조회
  - 반환값이 세션에 캐싱됨

DevDataInitializer.java
  - @Profile("local"): 개발 환경에서만 실행
  - 서버 시작 시 관리자 계정 자동 생성 (멱등성 보장)
  - admin@studyolle.com / admin1234! / ROLE_ADMIN
```

---

> 최종 갱신: 2026-03-05
