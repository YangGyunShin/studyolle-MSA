# Kafka — 쉽게 이해하는 완벽 가이드

> 작성일: 2026-04-02 | 프로젝트: StudyOlle MSA Phase 5

---

## 0. Kafka가 왜 필요한가? (RabbitMQ와 뭐가 다른가?)

### RabbitMQ의 한계를 보여주는 상황

| 상황 | RabbitMQ | Kafka |
|------|----------|-------|
| 이미 처리한 메시지를 다시 보고 싶다 | ❌ "이미 삭제됐는데요?" | ✅ "원하는 시점부터 다시 읽어드릴게요" |
| 초당 100만 건 처리 | ❌ 최대 10,000건 | ✅ 초당 100만 건도 거뜬 |
| 여러 서비스가 같은 이벤트를 각자 처리 | △ 복잡한 설정 필요 | ✅ groupId만 다르게 → 기본 기능! |

### 핵심 차이: 우체통 vs 도서관

```
RabbitMQ = 우체통
  편지(메시지)를 받으면 배달하고 사라진다.
  한 번 받아가면 없어진다.

Kafka = 도서관
  책(메시지)이 서가에 꽂혀 있다.
  누군가 읽어도 책은 그대로 있다.
  다른 사람도 읽을 수 있고, 다시 읽을 수도 있다.
  오래된 것은 나중에(retention 기간 지나면) 정리한다.
```

---

## 1. Kafka의 핵심 등장인물

| Kafka | 도서관 비유 | 설명 |
|-------|-----------|------|
| **Producer** | 책 기증자 | 메시지를 만들어서 Kafka에 보내는 서비스 |
| **Topic** | 책 카테고리 | 메시지의 주제/분류 (예: "orders" 토픽) |
| **Partition** | 책장 칸 | Topic을 나눈 병렬 처리 단위 |
| **Offset** | 페이지 번호 | 파티션 내 메시지의 순번 (위치 추적용) |
| **Broker** | 도서관 건물 | Kafka 서버 (여러 개 클러스터로 운영) |
| **Consumer** | 책 읽는 사람 | 메시지를 읽어서 처리하는 서비스 |
| **Consumer Group** | 독서 모임 | 같은 Topic을 함께 나눠 읽는 Consumer 팀 |

### 메시지 흐름

```
Producer → Topic(Partition) → Consumer Group → Consumer
(보내는 서비스)  (카테고리/책장칸)   (독서모임)       (읽는 서비스)
```

---

## 2. Topic — 메시지의 카테고리

Topic은 "같은 종류의 메시지를 모아두는 공간"이다.

> **RabbitMQ Queue vs Kafka Topic**
> - RabbitMQ Queue: 메시지를 읽으면 **사라진다**
> - Kafka Topic: 메시지를 읽어도 **그대로 남아 있다!**

```java
@Bean
public NewTopic studyEventsTopic() {
    return TopicBuilder.name("study-events")
        .partitions(3)    // 파티션 3개
        .replicas(1)      // 복제본 1개 (개발환경)
        .build();
}
```

---

## 3. Partition — Kafka 성능의 비밀 ⭐

이게 Kafka의 핵심 개념이자 가장 중요한 부분이다.

### 비유: 슈퍼마켓 계산대

```
계산대 1개:   손님이 줄 서서 하나씩 처리 → 느림
계산대 3개:   손님을 3개 줄로 나눠서 동시에 처리 → 3배 빠름!

Kafka의 Partition = 계산대 수
Consumer           = 계산원
```

### 그림으로 보기

```
Topic: "study-events" (Partition 3개)
┌──────────────────────────────────────────────────────────┐
│ Partition 0: [msg0][msg1][msg2][msg3]...                 │
│ Partition 1: [msg0][msg1][msg2]...                       │
│ Partition 2: [msg0][msg1][msg2][msg3][msg4]...           │
└──────────────────────────────────────────────────────────┘
       ↓              ↓              ↓
 [Consumer A]   [Consumer B]   [Consumer C]  ← 동시에 처리!
```

### 중요 규칙

| 상황 | 결과 |
|------|------|
| Partition 3개, Consumer 3개 | 딱 맞게 분배 ✅ (이상적) |
| Partition 3개, Consumer 2개 | Consumer 하나가 Partition 2개 담당 |
| Partition 3개, Consumer 4개 | Consumer 하나가 놀게 됨 (낭비!) |

