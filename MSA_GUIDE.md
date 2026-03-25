# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.

---

## 현재 진행 상황 (2026-03-23 기준)

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 (Eureka, Config, API Gateway) | ✅ 완료 |
| Phase 2 | account-service | ✅ 완료 (통합 테스트 완료) |
| Phase 3 | frontend-service (인증 페이지 + study 페이지 전체) | ✅ 완료 |
| Phase 4 | study-service 소스코드 + InternalStudyController 8개 엔드포인트 | ✅ 완료 |
| Phase 4 | 에러 페이지 (404.html, error.html) | ✅ 완료 |
| Phase 4 | 계정 설정 페이지 5개 + account-service 태그/지역 API | ✅ 완료 |
| Phase 4 | event-service | 🔲 다음 작업 |
| Phase 5 | notification-service | 🔲 예정 |
| Phase 6 | admin-service | 🔲 예정 |

**다음 즉시 할 일:**
1. 전체 서비스 재기동 후 스터디 페이지 + 계정 설정 페이지 통합 테스트
2. event-service 개발 시작 (Phase 4 마지막 단계)

자세한 TODO는 `MSA_TODO.txt` 참고.

---

## 아키텍처 요약

```
[브라우저]
    │
    ▼
[api-gateway :8080]   JWT 검증(쿠키/헤더), 라우팅, X-Account-Id 헤더 추가
    │
    ├── [account-service  :8081]   회원가입/로그인/JWT 발급/계정 설정
    ├── [study-service    :8083]   스터디 CRUD/설정/가입
    ├── [event-service    :8084]   모임 생성/신청 (다음 작업)
    └── [notification-service :8085] 알림 (예정)

[frontend-service :8090]   Thymeleaf HTML 서빙 (DB 없음)
    │  브라우저 렌더링 전 RestTemplate 으로 내부 API 호출
    ├── AccountInternalClient  lb://ACCOUNT-SERVICE/internal/**
    ├── StudyInternalClient    lb://STUDY-SERVICE/internal/**
    └── (EventInternalClient  — event-service 완성 후 추가)

[eureka-server :8761]   서비스 디스커버리
[config-server :8888]   중앙 설정 관리
```

