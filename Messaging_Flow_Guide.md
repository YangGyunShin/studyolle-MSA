# StudyOlle MSA — 메시징 흐름 완전 가이드

> 작성일: 2026-04-02
> 대상: RabbitMQ / Kafka 흐름이 코드 레벨에서 어떻게 연결되는지 이해하고 싶은 사람

---

## 0. RabbitMQ는 Config 파일이 필요하고, Kafka는 왜 필요 없는가?

### RabbitMQ — Config 파일 필수

RabbitMQ 서버에 처음 접속했을 때는 Exchange도 Queue도 Binding도 **아무것도 없다.**
메시지를 보내려면 이 구조가 먼저 존재해야 한다.
존재하지 않으면 `rabbitTemplate.convertAndSend()` 호출 시 에러가 발생한다.

그래서 `RabbitMQConfig.java` 를 만들어 `@Bean` 으로 선언한다.
Spring 이 이 Bean 을 감지하면 **애플리케이션 시작 시** RabbitMQ 서버에 자동으로 만들어준다.

```
애플리케이션 시작
    │
    ▼
Spring: "RabbitMQConfig 에 Exchange Bean 있네!"
    │
    ▼
RabbitMQ 서버에 Exchange 생성 요청
    │
    ├── 이미 존재하면 → 그냥 사용 (멱등성)
    └── 없으면       → 새로 생성
```

`event-service` 와 `notification-service` 양쪽에 동일하게 선언하는 이유:
- event-service 가 먼저 뜨면 → event-service 가 Exchange/Queue 생성
- notification-service 가 먼저 뜨면 → notification-service 가 Exchange/Queue 생성
- 어느 쪽이 먼저 뜨든 상관없이 항상 정상 동작한다 (멱등성)

### Kafka — Config 파일 불필요

Kafka 는 **Topic 이 없으면 자동으로 생성**한다. (`auto.create.topics.enable=true` 기본값)
`kafkaTemplate.send("study-events", ...)` 를 처음 호출하는 순간 Topic 이 자동 생성된다.

연결 정보(서버 주소, group-id 등)만 `application.yml` 에 설정하면 끝이다.
별도 Config 파일이 필요 없다.

```
RabbitMQ : 구조(Exchange/Queue/Binding)를 코드로 미리 선언해야 함  → Config 필수
Kafka    : Topic 이 자동 생성됨, 연결 정보만 yml 에 설정하면 됨    → Config 불필요
```

---

## 1. RabbitMQ 전체 흐름 — 모임 신청 수락 시나리오

### 시나리오

```
스터디 관리자가 "스프링 부트 스터디 첫 모임" 의 신청자(accountId=123)를 수락한다.
신청자에게 "모임 신청이 수락됐습니다." 알림이 전달되어야 한다.
```

---

### STEP 1. 브라우저 → api-gateway → event-service

```
[관리자 브라우저]
   │
   │  PATCH /api/studies/spring-study/events/1/enrollments/1/accept
   │  Authorization: Bearer {JWT}
   │  X-Account-Id: 999  (관리자 ID)
   │
   ▼
[api-gateway :8080]
   │  JwtAuthenticationFilter: JWT 검증 → X-Account-Id: 999 헤더 추가
   │
   ▼
[event-service :8084]
   │
   │  EventController.acceptEnrollment()
   │      @PostMapping("/api/studies/{path}/events/{eventId}/enrollments/{enrollmentId}/accept")
   │      public ResponseEntity<?> acceptEnrollment(
   │              @PathVariable Long eventId,        // 1
   │              @PathVariable Long enrollmentId,   // 1
   │              @RequestHeader("X-Account-Id") Long accountId) {  // 999
   │
   │          enrollmentService.acceptEnrollment(eventId, enrollmentId, accountId);
   │          return ResponseEntity.ok(CommonApiResponse.ok("참가 신청을 승인했습니다."));
   │      }
```

---

### STEP 2. EnrollmentService — 핵심 로직 처리 + 패킷 만들기