> **Partition 수 = Consumer 수** 로 맞추는 게 이상적!

### 메시지는 어느 Partition으로 가나?

```java
// Key 있는 전송 → 같은 studyPath는 항상 같은 Partition → 순서 보장!
kafkaTemplate.send("study-events", studyPath, event);
//                  Topic          Key         메시지

// Key 없는 전송 → Round-Robin 분산, 순서 보장 안됨
kafkaTemplate.send("study-events", event);
```

---

## 4. Offset — 내가 어디까지 읽었는지 기억하는 책갈피

Offset은 Partition 안에서 각 메시지에 붙는 **"번호표"** 다.

### 그림으로 보기

```
Partition 0:
┌────┬────┬────┬────┬────┬────┐
│ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │  ← Offset 번호
│msg │msg │msg │msg │msg │msg │
└────┴────┴────┴────┴────┴────┘
                   ↑
         Consumer A는 Offset 2까지 읽음 (다음에 3부터 읽을 것)
```

> **RabbitMQ:** 브로커(서버)가 "이 Consumer는 어디까지 읽었다"를 관리  
> **Kafka:** **Consumer가 직접** "나는 Offset 몇까지 읽었다"를 기록

Consumer가 Offset을 직접 관리하기 때문에:
- ✅ "어제 거 다시 읽고 싶어" → Offset을 어제로 되돌리면 됨 (replay)
- ✅ "처음부터 다시 읽고 싶어" → Offset을 0으로 되돌리면 됨
- ✅ 새 Consumer가 생겨도 → 원하는 시점부터 읽기 가능

### Offset 커밋 코드

```java
@KafkaListener(topics = "study-events", groupId = "notification-service")
public void consume(StudyEventDto event, Acknowledgment ack) {
    try {
        notificationService.save(event);
        ack.acknowledge();   // 처리 완료 → Offset 커밋
    } catch (Exception e) {
        // ack 호출 안하면 → 재시작 시 이 메시지부터 다시 읽음
        log.error("처리 실패", e);
    }
}
```

---

## 5. Consumer Group — 여러 서비스가 같은 Topic을 읽는 방법 ⭐

이게 Kafka의 킬러 기능이다.

### 상황: "study-events" Topic을 3개 서비스가 읽고 싶다

- `notification-service` : 알림을 보내야 함
- `analytics-service` : 통계를 내야 함
- `search-service` : 검색 인덱스를 업데이트해야 함

### Consumer Group으로 해결

각 서비스가 **다른 groupId**를 가지면 독립적으로 같은 메시지를 읽는다!

```
Topic: "study-events"
Partition 0, 1, 2 에 메시지가 있다.

notification-service (groupId: "notification-group")
  → 파티션 전체를 처음부터 읽음. Offset 따로 관리.

analytics-service (groupId: "analytics-group")
  → 같은 메시지를 처음부터 또 읽음. 서로 영향 없음.

search-service (groupId: "search-group")
  → 마찬가지로 독립적으로 읽음.
```

```
                Topic: "study-events"
                [A][B][C][D][E][F][G]...
                         │
         ┌───────────────┼────────────────┐
         ↓               ↓                ↓
  [notification]    [analytics]       [search]
  groupId=A          groupId=B         groupId=C
  전부 읽음           전부 읽음          전부 읽음
```

### Spring Boot groupId 설정

```yaml
# application.yml
spring:
  kafka:
    consumer:
      group-id: notification-service  # 이 이름이 Consumer Group ID
```

```java
// 또는 @KafkaListener에서 직접 지정
@KafkaListener(topics = "study-events", groupId = "notification-service")
public void consume(StudyEventDto event) { ... }
```

---

## 6. Broker, Leader, Follower — 장애가 나도 안전한 이유

### Broker란?

Kafka 서버 하나하나를 Broker라고 부른다.  
운영환경에서는 최소 3개의 Broker를 묶어서 **클러스터**로 운영한다.

```
Broker 1  ──┐
Broker 2  ──┼── Kafka Cluster
Broker 3  ──┘
```

### Replication — 데이터를 여러 곳에 복사

