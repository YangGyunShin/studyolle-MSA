# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.

---

## 현재 진행 상황 (2026-04-23 기준)

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 (Eureka, Config, API Gateway) | ✅ 완료 |
| Phase 2 | account-service | ✅ 완료 |
| Phase 3 | frontend-service (인증 + study + 계정 설정 + 프로필) | ✅ 완료 |
| Phase 4 | study-service / event-service 백엔드 + frontend 연동 | ✅ 완료 |
| Phase 5 | notification-service 소스코드 18개 파일 | ✅ 완료 |
| Phase 5 | notification-service Kafka 흐름 검증 | ✅ 완료 |
| Phase 5 | frontend-service 알림 페이지 연동 | ✅ 완료 |
| Phase 5 | RabbitMQ 흐름 테스트 (event → notification) | 🔲 대기 중 |
| Phase 6 | admin-service 백엔드 (8082) | ✅ 완료 |
| Phase 6 | api-gateway AdminRoleFilter + JWT role claim 전달 | ✅ 완료 |
| Phase 6 | account-service 관리자 회원 목록 API | ✅ 완료 |
| Phase 6 | frontend-service 임시 관리자 페이지 | ✅ 완료 (Phase 7 에서 제거됨) |
| Phase 7 | admin-gateway 모듈 (9080) 신규 생성 | ✅ 완료 |
| Phase 7 | admin-frontend 모듈 (9000) 신규 생성 | ✅ 완료 |
| Phase 7 | frontend-service 의 admin 패키지/템플릿 삭제 | ✅ 완료 |
| Phase 7 | api-gateway application.yml 관리자 라우트 정리 + block-admin-api 추가 | ✅ 완료 |
| Phase 7 | 통합 테스트 (11개 서비스 기동 + 두 사이트 동시 검증) | ✅ 완료 (2026-04-14) |
| Phase 8 | 회원 권한 변경 (방어 깊이 검증 포함 전체 통과) | ✅ 완료 (2026-04-16) |
| Phase 8 | 스터디 관리 / 강제 비공개 | ✅ 완료 (2026-04-16) |
| Phase 8 | **이메일 인증 기반 페이지 접근 제한 (옵션 B 하이브리드)** | ✅ 완료 (2026-04-23) |

**Phase 8 세 번째 기능 완료 — 통합 테스트 대기 중 + 다음 작업 미정**

Phase 8 의 세 번째 기능 (이메일 인증 기반 페이지 접근 제한) 이 마무리되었다.
프론트 인터셉터(1차 방어선) + 백엔드 EmailVerifiedGuard(2차 방어선) 의 하이브리드 구조로,
JWT claim 에 emailVerified 가 추가되어 게이트웨이가 X-Account-Email-Verified 헤더를 주입한다.

후보 작업: AccountInternalController 조회 API → Service 이관, RabbitMQ 흐름 테스트(Phase 5 잔여),
대시보드 스터디 수 카드 활성화, 한글 닉네임 인코딩 버그 수정, 404 커스텀 에러 페이지, Phase 9 기획 등.

자세한 TODO 와 Phase 별 작업 항목은 `MSA_TODO.txt` 참고.

---

## 아키텍처 요약

```
[일반 사용자 브라우저]                          [관리자 브라우저]
          │                                            │
          ▼                                            ▼
[api-gateway :8080]                             [admin-gateway :9080]
          │                                            │
          ├──────────────┬─────────────────────┴───────────────┐
          ▼                ▼                                    ▼
   [account :8081]    [study :8083]                       [admin-service :8082]
                      [event :8084]                        │
                      [notification :8085]                 └─ Feign → account-service
                               │                              (orchestration — 자체 DB 없음)
                               ▼
                          [frontend-service :8090]  ← api-gateway /** 이 라우팅
                          [admin-frontend   :9000]  ← admin-gateway /** 이 라우팅

[eureka-server :8761]   서비스 디스커버리
[config-server :8888]   중앙 설정 관리
```

