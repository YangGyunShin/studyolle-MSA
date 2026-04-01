# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.

---

## 현재 진행 상황 (2026-04-01 기준)

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 (Eureka, Config, API Gateway) | ✅ 완료 |
| Phase 2 | account-service | ✅ 완료 |
| Phase 3 | frontend-service (인증 페이지 + study 페이지 전체) | ✅ 완료 |
| Phase 4 | study-service 소스코드 + InternalStudyController | ✅ 완료 |
| Phase 4 | 에러 페이지 (404.html, error.html) | ✅ 완료 |
| Phase 4 | 계정 설정 페이지 5개 + account-service 태그/지역 API | ✅ 완료 |
| Phase 4 | event-service 백엔드 + frontend 연동 전체 | ✅ 완료 |
| Phase 4 | 프로필 페이지 (account/profile.html) | ✅ 완료 |
| Phase 4 | 대시보드 모임 표시 (EventFeignClient 연동) | ✅ 완료 |
| Phase 4 | account-service 닉네임 조회 API | ✅ 완료 |
| Phase 5 | notification-service | 🔲 예정 |
| Phase 6 | admin-service | 🔲 예정 |

**다음 즉시 할 일:**
- Phase 5 notification-service 개발 시작

자세한 TODO는 `MSA_TODO.txt` 참고.

---

## 아키텍처 요약

```
[브라우저]
    │
    ▼
[api-gateway :8080]   JWT 검증(쿠키/헤더), 라우팅, X-Account-Id 헤더 추가
    │
    ├── [account-service  :8081]   회원가입/로그인/JWT 발급/계정 설정/프로필
    ├── [study-service    :8083]   스터디 CRUD/설정/가입, EventFeignClient(event-service 호출)
    ├── [event-service    :8084]   모임 생성/신청 ✅ 전체 완성
    └── [notification-service :8085] 알림 (예정)

[frontend-service :8090]   Thymeleaf HTML 서빙 (DB 없음)
    │  브라우저 렌더링 전 RestTemplate 으로 내부 API 호출
    ├── AccountInternalClient  lb://ACCOUNT-SERVICE/internal/**
    ├── StudyInternalClient    lb://STUDY-SERVICE/internal/**
    └── EventInternalClient    lb://EVENT-SERVICE/internal/**  ✅ 완성

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
├── EventServiceApplication.java
├── config/
│     SecurityConfig.java
│     WebMvcConfig.java                InternalRequestFilter 등록
├── controller/
│     EventController.java             /api/studies/{path}/events/** (11개 엔드포인트)
│     EventInternalController.java     /internal/events/** (3개 엔드포인트)
│                                      - GET /internal/events/by-study/{path}
│                                      - GET /internal/events/calendar?accountId={id}
│                                      - GET /internal/events/{eventId}
├── dto/
│     request/  CreateEventRequest, UpdateEventRequest
│     response/ EventResponse, EnrollmentResponse, CommonApiResponse<T>
├── entity/
│     Event.java, Enrollment.java, EventType.java
├── exception/  GlobalExceptionHandler.java
├── filter/
│     InternalRequestFilter.java
│     ALLOWED: frontend-service, study-service, admin-service, notification-service
├── repository/
│     EventRepository.java, EnrollmentRepository.java
└── service/
      EventService.java, EnrollmentService.java
```

---

## account-service 주요 파일 목록 (2026-04-01 기준)

```
account-service/src/main/java/com/studyolle/account/
├── controller/
│     AuthController.java
│     AccountController.java
│     AccountInternalController.java
│         GET /internal/accounts/{id}
│         GET /internal/accounts/{id}/full
│         GET /internal/accounts/{id}/tags
│         GET /internal/accounts/{id}/zones
│         GET /internal/accounts/by-nickname/{nickname}   ← 2026-04-01 추가
├── filter/
│     InternalRequestFilter.java  (ALLOWED: frontend-service, study-service, admin-service, event-service)
├── repository/
│     AccountRepository.java  (findByNickname 추가)
└── ...
```

---

## study-service 주요 변경 (2026-04-01 기준)

```
study-service/src/main/java/com/studyolle/study/
├── client/
│     MetadataFeignClient.java
│     EventFeignClient.java          ← 2026-04-01 추가 (event-service 호출)
│         getEventsByStudy(studyPath)  → GET /internal/events/by-study/{path}
│         getCalendarEvents(accountId) → GET /internal/events/calendar?accountId={id}
├── client/dto/
│     EventSummaryDto.java            ← 2026-04-01 추가
└── controller/
      StudyInternalController.java
          getDashboard() 수정: studyEventsMap 실제 데이터 채움 (기존: 빈 Map)
```