```java
// EnrollmentService.java (event-service)

public void acceptEnrollment(Long eventId, Long enrollmentId, Long accountId) {

    // ① DB 에서 Event 조회
    Event event = eventService.getEventWithEnrollments(eventId);
    //   event.getId()    = 1
    //   event.getTitle() = "스프링 부트 스터디 첫 모임"
    //   event.getStudyPath() = "spring-study"

    // ② 관리자 권한 검증 (관리자만 수락 가능)
    eventService.validateManager(event, accountId);  // accountId=999

    // ③ 신청 내역 조회
    Enrollment enrollment = findEnrollment(enrollmentId);
    //   enrollment.getAccountId() = 123  ← 신청자 ID (알림 받을 사람)
    //   enrollment.isAccepted()   = false (아직 수락 안 됨)

    // ④ [핵심] DB 에 수락 상태 저장
    //    enrollment.accepted = true 로 변경
    //    Dirty Checking 으로 자동 UPDATE (save() 호출 불필요)
    event.accept(enrollment);

    // ⑤ [핵심] RabbitMQ 로 패킷 발행 ← 이 시점에 패킷을 만들어 던짐
    enrollmentRabbitProducer.send(
        EnrollmentEventDto.builder()
            .eventType("enrollment.accepted")  // Routing Key 와 동일
            .eventId(eventId)                  // 1
            .eventTitle(event.getTitle())      // "스프링 부트 스터디 첫 모임"
            .studyPath(event.getStudyPath())   // "spring-study"
            .enrollmentAccountId(enrollment.getAccountId())  // 123 ← 신청자
            .managedByAccountId(accountId)     // 999 ← 처리한 관리자
            .occurredAt(LocalDateTime.now())
            .build()
    );
}
```

---

### STEP 3. EnrollmentRabbitProducer — 패킷을 우체국에 던짐

```java
// EnrollmentRabbitProducer.java (event-service)

public void send(EnrollmentEventDto event) {
    try {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,   // "event.notification"  ← 우체국 이름
            event.getEventType(),      // "enrollment.accepted" ← 우편번호 (Routing Key)
            event                      // EnrollmentEventDto    ← 편지 내용 (JSON 변환됨)
        );
        // 내부 동작:
        // 1. Jackson2JsonMessageConverter 가 EnrollmentEventDto → JSON 변환
        //    {
        //      "eventType": "enrollment.accepted",
        //      "eventId": 1,
        //      "eventTitle": "스프링 부트 스터디 첫 모임",
        //      "studyPath": "spring-study",
        //      "enrollmentAccountId": 123,
        //      "managedByAccountId": 999,
        //      "occurredAt": "2026-04-02T10:00:00"
        //    }
        // 2. "event.notification" Exchange 에 전달
        // 3. Routing Key "enrollment.accepted" 포함

        log.info("[RabbitMQ] 전송 성공: routingKey=enrollment.accepted, enrollmentAccountId=123");

    } catch (Exception e) {
        // 전송 실패해도 예외를 던지지 않는다!
        // 이유: DB 에는 이미 수락 상태가 저장됐다.
        //       여기서 예외를 던지면 트랜잭션이 롤백되어 DB 반영이 취소된다.
        //       알림 전송 실패 때문에 수락 자체가 취소되면 안 된다.
        log.error("[RabbitMQ] 전송 실패: {}", e.getMessage());
    }
}
```

---

### STEP 4. RabbitMQ 서버 (Docker) — 우체국이 라우팅 처리

```
[RabbitMQ 서버 - localhost:5672]
   │
   │  Exchange "event.notification" 수신:
   │    메시지: { "eventType": "enrollment.accepted", ... }
   │    Routing Key: "enrollment.accepted"
   │
   │  Binding 규칙 확인:
   │    "enrollment.accepted" 가 "enrollment.#" 패턴에 맞는가?
   │      enrollment. ← 시작 부분 일치 ✅
   │      accepted    ← # 에 매칭 ✅
   │    → 맞음! → "enrollment.queue" 로 라우팅
   │
   │  enrollment.queue 에 메시지 추가:
   │    [메시지1: enrollment.accepted]  ← 방금 도착한 것
   │    [메시지2: ...]
   │    [메시지3: ...]
   │
   │  (notification-service 가 처리할 준비가 될 때까지 보관)
```

> **RabbitMQ Management UI** (`http://localhost:15672`) 에서 직접 확인 가능.
> Queue 에 메시지가 쌓이는 것, 처리되는 것을 실시간으로 볼 수 있다.
> 기본 계정: guest / guest

---

### STEP 5. EnrollmentEventConsumer — 우체통에서 꺼내서 처리