**핵심 원칙:**
- 브라우저 → api-gateway → 각 서비스 (외부 요청 흐름)
- frontend-service → 내부 서비스 직접 (lb://, api-gateway 우회)
- JWT 검증은 api-gateway 전담, 각 서비스는 X-Account-Id 헤더만 읽음
- 서비스 간 내부 통신은 /internal/** 경로 + X-Internal-Service 헤더
- 로그인 상태는 쿠키(accessToken)로 유지, api-gateway OptionalJwtFilter가 읽음

---

## api-gateway 필터 구조 (현재)

```
[브라우저 요청]
      │
      ▼
  라우트 매칭
      │
      ├── /api/auth/**      → ACCOUNT-SERVICE  (필터 없음, 공개)
      │
      ├── /api/accounts/**  → ACCOUNT-SERVICE
      │     └── JwtAuthenticationFilter
      │           Authorization 헤더 또는 쿠키(accessToken)에서 JWT 읽기
      │           검증 실패 → 401
      │           검증 성공 → X-Account-Id, X-Account-Nickname 헤더 추가
      │
      └── /**               → FRONTEND-SERVICE
            └── OptionalJwtFilter
                  토큰 없으면 → 그냥 통과 (비로그인 상태)
                  토큰 있으면 → X-Account-Id 헤더 추가 (로그인 상태)
```

---

## 로그인 상태 유지 흐름 (쿠키 방식)

```
① 로그인 성공 (login.html JS)
   → localStorage.setItem('accessToken', ...)   ← fetch() 호출용
   → document.cookie = 'accessToken=...; path=/; max-age=1800'  ← 페이지 이동용

② 브라우저 GET / (홈 이동)
   → 쿠키 자동 포함: Cookie: accessToken=eyJ...

③ api-gateway OptionalJwtFilter
   → 쿠키에서 accessToken 읽기 → JWT 검증
   → X-Account-Id: 123 헤더 추가

④ frontend-service HomeController / AccountPageController
   → accountId = 123 → AccountInternalClient.getAccountSummary(123)
   → account-service GET /internal/accounts/123 호출
   → 대시보드 또는 설정 페이지 렌더링
```

---

## account-service 주요 파일 목록 (2026-03-23 기준)

```
account-service/src/main/java/com/studyolle/account/
├── AccountServiceApplication.java
├── config/
├── controller/
│     AuthController.java               POST /api/auth/signup, /login, /refresh, /check-email-token
│     AccountController.java            GET/PUT /api/accounts/**
│                                       GET/POST /api/accounts/settings/tags/**
│                                       GET/POST /api/accounts/settings/zones/**
│     AccountInternalController.java    GET /internal/accounts/{id}
│                                       GET /internal/accounts/{id}/full
│                                       GET /internal/accounts/{id}/tags
│                                       GET /internal/accounts/{id}/zones
├── dto/
│     request/  SignUpRequest, LoginRequest, UpdateProfileRequest,
│               UpdatePasswordRequest, UpdateNicknameRequest,
│               UpdateNotificationsRequest, TagRequest, ZoneRequest
│     response/ CommonApiResponse, AccountResponse, AccountSummaryResponse,
│               JwtTokenResponse
├── entity/  Account.java
│             ├── tags  (@ElementCollection → account_tags 테이블)
│             └── zones (@ElementCollection → account_zones 테이블)
├── exception/  GlobalExceptionHandler.java
├── infra/mail/  ConsoleEmailService(@Profile("local")), HtmlEmailService
├── repository/  AccountRepository.java
├── security/
└── service/  SignUpService, AccountSettingsService, AuthService
```

---

## frontend-service 주요 파일 목록 (2026-03-23 기준)

```
frontend-service/src/main/
├── java/com/studyolle/frontend/
│     ├── FrontendServiceApplication.java
│     ├── HomeController.java
│     ├── config/RestTemplateConfig.java
│     ├── common/InternalHeaderHelper.java
│     ├── account/
│     │     controller/
│     │         AuthPageController.java      /login, /sign-up, /check-email 등
│     │         AccountPageController.java   /settings/** (신규 추가)
│     │     client/
│     │         AccountInternalClient.java   getAccountSummary, getAccountSettings,
│     │                                      getAccountTags, getAccountZones
│     │     dto/
│     │         AccountSummaryDto.java
│     │         AccountSettingsDto.java      (신규 추가)
│     └── study/
│           controller/StudyPageController.java
│           client/StudyInternalClient.java
│           dto/ (StudyPageDataDto, MemberDto, JoinRequestDto,
│                 DashboardDto, StudySummaryDto, EventSummaryDto)
│
└── resources/
      templates/
          fragments.html          account-settings-menu 프래그먼트 추가
          index.html, login.html
          account/ (sign-up, check-email, check-email-token, email-login)
          study/   (form, view, members, settings/*)
          settings/               (신규 추가)
              profile.html, password.html, notifications.html
              tags.html, zones.html
          error/                  (신규 추가)
              404.html, error.html
      static/css/ (auth-style.css, main-style.css)
      static/js/  (glass-validation.js)
```

---

## api-gateway application.yml 라우팅 (현재)

```yaml
routes:
  - id: account-service-public
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/auth/**

  - id: account-service-private
    uri: lb://ACCOUNT-SERVICE
    predicates:
      - Path=/api/accounts/**
    filters:
      - JwtAuthenticationFilter

  - id: frontend-service
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/**
    filters:
      - OptionalJwtFilter
```

---

## 전체 서비스 기동 순서

```
1. eureka-server   (:8761)
2. config-server   (:8888)
3. api-gateway     (:8080)
4. account-service (:8081)  — IntelliJ Active profiles: local
5. study-service   (:8083)
6. frontend-service (:8090)

접속: http://localhost:8080
```

---

## Docker PostgreSQL 기동 명령어

```bash
# account-service DB
docker run -d --name account-db \
  -e POSTGRES_DB=account_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5433:5432 postgres:15

# study-service DB
docker run -d --name study-db \
  -e POSTGRES_DB=study_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5434:5432 postgres:15
```

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 이어서 진행하세요.*
