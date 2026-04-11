# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.

---

## 현재 진행 상황 (2026-04-11 기준)

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
| Phase 5 | Docker 기동 (Zookeeper, Kafka, RabbitMQ, PostgreSQL, Redis) | ✅ 완료 |
| Phase 5 | study-service Kafka Producer | ✅ 완료 |
| Phase 5 | event-service RabbitMQ Producer | ✅ 완료 |
| Phase 5 | notification-service 소스코드 (18개 파일) | ✅ 완료 |
| Phase 5 | notification-service 기동 + Kafka 흐름 검증 | ✅ 완료 |
| Phase 5 | api-gateway /api/notifications/** 라우팅 | ✅ 완료 |
| Phase 5 | frontend-service 알림 페이지 연동 | ✅ 완료 |
| Phase 5 | RabbitMQ 흐름 테스트 (event-service → notification) | 🔲 대기 중 |
| Phase 6 | admin-service | 🔲 예정 |

**다음 즉시 할 일:**
1. RabbitMQ 흐름 테스트 (모임 신청/수락/거절/출석)
2. Phase 6 admin-service 시작

자세한 TODO는 `MSA_TODO.txt` 참고.

---

## 아키텍처 요약

```
[브라우저]
    │
    ▼
[api-gateway :8080]   JWT 검증(쿠키/헤더), 라우팅, X-Account-Id 헤더 추가
    │
    ├── [account-service      :8081]   회원가입/로그인/JWT 발급/계정 설정/프로필
    ├── [study-service        :8083]   스터디 CRUD/설정/가입 + Kafka Producer (Phase 5)
    ├── [event-service        :8084]   모임 생성/신청 + RabbitMQ Producer (Phase 5)
    ├── [notification-service :8085]   알림 저장/조회 (Phase 5) ← 신규
    │       ├── Kafka Consumer    (study-events Topic)
    │       ├── RabbitMQ Consumer (enrollment.queue)
    │       ├── PostgreSQL :5436  (알림 영구 저장)
    │       └── Redis :6379       (읽지 않은 알림 카운터, 중복 방지)
    └── [frontend-service     :8090]   Thymeleaf HTML 서빙

[frontend-service :8090]
    │  브라우저 렌더링 전 RestTemplate 으로 내부 API 호출
    ├── AccountInternalClient      lb://ACCOUNT-SERVICE/internal/**
    ├── StudyInternalClient        lb://STUDY-SERVICE/internal/**
    ├── EventInternalClient        lb://EVENT-SERVICE/internal/**    ✅ 완성
    └── NotificationInternalClient lb://NOTIFICATION-SERVICE/internal/**  ← Phase 5 추가 예정

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

## api-gateway 필터 구조 (현재)

```
[브라우저 요청]
      │
      ▼
  라우트 매칭
      │
      ├── /api/auth/**             → ACCOUNT-SERVICE      (필터 없음, 공개)
      ├── /api/accounts/**         → ACCOUNT-SERVICE      (JwtAuthenticationFilter)
      ├── /api/notifications/**    → NOTIFICATION-SERVICE (JwtAuthenticationFilter) ← Phase 5 추가 예정
      ├── /api/studies/*/events/** → EVENT-SERVICE        (JwtAuthenticationFilter)
      ├── /api/studies/**          → STUDY-SERVICE        (JwtAuthenticationFilter)
      ├── /internal/**             → 403 전면 차단
      │
      └── /**                      → FRONTEND-SERVICE
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
③ localStorage.setItem('accessToken', ...)          ← fetch() 호출용
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

## event-service 구조 (Phase 4 완성 + Phase 5 추가)

```
event-service/src/main/java/com/studyolle/event/
├── EventServiceApplication.java
├── config/
│     SecurityConfig.java
│     WebMvcConfig.java                InternalRequestFilter 등록
├── controller/
│     EventController.java             /api/studies/{path}/events/** (11개 엔드포인트)
│     EventInternalController.java     /internal/events/** (3개 엔드포인트)
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

## study-service 주요 파일 목록 (Phase 4 완성 + Phase 5 추가)

```
study-service/src/main/java/com/studyolle/study/
├── client/
│     MetadataFeignClient.java
│     EventFeignClient.java           event-service 호출
├── client/dto/
│     EventSummaryDto.java
├── kafka/                            ← Phase 5 추가
│     StudyEventDto.java              Kafka 발행 DTO
│     StudyKafkaProducer.java         KafkaTemplate.send(topic, studyPath, event)
│                                     전송 실패 시 예외 삼킴 (부가 기능)
└── service/
      StudyService.java               createNewStudy() → STUDY_CREATED 발행
      StudySettingsService.java       publish()        → STUDY_PUBLISHED 발행
                                      startRecruit()   → RECRUITING_STARTED 발행
                                      stopRecruit()    → RECRUITING_STOPPED 발행
                                      (accountId 파라미터 추가됨)

Kafka 설정:
  Topic  : study-events
  Key    : studyPath (같은 스터디 → 같은 Partition → 순서 보장)
  발행 시점: 스터디 생성 / 공개 / 모집 시작 / 모집 종료
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
│         GET /internal/accounts/by-nickname/{nickname}
├── filter/
│     InternalRequestFilter.java  (ALLOWED: frontend-service, study-service, admin-service, event-service)
├── repository/
│     AccountRepository.java  (findByNickname 포함)
└── ...
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
│     └── notification/                    ← Phase 5 추가 예정
│           controller/NotificationPageController.java  GET /notifications
│           client/NotificationInternalClient.java
│
└── resources/
      templates/
          fragments.html       (nav 🔔 뱃지 연동 예정)
          index.html
          account/ (profile.html 포함)
          study/   (form, view, members, settings/*)
          settings/ (profile, password, notifications, tags, zones)
          error/   (404.html, error.html)
          event/   (form.html, view.html)
          notifications.html   ← Phase 5 추가 예정 (Claude 담당)
      static/css/ (auth-style.css, main-style.css)
      static/js/  (glass-validation.js)
```

---

## api-gateway application.yml 라우팅 (현재 + Phase 5 예정)

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

  # Phase 5 추가 예정
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
1. eureka-server        (:8761)
2. config-server        (:8888)
3. api-gateway          (:8080)
4. account-service      (:8081)  — IntelliJ Active profiles: local
5. study-service        (:8083)
6. event-service        (:8084)
7. notification-service (:8085)  ← Phase 5 추가
8. frontend-service     (:8090)

접속: http://localhost:8080  (8090 직접 접속 금지 — OptionalJwtFilter 우회)
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

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 이어서 진행하세요.*