```java
// EnrollmentEventConsumer.java (notification-service)

@RabbitListener(queues = RabbitMQConfig.QUEUE)  // "enrollment.queue" 를 계속 감시
public void consume(EnrollmentEventDto event) {
    // 이 메서드는 Queue 에 메시지가 도착하는 순간 자동으로 실행된다.
    // Spring 이 내부적으로 별도 스레드로 Queue 를 폴링하다가
    // 메시지가 오면 JSON → EnrollmentEventDto 로 역직렬화해서 이 메서드를 호출한다.

    // event 에 담긴 값:
    //   event.getEventType()              = "enrollment.accepted"
    //   event.getEventId()                = 1
    //   event.getEventTitle()             = "스프링 부트 스터디 첫 모임"
    //   event.getStudyPath()              = "spring-study"
    //   event.getEnrollmentAccountId()    = 123  ← 신청자 (알림 받을 사람)
    //   event.getManagedByAccountId()     = 999  ← 처리한 관리자

    log.info("[RabbitMQ] 수신: eventType=enrollment.accepted, enrollmentAccountId=123");

    // ① 중복 이벤트 방지
    //    "enrollment:1:enrollment.accepted" 키가 Redis 에 없으면 저장 + true 반환
    //    이미 있으면 → 중복! → return (처리 안 함)
    String dedupKey = "enrollment:1:enrollment.accepted";
    if (!notificationService.isFirstProcessing(dedupKey)) {
        log.warn("[RabbitMQ] 중복 이벤트 무시");
        return;
    }

    // ② eventType 에 따라 알림 메시지 결정
    String message = switch (event.getEventType()) {
        case "enrollment.accepted" ->
            "[스프링 부트 스터디 첫 모임] 모임 신청이 수락됐습니다.";
        case "enrollment.rejected" ->
            "[스프링 부트 스터디 첫 모임] 모임 신청이 거절됐습니다.";
        case "enrollment.applied" ->
            "[스프링 부트 스터디 첫 모임] 모임 신청이 완료됐습니다.";
        case "enrollment.attendance" ->
            "[스프링 부트 스터디 첫 모임] 출석이 확인됐습니다.";
        default -> null;
    };

    // ③ 알림 생성 요청
    notificationService.createNotification(
        123L,                                          // accountId: 신청자에게
        "[스프링 부트 스터디 첫 모임] 모임 신청이 수락됐습니다.",
        "/study/spring-study/events/1",               // 클릭 시 이동할 링크
        NotificationType.ENROLLMENT
    );

    // 메서드가 정상 리턴되면 RabbitMQ 가 자동으로 ACK (메시지 Queue 에서 삭제)
}
```

---

### STEP 6. NotificationService — DB 저장 + Redis 카운터 증가

```java
// NotificationService.java (notification-service)

public void createNotification(Long accountId, String message,
                               String link, NotificationType type) {

    // ① PostgreSQL 에 알림 영구 저장
    notificationRepository.save(
        Notification.builder()
            .accountId(123L)
            .message("[스프링 부트 스터디 첫 모임] 모임 신청이 수락됐습니다.")
            .link("/study/spring-study/events/1")
            .type(NotificationType.ENROLLMENT)
            .checked(false)          // 아직 읽지 않음
            .createdAt(LocalDateTime.now())
            .build()
    );
    // 실행되는 SQL:
    // INSERT INTO notifications (account_id, message, link, type, checked, created_at)
    // VALUES (123, '...', '/study/...', 'ENROLLMENT', false, NOW())

    // ② Redis 카운터 +1
    //    KEY: "notification:unread:123"
    //    VALUE: "0" → "1" (INCR 명령어)
    redisTemplate.opsForValue().increment("notification:unread:123");

    log.info("[Notification] 생성: accountId=123, type=ENROLLMENT");
}
```

---

### STEP 7. 나중에 사용자가 페이지를 열면

```
[신청자(accountId=123) 브라우저]
   │
   │  GET http://localhost:8080/
   │  (쿠키에 accessToken 포함)
   │
   ▼
[api-gateway]
   │  OptionalJwtFilter: 쿠키에서 JWT 읽기 → X-Account-Id: 123 헤더 추가
   │
   ▼
[frontend-service HomeController.java]
   │
   │  @GetMapping("/")
   │  public String home(@RequestHeader("X-Account-Id") Long accountId, Model model) {
   │
   │      // 읽지 않은 알림 수 조회 (Redis 에서 즉시)
   │      long unreadCount = notificationInternalClient.getUnreadCount(accountId);
   │      //   → GET lb://NOTIFICATION-SERVICE/internal/notifications/count/123
   │      //   → Redis GET "notification:unread:123" → "1"
   │      //   → 반환값: 1
   │
   │      model.addAttribute("unreadCount", 1L);
   │      return "index";
   │  }
   │
   ▼
[templates/index.html - Thymeleaf 렌더링]
   │
   │  nav 바 🔔 뱃지:
   │  <span th:if="${unreadCount > 0}" th:text="${unreadCount}">1</span>
   │
   ▼
[브라우저에 렌더링된 HTML]
   nav 바에 🔔 1 표시됨!
```

---