```
Partition 0 (replicas=3인 경우)
  Broker 1: Partition 0 Leader   ← 실제 읽기/쓰기 담당
  Broker 2: Partition 0 Follower ← Leader 데이터 복제
  Broker 3: Partition 0 Follower ← Leader 데이터 복제

→ Broker 1이 죽으면? Broker 2 또는 3이 자동으로 Leader 승격!
→ 데이터 유실 없이 서비스 계속!
```

### 운영 환경 황금 공식

```java
TopicBuilder.name("orders")
    .partitions(3)
    .replicas(3)
    .config("min.insync.replicas", "2")  // 최소 2개에 저장 확인 후 완료
    .build();
```

---

## 7. ZooKeeper vs KRaft — Kafka의 최신 변화

### 과거: ZooKeeper 필요했음

```
Kafka 3개 + ZooKeeper 3개 = 총 6개 서버 관리
→ 복잡하고 힘들었다.
```

### 현재: Kafka 4.0부터 ZooKeeper 완전 제거

| 버전 | 시점 | 변화 |
|------|------|------|
| Kafka 3.3 | 2022 | KRaft 프로덕션 준비 완료 |
| Kafka 3.5 | 2023 | ZooKeeper 공식 deprecated |
| Kafka 3.9 | 2024.11 | ZooKeeper 지원 마지막 버전 |
| **Kafka 4.0** | **2025.03** | **ZooKeeper 완전 제거, KRaft only** |

> **KRaft** = Kafka 자체에 내장된 Raft 합의 프로토콜  
> → 별도 ZooKeeper 클러스터 불필요, 운영 복잡도 대폭 감소

> **StudyOlle Phase 5에서는?**  
> 학습 목적으로 Docker에서 ZooKeeper + Kafka를 함께 띄운다.  
> (Confluent 이미지 기준, 운영 프로젝트라면 KRaft 방식을 쓰는 게 맞다)

---

## 8. Retention & Log Compaction — 메시지를 언제 지우나?

### Retention — 메시지 보존 기간

```yaml
spring:
  kafka:
    # 기본값: 7일 (168시간)
    # log.retention.hours=168
    # log.retention.bytes=1073741824  (1GB)
    # 둘 다 설정하면 먼저 도달하는 조건 적용
```

### Log Compaction — 키의 최신 값만 남기기

```
Before: [user:1, 이름=김철수]  Offset 0
        [user:1, 이름=이영희]  Offset 5
        [user:1, 이름=박민준]  Offset 12  ← 최신

After (Log Compaction):
        [user:1, 이름=박민준]  ← 최신 값만 남음
```

> **언제 쓰나:** 사용자 프로필 변경 이력, 설정값 변경 이벤트 등 "현재 상태"만 중요한 경우

---

## 9. Spring Boot에서 Kafka 사용하기

### 의존성

```groovy
implementation 'org.springframework.kafka:spring-kafka'
```

### application.yml 설정

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092

    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all           # 안전하게: 모든 복제본에 저장 확인 후 완료
      properties:
        enable.idempotence: true             # 중복 전송 방지
        spring.json.add.type.headers: false  # 수신측에서 클래스 이슈 방지

    consumer:
      group-id: notification-service
      auto-offset-reset: earliest            # 새 Consumer는 처음부터 읽기
      enable-auto-commit: false              # 수동 커밋 사용 (안전)
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.studyolle.study.kafka

    listener:
      ack-mode: manual_immediate
```

### Producer 코드

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyKafkaProducer {

    private static final String TOPIC = "study-events";
    private final KafkaTemplate<String, StudyEventDto> kafkaTemplate;

    public void sendStudyEvent(StudyEventDto event) {
        // studyPath를 Key로 → 같은 스터디는 같은 Partition → 순서 보장
        kafkaTemplate.send(TOPIC, event.getStudyPath(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[Kafka] 전송 성공: partition={}, offset={}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("[Kafka] 전송 실패: {}", ex.getMessage());
                    }
                });
    }
}
```

