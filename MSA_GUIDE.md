# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.

---

## 현재 진행 상황 (2026-03-29 기준)

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 (Eureka, Config, API Gateway) | ✅ 완료 |
| Phase 2 | account-service | ✅ 완료 (통합 테스트 완료) |
| Phase 3 | frontend-service (인증 페이지 + study 페이지 전체) | ✅ 완료 |
| Phase 4 | study-service 소스코드 + InternalStudyController 8개 엔드포인트 | ✅ 완료 |
| Phase 4 | 에러 페이지 (404.html, error.html) | ✅ 완료 |
| Phase 4 | 계정 설정 페이지 5개 + account-service 태그/지역 API | ✅ 완료 |
| Phase 4 | event-service 백엔드 (Entity/Repository/Service/Controller/Config/Filter) | ✅ 완료 |
| Phase 4 | event-service frontend 템플릿 (form.html, view.html) | ✅ 완료 |
| Phase 5 | notification-service | 🔲 예정 |
| Phase 6 | admin-service | 🔲 예정 |

**다음 즉시 할 일:**
1. frontend-service event 패키지 추가 (EventPageController, EventInternalClient, DTO)
2. templates/event/form.html — 모임 생성/수정 폼
3. templates/event/view.html — 모임 상세 (신청/취소/출석체크)
4. study/view.html 모임 목록 탭 event-service 연동

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
    ├── [event-service    :8084]   모임 생성/신청 ✅ 백엔드 완성
    └── [notification-service :8085] 알림 (예정)

[frontend-service :8090]   Thymeleaf HTML 서빙 (DB 없음)
    │  브라우저 렌더링 전 RestTemplate 으로 내부 API 호출
    ├── AccountInternalClient  lb://ACCOUNT-SERVICE/internal/**
    ├── StudyInternalClient    lb://STUDY-SERVICE/internal/**
    └── EventInternalClient    lb://EVENT-SERVICE/internal/**  ← 추가 예정