---

## frontend-service 주요 파일 목록 (2026-04-01 기준)

```
frontend-service/src/main/
├── java/com/studyolle/frontend/
│     ├── FrontendServiceApplication.java
│     ├── HomeController.java              GET /, GET /logout
│     ├── config/RestTemplateConfig.java
│     ├── common/InternalHeaderHelper.java
│     ├── account/
│     │     controller/
│     │         AuthPageController.java
│     │         AccountPageController.java  /settings/**
│     │         ProfilePageController.java  /profile/{nickname}  ← 2026-04-01 추가
│     │     client/AccountInternalClient.java
│     │         getAccountSummary(id)
│     │         getAccountSettings(id)
│     │         getAccountTags(id)
│     │         getAccountZones(id)
│     │         getAccountByNickname(nickname)  ← 2026-04-01 추가
│     │     dto/AccountSummaryDto.java (@Data), AccountSettingsDto.java (@Data)
│     ├── study/
│     │     controller/StudyPageController.java
│     │     client/StudyInternalClient.java
│     │     dto/ StudyPageDataDto(@Data, useBanner 포함), MemberDto(@Data),
│     │          JoinRequestDto(@Data), DashboardDto(@Data), StudySummaryDto(@Data)
│     └── event/
│           controller/EventPageController.java
│               GET /study/{path}/events/new
│               GET /study/{path}/events/{eventId}/edit
│               GET /study/{path}/events/{eventId}
│           client/EventInternalClient.java
│               getEventsByStudy(studyPath)
│               getEventById(eventId)
│           dto/EventSummaryDto.java (@Data), EnrollmentDto.java (@Data)
│
└── resources/
      templates/
          fragments.html       (study-list fragment 중복 제거 완료)
          index.html           (추천 스터디 컴팩트 리스트 레이아웃)
          account/
              profile.html     ← 2026-04-01 추가 (프로필 페이지)
              sign-up, check-email, check-email-token, email-login
          study/   (form, view, members, settings/*)
              settings/study.html  (fetch URL 버그 수정: /settings/study/* → /settings/*)
          settings/ (profile, password, notifications, tags, zones)
          error/   (404.html, error.html)
          event/   (form.html, view.html)
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
      - Path=/api/studies/*/events/**
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

## Docker 기동 명령어

```bash
# PostgreSQL
docker start account-db
docker start study-db
docker start event-db

# 최초 생성
docker run -d --name account-db -e POSTGRES_DB=account_db -e POSTGRES_USER=studyolle -e POSTGRES_PASSWORD=studyolle -p 5433:5432 postgres:15
docker run -d --name study-db   -e POSTGRES_DB=study_db   -e POSTGRES_USER=studyolle -e POSTGRES_PASSWORD=studyolle -p 5434:5432 postgres:15
docker run -d --name event-db   -e POSTGRES_DB=event_db   -e POSTGRES_USER=studyolle -e POSTGRES_PASSWORD=studyolle -p 5435:5432 postgres:15

# Phase 5 (notification-service)
docker run -d --name zookeeper \
  -e ZOOKEEPER_CLIENT_PORT=2181 -p 2181:2181 \
  confluentinc/cp-zookeeper:7.5.0
docker run -d --name kafka \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=host.docker.internal:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -p 9092:9092 confluentinc/cp-kafka:7.5.0
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 rabbitmq:3-management
docker run -d --name notification-db \
  -p 27017:27017 mongo:7
```

---

## 주요 트러블슈팅 이력

| 번호 | 문제 | 원인 |
|------|------|------|
| 015 | 로그아웃 불가 + 로그인 유지 안됨 | localhost:8090 직접 접속 / const API_BASE 중복 선언 / InternalRequestFilter 누락 |
| 016 | study/view.html Thymeleaf 파싱 에러 | StudyPageDataDto에 useBanner 필드 누락 |
| 017 | 대시보드 예정된 모임 항상 없음 | event-service InternalRequestFilter ALLOWED에 study-service 미포함 → 403 → catch에서 무시 |
| 018 | 스터디 설정 공개/경로/이름 수정 404 | study.html fetch URL에 /settings/study/ 불필요하게 포함 |
| 019 | 추천 스터디 카드 세로 나열 | col-lg-4 영역에서 col-md-6 col-lg-4 fragment 사용 → 컴팩트 리스트로 교체 |
| 020 | 스터디 카드 중복 표시 | fragments.html에 study-list fragment 두 번 정의 |

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 이어서 진행하세요.*