### Consumer 코드

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class StudyEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "study-events",
        groupId = "notification-service"
    )
    public void consume(
            StudyEventDto event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[Kafka] 수신: partition={}, offset={}, type={}",
                partition, offset, event.getEventType());
        try {
            notificationService.createStudyNotification(event);
            ack.acknowledge();   // 처리 완료 → Offset 커밋
        } catch (Exception e) {
            log.error("처리 실패 — 재시작 시 이 메시지부터 다시 읽음", e);
            // ack 호출 안하면 재전달됨
        }
    }
}
```

---

## 10. StudyOlle에서의 Kafka 활용

### 전체 흐름

```
study-service                            notification-service
     |                                          |
     | 스터디 공개/모집 발생                    | Kafka 메시지 수신
     |                                          | → PostgreSQL에 알림 저장
     +── Kafka ───────────────────────────────→ |
         Topic    : study-events                @KafkaListener
         Key      : studyPath                   groupId: notification-service
         Partition: studyPath 해시로 결정
```

### 발행 이벤트 종류

| eventType | 발생 시점 |
|-----------|----------|
| `STUDY_CREATED` | 스터디 생성 시 |
| `STUDY_PUBLISHED` | 스터디 공개 시 |
| `RECRUITING_STARTED` | 모집 시작 시 |
| `RECRUITING_STOPPED` | 모집 종료 시 |

### Consumer에서 처리

```java
@KafkaListener(topics = "study-events", groupId = "notification-service")
public void consume(StudyEventDto event, Acknowledgment ack) {
    String message = switch (event.getEventType()) {
        case "STUDY_PUBLISHED"    -> "[" + event.getStudyTitle() + "] 스터디가 공개됐습니다.";
        case "STUDY_CREATED"      -> "[" + event.getStudyTitle() + "] 스터디가 생성됐습니다.";
        case "RECRUITING_STARTED" -> "[" + event.getStudyTitle() + "] 모집을 시작했습니다.";
        case "RECRUITING_STOPPED" -> "[" + event.getStudyTitle() + "] 모집이 종료됐습니다.";
        default -> null;
    };

    if (message != null) {
        notificationService.createNotification(
            event.getTriggeredByAccountId(),
            message,
            "/study/" + event.getStudyPath()
        );
    }
    ack.acknowledge();
}
```

---

## 11. 자주 하는 실수 & 주의사항

| ❌ 실수 | 🔧 해결 |
|--------|--------|
| `enable-auto-commit: true` (기본값) | `false` + 수동 `ack.acknowledge()` 사용 |
| Consumer 수 > Partition 수 | Partition 수 = Consumer 수로 맞추기 |
| Key 없이 순서 보장 기대 | 순서가 필요하면 studyPath 등을 Key로 사용 |
| `auto-offset-reset: latest` | `earliest`로 설정 → 처음부터 읽기 |

### 올바른 상수 관리

```java
public class KafkaConstants {
    public static final String TOPIC_STUDY_EVENTS = "study-events";
    public static final String GROUP_NOTIFICATION  = "notification-service";

    public static final String EVENT_STUDY_CREATED    = "STUDY_CREATED";
    public static final String EVENT_STUDY_PUBLISHED  = "STUDY_PUBLISHED";
    public static final String EVENT_RECRUITING_START = "RECRUITING_STARTED";
    public static final String EVENT_RECRUITING_STOP  = "RECRUITING_STOPPED";
}
```

---

## 핵심 용어 한줄 요약

| 용어 | 설명 |
|------|------|
| Producer | 메시지 만들어서 Kafka에 보내는 서비스 |
| Topic | 메시지의 카테고리/주제 (예: "study-events") |
| Partition | Topic을 나눈 병렬 처리 단위 (많을수록 빠름) |
| Offset | Partition 안에서 메시지의 순번 (책갈피) |
| Broker | Kafka 서버 하나 |
| Consumer | 메시지 읽어서 처리하는 서비스 |
| Consumer Group | 같은 Topic을 나눠 읽는 Consumer 팀 |
| Replication | 데이터를 여러 Broker에 복사 (장애 대비) |
| Leader | 실제 읽기/쓰기 담당 Partition 복제본 |
| Follower | Leader 데이터를 복제해두는 백업 |
| Retention | 메시지 보존 기간 (기본 7일) |
| Log Compaction | 같은 Key의 최신 값만 남기는 정리 방식 |
| KRaft | ZooKeeper 없이 Kafka 자체로 클러스터 관리 (4.0~) |
| Acknowledgment | 처리 완료 후 Offset 커밋 신호 |

---

*이전: [RabbitMQ_Guide.md](./RabbitMQ_Guide.md)*