**핵심 원칙:**
- 브라우저 → 게이트웨이 → 각 서비스 (외부 요청 흐름)
- frontend-service / admin-frontend → 내부 서비스 직접 (lb://, 게이트웨이 우회)
- JWT 검증은 게이트웨이 전담, 각 서비스는 X-Account-* 헤더만 읽음
- 서비스 간 내부 통신은 /internal/** 경로 + X-Internal-Service 헤더
- 로그인 상태는 쿠키(accessToken)로 유지, 각 게이트웨이 OptionalJwtFilter가 읽음
- 일반 사용자는 **반드시 localhost:8080으로 접속**
- 관리자는 **반드시 localhost:9080으로 접속**
- 두 사이트는 각자 다른 포트/도메인을 쓰지만 account-service 의 같은 /api/auth/login 으로
  로그인하고, 발급된 JWT 의 role claim 을 admin-frontend 로그인 JS 가 확인해 관리자만 통과시킨다

---

## JWT Claim 구조 (Phase 8 세 번째 기능 후 최종)

account-service 의 `JwtTokenProvider.createAccessToken()` 이 발급하는 JWT 에는
다음 4가지 claim 이 담긴다. 게이트웨이가 각각을 헤더로 변환해 하위 서비스에 전달한다.

| JWT Claim | HTTP Header | 의미 | 추가된 시점 |
|-----------|-------------|------|------------|
| `sub` | `X-Account-Id` | 사용자 DB PK | Phase 2 |
| `nickname` | `X-Account-Nickname` | 사용자 닉네임 | Phase 2 |
| `role` | `X-Account-Role` | ROLE_USER / ROLE_ADMIN | Phase 6 |
| `emailVerified` | `X-Account-Email-Verified` | 이메일 인증 완료 여부 | **Phase 8 세 번째 기능** |

**이메일 인증 완료 시 JWT 재발급 흐름**:
인증 완료 시점(`/check-email-token`)에서 새 JWT 를 발급해 응답으로 돌려준다.
프론트 `check-email-token.html` JS 가 받은 새 토큰으로 localStorage + 쿠키를 교체.
다음 요청부터 즉시 쓰기 기능 사용 가능. (재로그인 불필요)

---

## Phase 5 메시지 흐름

```
study-service ──→ Kafka "study-events" ──→ notification-service
  (STUDY_CREATED / STUDY_PUBLISHED /          @KafkaListener
   RECRUITING_STARTED / RECRUITING_STOPPED)

event-service ──→ RabbitMQ "enrollment.queue" ──→ notification-service
  (enrollment.accepted / rejected /               @RabbitListener
   applied / attendance)

notification-service ──→ PostgreSQL (알림 영구 저장)
                    ──→ Redis (읽지 않은 알림 카운터 INCR/DECR)
                              (중복 이벤트 방지 SETNX + TTL 1일)
```

---

## 두 게이트웨이 필터 구조 (Phase 7 완료 후)

```
[일반 사용자 브라우저]                       [관리자 브라우저]
          │                                         │
          ▼                                         ▼
[api-gateway :8080]                          [admin-gateway :9080]
    ├── /internal/**             → 403 차단     ├── /internal/**           → 403 차단
    ├── /api/auth/**             → ACCOUNT      ├── /api/auth/**           → ACCOUNT (관리자 로그인)
    ├── /api/accounts/**         → ACCOUNT      ├── /api/admin/**          → ADMIN-SERVICE
    ├── /api/notifications/**    → NOTIFICATION │        + JwtAuthenticationFilter
    ├── /api/studies/*/events/** → EVENT        │        + AdminRoleFilter
    ├── /api/studies/**          → STUDY        └── /**                    → ADMIN-FRONTEND
    ├── /api/admin/**            → 404 차단              + OptionalJwtFilter
    │   (깊이 있는 방어 — 일반 GW 에는 관리자 API 가
    │    존재하지 않는 것처럼 보이게 함)
    └── /**                      → FRONTEND-SERVICE
          + OptionalJwtFilter
```

**두 게이트웨이의 jwt.secret 은 반드시 동일해야 한다.**
account-service 가 발급한 JWT 를 양쪽에서 모두 검증할 수 있어야 하기 때문이다.
관리자도 일반 사용자와 같은 POST /api/auth/login 을 재사용하므로 별도 발급자는 없다.

**Phase 8 세 번째 기능 적용 후 두 게이트웨이의 모든 JWT 필터는** JWT claim 의 emailVerified 를 추출해
`X-Account-Email-Verified` 헤더로 주입한다. 4개 필터 모두 동일 로직.

---

## 로그인/로그아웃 흐름

```
[로그인]
① login.html JS → fetch POST /api/auth/login
② 응답에서 accessToken, refreshToken 수신
③ localStorage.setItem('accessToken', ...)          ← fetch() 호출용
④ document.cookie = 'accessToken=...; max-age=1800' ← 페이지 이동용
⑤ window.location.href = '/'

[페이지 이동 시 인증]
① 브라우저 GET / → 쿠키 자동 포함
② api-gateway OptionalJwtFilter → 쿠키에서 accessToken 읽기 → JWT 검증
③ X-Account-Id / X-Account-Email-Verified 등 헤더 추가 → frontend-service HomeController로 전달
④ account-service /internal/accounts/{id} 호출 → 대시보드 렌더링

[로그아웃]
① handleLogout(e) → localStorage.removeItem(...)
② window.location.href = '/logout'
③ HomeController GET /logout → Set-Cookie: accessToken=; max-age=0 (서버 사이드 쿠키 삭제)
④ redirect:/

[이메일 인증 완료 — Phase 8 세 번째 기능 신규]
① 인증 메일 클릭 → /check-email-token 페이지 로드
② JS fetch → account-service GET /api/auth/check-email-token
③ account-service 가 emailVerified=true UPDATE + 새 JWT 발급해서 응답
④ JS 가 응답에서 새 JWT 추출 → localStorage + 쿠키 교체
⑤ 1.5초 후 자동으로 / 로 이동 (재로그인 불필요)
```

---

## 이메일 인증 기반 접근 제한 구조 (Phase 8 세 번째 기능)

```
[비로그인 사용자]                  [로그인 + 인증 안 함]                  [로그인 + 인증 완료]
       │                                  │                                       │
       ▼                                  ▼                                       ▼
모든 페이지 통과 (둘러보기 가능)  → /new-study 등 접근                        모든 기능 사용 가능
                                  → 1차: EmailVerifiedInterceptor 가
                                          /check-email-required 로 리다이렉트
                                  → 2차: 만약 fetch 직접 호출로 우회 시
                                          백엔드 EmailVerifiedGuard 가 403 반환
```

**1차 방어선 (UX)**: `frontend-service/EmailVerifiedInterceptor`
- 페이지 레벨 차단 → 친절한 안내 페이지(`/check-email-required`) 로 리다이렉트
- 적용 경로: `/new-study`, `/study/*/settings/**`, `/study/*/new-event`, `/study/*/events/*/edit`,
  `/settings/**`, `/notifications`, `/profile/*`

**2차 방어선 (보안)**: 각 백엔드 서비스의 `EmailVerifiedGuard.require(emailVerified)`
- API 직접 호출 우회를 차단
- 적용 위치: study-service / event-service / account-service 의 모든 쓰기 엔드포인트

**옵션 B 결정 — 로그인 자체는 허용**:
이메일 인증 안 한 사용자도 로그인 가능 (이전에는 로그인 자체가 막혔음).
JWT claim 에 emailVerified=false 가 담겨서 나가고, 쓰기 시점에만 차단.
"인증 안 한 사람도 둘러볼 수는 있고, 쓸 수는 없다" 가 더 유연한 UX.

---

## admin-gateway 구조 (Phase 7 신규)

```
infra/admin-gateway/
├── build.gradle                                 — api-gateway 와 동일한 의존성 (starter-gateway, eureka, jjwt)
└── src/main/
      ├── java/com/studyolle/admingateway/
      │     ├── AdminGatewayApplication.java
      │     └── filter/
      │           ├── JwtAuthenticationFilter.java   api-gateway 와 동일 로직, package 만 다름
      │                                              (Phase 8 세 번째 기능: emailVerified claim 추출)
      │           ├── AdminRoleFilter.java           X-Account-Role == ROLE_ADMIN 검증
      │           └── OptionalJwtFilter.java         admin-frontend 페이지용 (비로그인 통과)
      │                                              (Phase 8 세 번째 기능: emailVerified claim 추출)
      └── resources/
            └── application.yml                        port 9080, jwt.secret 동일
                                                         CORS allowed: 9080, 9000

라우팅:
  /internal/**     → 403 차단
  /api/auth/**     → ACCOUNT-SERVICE (관리자 로그인 재사용)
  /api/admin/**    → ADMIN-SERVICE (JwtAuth + AdminRole)
  /**              → ADMIN-FRONTEND (OptionalJwt)
```

---

## admin-frontend 구조 (Phase 7 + Phase 8 누적)

```
services/admin-frontend/src/main/
├── java/com/studyolle/adminfrontend/
│     ├── AdminFrontendApplication.java
│     ├── HomeController.java                GET /, /dashboard, /logout
│     ├── config/RestTemplateConfig.java     @LoadBalanced RestTemplate Bean
│     ├── member/                            Phase 7 완성
│     │     ├── controller/AdminMemberController.java   GET /members, POST /members/{id}/role
│     │     ├── client/AdminInternalClient.java         lb://ADMIN-SERVICE/api/admin/members 호출
│     │     └── dto/  AdminMemberDto, AdminPageResponse, AdminCommonApiResponse
│     └── study/                             Phase 8 두 번째 기능 신규
│           ├── controller/AdminStudyController.java   GET /studies, POST /studies/{path}/force-close
│           ├── client/AdminStudyClient.java
│           └── dto/AdminStudyDto.java
└── resources/
      ├── application.yml                    port 9000, api-base-url = http://localhost:9080
      ├── static/css/admin-style.css         다크 슬레이트 + 청록색 포인트
      └── templates/
            ├── fragments.html                 head + admin-nav fragment
            ├── login.html                     JWT role 검증 포함 (비관리자는 토큰 저장 안 함)
            ├── dashboard.html                 회원 수 표시 + Quick Actions (스터디 관리 활성)
            ├── members.html                   검색 + 페이지네이션 + 권한 변경
            └── studies.html                   검색 + 페이지네이션 + 강제 비공개

[일반 frontend-service 와의 차이점]
- node-gradle 플러그인 없음 (npm 데피던시 없이 순수 HTML/CSS 만 사용)
- spring-cloud-starter-loadbalancer 명시적 포함 (lb:// 스킴 변환용)
- AdminInternalClient 만 /api/admin/** 정문을 호출, 나머지는 /internal/** 사용 안 함
- 첫 화면은 로그인 페이지 (랜딩 페이지 없음)
```

---

## notification-service 구조 (Phase 5 신규, 2026-04-02)

```
notification-service/src/main/java/com/studyolle/notification/
├── NotificationServiceApplication.java
├── config/
│     RedisConfig.java             RedisTemplate<String, String> Bean 등록
│     RabbitMQConfig.java          Exchange / Queue / Binding 선언 (Consumer 측)
│     SecurityConfig.java          Security 설정 (전체 permitAll)
│     WebMvcConfig.java            InternalRequestFilter 등록
├── filter/
│     InternalRequestFilter.java   /internal/** 접근 제어
│     ALLOWED: frontend-service, study-service, event-service, admin-service
├── entity/
│     NotificationType.java        STUDY / ENROLLMENT enum
│     Notification.java            @Entity — id, accountId, message, link, type, checked, createdAt
├── dto/
│     NotificationResponse.java    Notification → Response 변환 (from() 정적 팩토리)
├── repository/
│     NotificationRepository.java  JpaRepository + @Modifying markAllAsRead()
├── service/
│     NotificationService.java
│         createNotification()     알림 생성 + Redis INCR
│         isFirstProcessing()      중복 방지 (Redis SETNX + TTL 1일)
│         getUnreadCount()         Redis 즉시 반환
│         getUnreadNotifications() PostgreSQL 조회
│         markAllAsRead()          PostgreSQL 벌크 UPDATE + Redis SET 0
│         markAsRead()             PostgreSQL UPDATE + Redis DECR
├── kafka/
│     StudyEventDto.java           study-service 와 동일한 필드 구조
│     StudyEventConsumer.java      @KafkaListener(topics = "study-events")
│                                  중복 방지 후 createNotification() 호출
├── rabbitmq/
│     EnrollmentEventDto.java      event-service 와 동일한 필드 구조
│     EnrollmentEventConsumer.java @RabbitListener(queues = "enrollment.queue")
│                                  중복 방지 후 createNotification() 호출
└── controller/
      NotificationController.java
          GET   /api/notifications        읽지 않은 알림 목록 (PostgreSQL)
          PATCH /api/notifications        전체 읽음 처리
          PATCH /api/notifications/{id}   단건 읽음 처리
      NotificationInternalController.java
          GET /internal/notifications/count/{accountId}  읽지 않은 알림 수 (Redis 즉시)

Redis Key 구조:
  notification:unread:{accountId}      읽지 않은 알림 수 카운터
  notification:dedup:{eventKey}        중복 이벤트 방지 (TTL 1일)
```

---

## event-service 구조 (Phase 4 + Phase 5 + Phase 8 누적)

```
event-service/src/main/java/com/studyolle/event/
├── EventServiceApplication.java
├── common/                              ← Phase 8 세 번째 기능 신규
│     EmailVerifiedGuard.java            EmailVerifiedGuard.require(emailVerified)
├── config/
│     SecurityConfig.java
│     WebMvcConfig.java                  InternalRequestFilter 등록
├── controller/
│     EventController.java               /api/studies/{path}/events/** (11개 엔드포인트)
│                                        쓰기 메서드 9개에 EmailVerifiedGuard 적용
│     EventInternalController.java       /internal/events/** (3개 엔드포인트)
├── dto/
│     request/  CreateEventRequest, UpdateEventRequest
│     response/ EventResponse, EnrollmentResponse, CommonApiResponse<T>
├── entity/
│     Event.java, Enrollment.java, EventType.java
├── exception/  GlobalExceptionHandler.java
├── filter/
│     InternalRequestFilter.java
│     ALLOWED: frontend-service, study-service, admin-service, notification-service
├── rabbitmq/                          ← Phase 5 추가
│     EnrollmentEventDto.java          RabbitMQ 발행 DTO
│     RabbitMQConfig.java              Exchange(event.notification) / Queue(enrollment.queue) / Binding
│     EnrollmentRabbitProducer.java    RabbitTemplate.convertAndSend()
│                                      전송 실패 시 예외 삼킴 (부가 기능)
├── repository/
│     EventRepository.java, EnrollmentRepository.java
└── service/
      EventService.java
      EnrollmentService.java           ← Phase 5 수정
          enroll()        → enrollment.applied  (선착순 즉시 수락 시)
          acceptEnrollment() → enrollment.accepted
          rejectEnrollment() → enrollment.rejected
          checkIn()       → enrollment.attendance

RabbitMQ 설정:
  Exchange : event.notification (Topic Exchange)
  Queue    : enrollment.queue   (durable)
  Routing  : enrollment.#       (enrollment. 으로 시작하는 모든 키)
```

---

## study-service 구조 (Phase 4 + Phase 5 + Phase 8 누적)

```
study-service/src/main/java/com/studyolle/study/
├── client/
│     MetadataFeignClient.java
│     EventFeignClient.java           event-service 호출
├── client/dto/
│     EventSummaryDto.java
├── common/                            ← Phase 8 세 번째 기능 신규
│     EmailVerifiedGuard.java          EmailVerifiedGuard.require(emailVerified)
├── controller/
│     StudyController.java             createStudy / joinStudy 에 EmailVerifiedGuard 적용
│     StudySettingsController.java     모든 엔드포인트(17개) 에 EmailVerifiedGuard 적용
│     StudyInternalController.java     관리자용 force-close 엔드포인트 포함
├── dto/response/
│     StudyAdminResponse.java          ← Phase 8 두 번째 기능 신규 (관리자 전용 DTO)
├── entity/
│     Study.java                       forceClose() 도메인 메서드 ← Phase 8 두 번째 기능 신규
├── kafka/                            ← Phase 5 추가
│     StudyEventDto.java              Kafka 발행 DTO
│     StudyKafkaProducer.java         KafkaTemplate.send(topic, studyPath, event)
│                                     전송 실패 시 예외 삼킴 (부가 기능)
├── repository/
│     StudyRepository.java            findByTitleContainingIgnoreCaseOrPathContainingIgnoreCase 추가
│                                     ← Phase 8 두 번째 기능
└── service/
      StudyService.java               createNewStudy() → STUDY_CREATED 발행
      StudySettingsService.java       publish()        → STUDY_PUBLISHED 발행
                                      startRecruit()   → RECRUITING_STARTED 발행
                                      stopRecruit()    → RECRUITING_STOPPED 발행
      StudyInternalService.java       ← Phase 8 두 번째 기능 신규
                                      forceClose(path, requesterId)

Kafka 설정:
  Topic  : study-events
  Key    : studyPath (같은 스터디 → 같은 Partition → 순서 보장)
  발행 시점: 스터디 생성 / 공개 / 모집 시작 / 모집 종료
```

---

## account-service 구조 (Phase 8 세 번째 기능 후 최신)

```
account-service/src/main/java/com/studyolle/account/
├── common/                              ← Phase 8 세 번째 기능 신규
│     EmailVerifiedGuard.java            EmailVerifiedGuard.require(emailVerified)
├── controller/
│     AuthController.java
│         POST /api/auth/login              login (이메일 인증 미완료자도 허용 ← Phase 8 세 번째 기능)
│         POST /api/auth/signup             회원가입
│         POST /api/auth/refresh            토큰 재발급
│         GET  /api/auth/check-email-token  이메일 토큰 검증 + JWT 재발급 ← Phase 8 세 번째 기능
│         POST /api/auth/resend-confirm-email
│         POST /api/auth/email-login
│         GET  /api/auth/login-by-email
│     AccountController.java
│         설정 변경 메서드 7개에 EmailVerifiedGuard 적용 ← Phase 8 세 번째 기능
│     AccountInternalController.java
│         GET   /internal/accounts/{id}
│         GET   /internal/accounts/{id}/full
│         GET   /internal/accounts/{id}/tags
│         GET   /internal/accounts/{id}/zones
│         GET   /internal/accounts/by-nickname/{nickname}
│         GET   /internal/accounts                          (관리자 회원 목록, 페이지네이션)
│         POST  /internal/accounts/{id}/role               ← Phase 8 첫 번째 기능 (PATCH 에서 POST 로 변경)
├── dto/request/
│     RoleUpdateRequest.java                              ← Phase 8 첫 번째 기능 신규 record
├── entity/
│     Account.java
│         changeRole(String) 도메인 메서드                ← Phase 8 첫 번째 기능 신규
├── filter/
│     InternalRequestFilter.java  (ALLOWED: frontend-service, study-service, admin-service, event-service)
├── repository/
│     AccountRepository.java  (findByNickname, findByEmailContainingOrNicknameContaining 포함)
├── security/
│     JwtTokenProvider.java
│         createAccessToken(id, nickname, role, emailVerified) ← Phase 8 세 번째 기능: 4번째 파라미터 추가
├── service/
│     AccountAuthService.java
│         login() — emailVerified 체크 제거 ← Phase 8 세 번째 기능
│         reissueTokensAfterEmailVerification() ← Phase 8 세 번째 기능 신규
│     AccountSettingsService.java
│     SignUpService.java
│     AccountInternalService.java                         ← Phase 8 첫 번째 기능 신규
│         updateRole(targetId, requesterId, newRole) — 자기 자신 검증 / role 화이트리스트 / DB UPDATE
│         (권고: 현재 AccountInternalController 의 조회 API 들은 추후 모두 이 service 로 이관 예정)
└── ...
```

---

## frontend-service 구조 (Phase 8 세 번째 기능 후 최신)

```
frontend-service/src/main/
├── java/com/studyolle/frontend/
│     ├── FrontendServiceApplication.java
│     ├── HomeController.java              GET /, GET /logout
│     ├── config/
│     │     RestTemplateConfig.java
│     │     WebMvcConfig.java              ← Phase 8 세 번째 기능 신규
│     │                                    EmailVerifiedInterceptor 등록 + 적용 경로 지정
│     ├── common/
│     │     InternalHeaderHelper.java
│     │     EmailVerifiedInterceptor.java  ← Phase 8 세 번째 기능 신규
│     │                                    AccountInternalClient 호출 → 미인증 시 /check-email-required 리다이렉트
│     ├── account/
│     │     controller/
│     │         AuthPageController.java    + GET /check-email-required ← Phase 8 세 번째 기능
│     │         AccountPageController.java  /settings/**
│     │         ProfilePageController.java  /profile/{nickname}
│     │     client/AccountInternalClient.java
│     │     dto/AccountSummaryDto.java, AccountSettingsDto.java
│     ├── study/
│     │     controller/StudyPageController.java
│     │     client/StudyInternalClient.java
│     │     dto/ StudyPageDataDto, MemberDto, JoinRequestDto, DashboardDto, StudySummaryDto
│     ├── event/
│     │     controller/EventPageController.java
│     │     client/EventInternalClient.java
│     │     dto/EventSummaryDto.java, EnrollmentDto.java
│     └── notification/                    ← Phase 5 완료
│           controller/NotificationPageController.java  GET /notifications
│           client/NotificationInternalClient.java
│
└── resources/
      templates/
          fragments.html
          index.html
          check-email-required.html        ← Phase 8 세 번째 기능 신규
          account/ (profile.html, check-email.html, check-email-token.html ← Phase 8 세 번째 기능 수정)
          study/   (form, view, members, settings/*)
          settings/ (profile, password, notifications, tags, zones)
          error/   (404.html, error.html)
          event/   (form.html, view.html)
          notifications.html
      static/css/ (auth-style.css, main-style.css)
      static/js/  (glass-validation.js)
```

---

## api-gateway application.yml 라우팅 (Phase 7 완료 후)

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

  - id: notification-service
    uri: lb://NOTIFICATION-SERVICE
    predicates:
      - Path=/api/notifications/**
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

  # 깊이 있는 방어 — 일반 게이트웨이에는 관리자 API 가 아예 존재하지 않는 것처럼 보이게
  # 관리자 기능은 admin-gateway(9080) 를 통해서만 접근 가능하다.
  - id: block-admin-api
    uri: no://op
    predicates:
      - Path=/api/admin/**
    filters:
      - SetStatus=404

  - id: frontend-service
    uri: lb://FRONTEND-SERVICE
    predicates:
      - Path=/**
    filters:
      - OptionalJwtFilter
```

---

## admin-gateway application.yml 라우팅

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

  - id: admin-service
    uri: lb://ADMIN-SERVICE
    predicates:
      - Path=/api/admin/**
    filters:
      - JwtAuthenticationFilter
      - AdminRoleFilter

  - id: admin-frontend
    uri: lb://ADMIN-FRONTEND
    predicates:
      - Path=/**
    filters:
      - OptionalJwtFilter
```

---

## 전체 서비스 기동 순서 (Phase 7 완료 후, 총 11개)

```
1. eureka-server         (:8761)
2. config-server         (:8888)
3. api-gateway           (:8080)
4. admin-gateway         (:9080)  ← Phase 7 신규
5. account-service       (:8081)  — IntelliJ Active profiles: local
6. study-service         (:8083)
7. event-service         (:8084)
8. notification-service  (:8085)
9. admin-service         (:8082)
10. frontend-service     (:8090)
11. admin-frontend       (:9000)  ← Phase 7 신규

[접속 경로]
일반 사용자: http://localhost:8080   (8090 직접 접속 금지)
관리자:     http://localhost:9080   (9000 직접 접속 금지)

두 게이트웨이 모두 Eureka 에 등록되면
  http://localhost:8761 에 ADMIN-GATEWAY 와 ADMIN-FRONTEND 가 UP 상태로 보여야 한다.
```

---

## Docker 기동 명령어

```bash
# PostgreSQL (기존 DB 시작)
docker start account-db
docker start study-db
docker start event-db

# Phase 5 신규 컨테이너 (최초 생성)
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

# notification-db: MongoDB → PostgreSQL 로 변경 (2026-04-02)
docker run -d --name notification-db \
  -e POSTGRES_DB=notification_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5436:5432 postgres:15

docker run -d --name redis \
  -p 6379:6379 redis:7-alpine

# 기동 확인 (8개 컨테이너 Up 상태여야 함)
docker ps
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
| 016-Phase5 | notification-service 기동 실패 + Kafka 역직렬화 실패 | IntelliJ Run Config의 Main class 패키지 오타 / JsonDeserializer default type 누락 |
| 017-Phase5 | 알림 읽음 처리 시 nav 배지 차감 불발 | fetch() 비동기 race condition — page navigation으로 in-flight 요청 취소 |

*Phase 5 관련 상세 트러블슈팅은 `TroubleShooting/TroubleShooting_016.MD`, `TroubleShooting_017.MD` 참고.*

---

## 학습 자료 문서 (프로젝트 루트)

| 파일 | 내용 |
|------|------|
| `RabbitMQ_Guide.md` | RabbitMQ 핵심 개념, Exchange 4종류, ACK/DLQ, StudyOlle 활용 코드 |
| `Kafka_Guide.md` | Kafka 핵심 개념, Topic/Partition/Offset, Consumer Group, StudyOlle 활용 코드 |
| `MSA_AUTH_FLOW.md` | 인증 및 요청 처리 흐름 (JWT, X-Account-Id, 내부 통신) |
| `회원_권한_변경.md` | Phase 8 첫 번째 기능 상세 — 5단계 호출 / 방어 깊이 3중 검증 |
| `스터디_강제_비공개.md` | Phase 8 두 번째 기능 상세 — Option A 상태 전이 / State Machine |
| `이메일_인증_접근제한.md` | Phase 8 세 번째 기능 상세 — 옵션 B 하이브리드 / JWT claim 방식 |

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 이어서 진행하세요.*