[eureka-server :8761]   서비스 디스커버리
[config-server :8888]   중앙 설정 관리
```

**핵심 원칙:**
- 브라우저 → api-gateway → 각 서비스 (외부 요청 흐름)
- frontend-service → 내부 서비스 직접 (lb://, api-gateway 우회)
- JWT 검증은 api-gateway 전담, 각 서비스는 X-Account-Id 헤더만 읽음
- 서비스 간 내부 통신은 /internal/** 경로 + X-Internal-Service 헤더
- 로그인 상태는 쿠키(accessToken)로 유지, api-gateway OptionalJwtFilter가 읽음
- **반드시 localhost:8080으로 접속** (8090 직접 접속 시 OptionalJwtFilter 우회)

---

## api-gateway 필터 구조 (현재)

```
[브라우저 요청]
      │
      ▼
  라우트 매칭
      │
      ├── /api/auth/**            → ACCOUNT-SERVICE  (필터 없음, 공개)
      ├── /api/accounts/**        → ACCOUNT-SERVICE  (JwtAuthenticationFilter)
      ├── /api/studies/*/events/**→ EVENT-SERVICE    (JwtAuthenticationFilter)
      ├── /api/studies/**         → STUDY-SERVICE    (JwtAuthenticationFilter)
      ├── /internal/**            → 403 전면 차단
      │
      └── /**                     → FRONTEND-SERVICE
            └── OptionalJwtFilter
                  토큰 없으면 → 그냥 통과 (비로그인 상태)
                  토큰 있으면 → X-Account-Id 헤더 추가 (로그인 상태)
```

---

## 로그인/로그아웃 흐름

```
[로그인]
① login.html JS → fetch POST /api/auth/login
② 응답에서 accessToken, refreshToken 수신
③ localStorage.setItem('accessToken', ...)        ← fetch() 호출용
④ document.cookie = 'accessToken=...; max-age=1800' ← 페이지 이동용
⑤ window.location.href = '/'

[페이지 이동 시 인증]
① 브라우저 GET / → 쿠키 자동 포함
② api-gateway OptionalJwtFilter → 쿠키에서 accessToken 읽기 → JWT 검증
③ X-Account-Id 헤더 추가 → frontend-service HomeController로 전달
④ account-service /internal/accounts/{id} 호출 → 대시보드 렌더링

[로그아웃]
① handleLogout(e) → localStorage.removeItem(...)
② window.location.href = '/logout'
③ HomeController GET /logout → Set-Cookie: accessToken=; max-age=0 (서버 사이드 쿠키 삭제)
④ redirect:/
```

---

## event-service 구조 (2026-03-29 완성)

```
event-service/src/main/java/com/studyolle/event/
├── EventServiceApplication.java       (@EnableFeignClients)
├── config/
│     SecurityConfig.java              csrf disable, anyRequest permitAll
│     WebMvcConfig.java                InternalRequestFilter 인터셉터 등록
├── controller/
│     EventController.java             /api/studies/{path}/events/** (11개 엔드포인트)
│     EventInternalController.java     /internal/events/** (2개 엔드포인트)
├── dto/
│     request/  CreateEventRequest, UpdateEventRequest
│     response/ EventResponse, EnrollmentResponse, CommonApiResponse<T>
├── entity/
│     Event.java          (@Builder, @Builder.Default enrollments)
│     Enrollment.java     (@Builder)
│     EventType.java      (FCFS, CONFIRMATIVE)
├── exception/
│     GlobalExceptionHandler.java
├── filter/
│     InternalRequestFilter.java       ALLOWED: frontend-service, study-service, admin-service
├── repository/
│     EventRepository.java             (@EntityGraph enrollments)
│     EnrollmentRepository.java
└── service/
      EventService.java                createEvent, getEvents, updateEvent, deleteEvent
      EnrollmentService.java           enroll, cancelEnrollment, acceptEnrollment, rejectEnrollment, checkIn

application.yaml: port 8084, PostgreSQL localhost:5435/event_db
```

**EventController 엔드포인트 목록:**
```
POST   /api/studies/{path}/events                          모임 생성
GET    /api/studies/{path}/events                          모임 목록
GET    /api/studies/{path}/events/{eventId}                모임 상세
PUT    /api/studies/{path}/events/{eventId}                모임 수정
DELETE /api/studies/{path}/events/{eventId}                모임 삭제
POST   /api/studies/{path}/events/{eventId}/enroll         참가 신청
POST   /api/studies/{path}/events/{eventId}/leave          참가 취소
POST   .../enrollments/{enrollmentId}/accept               승인 (운영자)
POST   .../enrollments/{enrollmentId}/reject               거절 (운영자)
POST   .../enrollments/{enrollmentId}/checkin              출석 체크 (운영자)
POST   .../enrollments/{enrollmentId}/cancel-checkin       출석 취소 (운영자)

GET    /internal/events/by-study/{studyPath}               스터디의 모임 목록 (내부)
GET    /internal/events/calendar?accountId={id}            내가 신청한 모임 (내부)
```

---

## account-service 주요 파일 목록 (2026-03-29 기준)

```
account-service/src/main/java/com/studyolle/account/
├── AccountServiceApplication.java
├── config/
│     SecurityConfig.java
│     WebMvcConfig.java                ← 신규 추가 (InternalRequestFilter 등록)
├── controller/
│     AuthController.java              POST /api/auth/signup, /login, /refresh, /check-email-token
│     AccountController.java           GET/PUT /api/accounts/**
│                                      GET/POST /api/accounts/settings/tags/**
│                                      GET/POST /api/accounts/settings/zones/**
│     AccountInternalController.java   GET /internal/accounts/{id}
│                                      GET /internal/accounts/{id}/full
│                                      GET /internal/accounts/{id}/tags
│                                      GET /internal/accounts/{id}/zones
├── filter/
│     InternalRequestFilter.java       ← 신규 추가 (ALLOWED: frontend-service, study-service, admin-service, event-service)
├── dto/ ...
├── entity/  Account.java (@ElementCollection tags, zones)
├── exception/  GlobalExceptionHandler.java
├── infra/mail/  ConsoleEmailService, HtmlEmailService
├── repository/  AccountRepository.java
├── security/    JwtTokenProvider.java
└── service/     AccountAuthService, AccountSettingsService, SignUpService
```

---

## frontend-service 주요 파일 목록 (2026-03-29 기준)

```
frontend-service/src/main/
├── java/com/studyolle/frontend/
│     ├── FrontendServiceApplication.java
│     ├── HomeController.java              GET /, GET /logout (쿠키 삭제)
│     ├── config/RestTemplateConfig.java
│     ├── common/InternalHeaderHelper.java
│     ├── account/
│     │     controller/
│     │         AuthPageController.java
│     │         AccountPageController.java  /settings/**
│     │     client/AccountInternalClient.java
│     │     dto/AccountSummaryDto.java, AccountSettingsDto.java
│     ├── study/
│     │     controller/StudyPageController.java
│     │     client/StudyInternalClient.java
│     │     dto/ StudyPageDataDto, MemberDto, JoinRequestDto,
│     │          DashboardDto, StudySummaryDto, EventSummaryDto
│     └── event/                            ← 다음 작업 추가 예정
│           controller/EventPageController.java
│           client/EventInternalClient.java
│           dto/EventPageDataDto.java 등
│
└── resources/
      templates/
          fragments.html     (var API_BASE, handleLogout → /logout 서버 사이드 삭제)
          index.html         (var API_BASE)
          login.html
          account/ (sign-up, check-email, check-email-token, email-login)
          study/   (form, view, members, settings/*)
          settings/ (profile, password, notifications, tags, zones)
          error/   (404.html, error.html)
          event/   ← 다음 작업 추가 예정 (form.html, view.html)
      static/css/ (auth-style.css, main-style.css)
      static/js/  (glass-validation.js)
```

---

## api-gateway application.yml 라우팅 (현재)

```yaml
routes:
  - id: block-internal
    uri: no://op
    predicates:
      - Path=/internal/**
    filters:
      - SetStatus=403

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

  - id: event-service
    uri: lb://EVENT-SERVICE
    predicates:
      - Path=/api/studies/*/events/**   # study-service보다 먼저 선언 (더 구체적)
    filters:
      - JwtAuthenticationFilter

  - id: study-service
    uri: lb://STUDY-SERVICE
    predicates:
      - Path=/api/studies/**
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
6. event-service   (:8084)  — event-db Docker 컨테이너 필요
7. frontend-service (:8090)

접속: http://localhost:8080  (8090 직접 접속 금지 — OptionalJwtFilter 우회)
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

# event-service DB
docker run -d --name event-db \
  -e POSTGRES_DB=event_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5435:5432 postgres:15
```

---

## 주요 트러블슈팅 이력 (TroubleShooting/ 참고)

| 번호 | 문제 | 원인 | 파일 |
|------|------|------|------|
| 015 | 로그아웃 불가 + 로그인 유지 안됨 | localhost:8090 직접 접속 / const API_BASE 중복 선언 / InternalRequestFilter 누락 | TroubleShooting_015.MD |

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 이어서 진행하세요.*