## 2. Kafka 전체 흐름 — 스터디 공개 시나리오

### 시나리오

```
스터디 관리자가 "스프링 부트 스터디" 를 공개한다.
관리자에게 "스터디가 공개됐습니다." 알림이 전달되어야 한다.
```

---

### STEP 1. 브라우저 → api-gateway → study-service

```
[관리자 브라우저]
   │
   │  POST /api/studies/spring-study/settings/publish
   │  X-Account-Id: 999 (관리자 ID)
   │
   ▼
[api-gateway :8080]
   │
   ▼
[study-service :8083]
   │
   │  StudySettingsController.publish()
   │      @PostMapping("/api/studies/{path}/settings/publish")
   │      public ResponseEntity<?> publish(
   │              @RequestHeader("X-Account-Id") Long accountId,  // 999
   │              @PathVariable String path) {                     // "spring-study"
   │
   │          Study study = studyService.getStudyToUpdateStatus(accountId, path);
   │          studySettingsService.publish(study, accountId);  // accountId 전달
   │          return ResponseEntity.ok(CommonApiResponse.ok("스터디를 공개했습니다."));
   │      }
```

---

### STEP 2. StudySettingsService — DB 저장 + Kafka 발행

```java
// StudySettingsService.java (study-service)

public void publish(Study study, Long accountId) {

    // ① DB 에 공개 상태 저장 (Dirty Checking 으로 자동 UPDATE)
    study.publish();
    //   study.published = true
    //   study.publishedDateTime = LocalDateTime.now()

    // ② [핵심] Kafka 로 이벤트 발행
    studyKafkaProducer.sendStudyEvent(
        StudyEventDto.builder()
            .eventType("STUDY_PUBLISHED")         // 어떤 이벤트인지
            .studyPath(study.getPath())            // "spring-study" ← Kafka Key 로 사용
            .studyTitle(study.getTitle())          // "스프링 부트 스터디"
            .triggeredByAccountId(accountId)       // 999 ← 이벤트 발생시킨 사람
            .occurredAt(LocalDateTime.now())
            .build()
    );
}
```

---

### STEP 3. StudyKafkaProducer — Kafka 에 메시지 발행

```java
// StudyKafkaProducer.java (study-service)

public void sendStudyEvent(StudyEventDto event) {
    kafkaTemplate.send(
        "study-events",          // Topic 이름
        event.getStudyPath(),    // Key = "spring-study"
                                 // 같은 스터디 이벤트는 항상 같은 Partition → 순서 보장
        event                    // Value = StudyEventDto (JSON 변환됨)
    )
    .whenComplete((result, ex) -> {
        if (ex == null) {
            // 전송 성공
            // result.getRecordMetadata().partition() → 어느 Partition 에 들어갔는지
            // result.getRecordMetadata().offset()    → 그 Partition 의 몇 번째인지
            log.info("[Kafka] 전송 성공: partition={}, offset={}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } else {
            // 전송 실패해도 예외를 던지지 않음
            // 스터디 공개 자체는 이미 DB 에 반영됐으므로 롤백되면 안 됨
            log.error("[Kafka] 전송 실패: {}", ex.getMessage());
        }
    });
}
```

---

### STEP 4. Kafka 서버 — Topic 에 메시지 저장

```
[Kafka 서버 - localhost:9092]
   │
   │  Topic: "study-events" (Partition 이 여러 개)
   │
   │  Key = "spring-study" 의 해시값으로 Partition 결정
   │  예: "spring-study" → Partition 2 로 라우팅
   │
   │  Partition 2:
   │  Offset 0: { "eventType": "STUDY_CREATED",   "studyPath": "spring-study", ... }
   │  Offset 1: { "eventType": "STUDY_PUBLISHED",  "studyPath": "spring-study", ... }  ← 방금 도착
   │  Offset 2: ...
   │
   │  메시지는 삭제되지 않고 retention 기간(기본 7일) 동안 보존된다.
   │  다른 Consumer Group 이 나중에 읽어도 메시지가 그대로 있다.
```

---

### STEP 5. StudyEventConsumer — Kafka 에서 메시지 수신

```java
// StudyEventConsumer.java (notification-service)

@KafkaListener(
    topics = "study-events",
    groupId = "notification-service"
    // groupId 가 "notification-service" 이므로
    // 다른 서비스(analytics-service 등)가 같은 Topic 을 읽어도 독립적으로 동작
)
public void consume(
        @Payload StudyEventDto event,
        //   event.getEventType()           = "STUDY_PUBLISHED"
        //   event.getStudyPath()           = "spring-study"
        //   event.getStudyTitle()          = "스프링 부트 스터디"
        //   event.getTriggeredByAccountId()= 999

        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,  // 2
        @Header(KafkaHeaders.OFFSET) long offset,                // 1
        Acknowledgment ack) {

    log.info("[Kafka] 수신: partition=2, offset=1, eventType=STUDY_PUBLISHED");

    // ① 중복 방지 체크
    String dedupKey = "study:spring-study:2026-04-02T10:00:00";
    if (!notificationService.isFirstProcessing(dedupKey)) {
        ack.acknowledge();  // 중복이어도 Offset 은 커밋 필수
        return;
    }

    // ② 알림 메시지 결정
    String message = "[스프링 부트 스터디] 스터디가 공개됐습니다.";

    // ③ 알림 생성
    notificationService.createNotification(
        999L,                           // triggeredByAccountId (관리자에게 알림)
        message,
        "/study/spring-study",
        NotificationType.STUDY
    );

    ack.acknowledge();  // Offset 커밋 → 재처리 방지
    // RabbitMQ 와 다르게 명시적으로 호출해야 한다.
    // 호출 안 하면 서비스 재시작 시 이 메시지부터 다시 처리한다.
}
```

---

## 3. RabbitMQ vs Kafka 흐름 비교

```
RabbitMQ (event-service → notification-service)

  EnrollmentService          EnrollmentRabbitProducer    RabbitMQ         EnrollmentEventConsumer
       │                            │                       │                      │
       │ acceptEnrollment()         │                       │                      │
       │────────────────────────→  │                       │                      │
       │                           │ convertAndSend()       │                      │
       │                           │──────────────────────→│                      │
       │                           │  Exchange 라우팅        │                      │
       │                           │  → enrollment.queue   │                      │
       │                           │                       │ Queue 에 보관          │
       │                           │                       │──────────────────────→│
       │                           │                       │                      │ consume()
       │                           │                       │                      │ 자동 실행


Kafka (study-service → notification-service)

  StudySettingsService       StudyKafkaProducer          Kafka            StudyEventConsumer
       │                            │                       │                      │
       │ publish()                  │                       │                      │
       │────────────────────────→  │                       │                      │
       │                           │ send()                 │                      │
       │                           │──────────────────────→│                      │
       │                           │                       │ Partition 에 저장      │
       │                           │                       │ (7일간 보존)           │
       │                           │                       │                      │
       │                           │                       │  Consumer 가 Pull     │
       │                           │                       │←─────────────────────│
       │                           │                       │ 메시지 전달            │
       │                           │                       │──────────────────────→│
       │                           │                       │                      │ consume()
       │                           │                       │                      │ ack.acknowledge()
```

핵심 차이:
- RabbitMQ: 브로커가 Consumer 에게 **밀어넣음 (Push)**. 메서드 리턴 시 자동 ACK.
- Kafka: Consumer 가 브로커에서 **당겨옴 (Pull)**. `ack.acknowledge()` 를 수동 호출.

---

## 4. 관련 파일 위치 요약

```
study-service/
  service/StudySettingsService.java   publish() → studyKafkaProducer.sendStudyEvent()
  kafka/StudyKafkaProducer.java       kafkaTemplate.send("study-events", studyPath, event)
  kafka/StudyEventDto.java            Kafka 발행용 DTO

event-service/
  service/EnrollmentService.java      acceptEnrollment() → enrollmentRabbitProducer.send()
  rabbitmq/EnrollmentRabbitProducer.java  rabbitTemplate.convertAndSend(EXCHANGE, key, event)
  rabbitmq/EnrollmentEventDto.java    RabbitMQ 발행용 DTO
  rabbitmq/RabbitMQConfig.java        Exchange / Queue / Binding 선언 (Producer 측)

notification-service/
  kafka/StudyEventConsumer.java       @KafkaListener → notificationService.createNotification()
  kafka/StudyEventDto.java            Kafka 수신용 DTO (study-service 와 동일 구조)
  rabbitmq/EnrollmentEventConsumer.java  @RabbitListener → notificationService.createNotification()
  rabbitmq/EnrollmentEventDto.java    RabbitMQ 수신용 DTO (event-service 와 동일 구조)
  config/RabbitMQConfig.java          Exchange / Queue / Binding 선언 (Consumer 측)
  service/NotificationService.java    createNotification() → PostgreSQL + Redis
  controller/NotificationController.java            /api/notifications/** (브라우저 호출)
  controller/NotificationInternalController.java    /internal/notifications/** (서버 호출)
```

---

*관련 문서: RabbitMQ_Guide.md / Kafka_Guide.md / MSA_GUIDE.md*
