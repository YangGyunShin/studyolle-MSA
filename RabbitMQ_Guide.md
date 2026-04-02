# RabbitMQ — 쉽게 이해하는 완벽 가이드

> 작성일: 2026-04-02 | 프로젝트: StudyOlle MSA Phase 5

---

## 0. RabbitMQ가 왜 필요한가?

먼저 문제 상황을 상상해보자.

### 문제: 서비스 직접 호출의 한계

```
주문서비스 ──── 직접 호출 (HTTP) ────→ 결제서비스
```

| 상황 | 결과 |
|------|------|
| 결제서비스가 갑자기 죽으면? | 주문서비스도 같이 실패 (에러 터짐) |
| 결제서비스가 응답을 줄 때까지? | 주문서비스가 멈추고 기다림 (느려짐) |
| 결제 요청이 갑자기 10만 건 몰리면? | 결제서비스가 버티지 못하고 뻗음 (과부하) |

### 해결책: 중간에 우체통을 놓자

```
주문서비스 ──→ [📮 우체통] ──→ 결제서비스
           편지 넣고 끝     여유될 때 꺼내서 처리
```

이 **우체통** 역할을 하는 소프트웨어가 바로 **RabbitMQ**다!

- 결제서비스가 죽어도? → 우체통이 편지를 보관
- 결제서비스가 느려도? → 주문서비스는 넣고 바로 다음 일
- 요청이 몰려도? → 우체통이 쌓아두고 천천히 처리

---

## 1. RabbitMQ의 4가지 핵심 등장인물

| RabbitMQ | 우편 시스템 | 설명 |
|----------|------------|------|
| **Producer** | 편지 보내는 사람 | 메시지를 만들어서 보내는 서비스 |
| **Exchange** | 우체국 | 어느 우체통으로 보낼지 결정하는 라우터 |
| **Queue** | 우체통 | 메시지가 쌓여서 대기하는 곳 |
| **Consumer** | 편지 받는 사람 | 메시지를 꺼내서 처리하는 서비스 |

### 메시지 흐름

```
Producer → Exchange → Queue → Consumer
(보내는사람)  (우체국)  (우체통)  (받는사람)
```

### 실제 코드

```java
// Producer: 메시지 보내기
rabbitTemplate.convertAndSend(
    "order.exchange",   // 어느 우체국으로?
    "order.created",    // 어떤 우체통으로? (Routing Key)
    orderEvent          // 보낼 내용
);

// Consumer: 메시지 받기
@RabbitListener(queues = "order.queue")
public void handleOrder(OrderEvent event) {
    // 메시지가 오면 이 메서드가 자동으로 실행됨
}
```

---

## 2. Exchange (우체국) — 핵심 중의 핵심

Exchange는 메시지를 받아서 **"이 메시지는 어느 Queue로 보낼까?"** 를 결정한다.  
이때 결정 기준이 되는 것이 **Routing Key** (우편번호 같은 것)다.

### Exchange 4가지 유형

#### ① Direct Exchange — 정확히 일치해야 전달

Routing Key가 **정확히 같은** Queue로만 보낸다.

```
Producer → "order.created" → [Direct Exchange]
                                   ↓
                        [order.created Queue] ✅ 도착
                        [order.deleted Queue] ❌ 안 도착
```

> **언제 쓰나:** 특정 서비스 하나에만 메시지를 전달할 때

---

#### ② Topic Exchange — 패턴으로 매칭 ⭐ (가장 많이 씀!)

와일드카드 패턴으로 여러 Queue에 유연하게 보낼 수 있다.

| 패턴 | 의미 |
|------|------|
| `*` | 단어 정확히 하나 |
| `#` | 단어 0개 이상 |

```
라우팅 키: "order.created" 로 보낼 때

"order.*"       → ✅ 매칭 (order 뒤에 단어 하나)
"order.#"       → ✅ 매칭 (order 뒤에 뭐든)
"order.created" → ✅ 매칭 (정확히 일치)
"#"             → ✅ 매칭 (모든 것)
"payment.*"     → ❌ 불일치
```

**실제 활용 예 (StudyOlle):**
```
enrollment.accepted  → 재고 서비스, 이메일 서비스 둘 다 받아야 함
enrollment.rejected  → 이메일 서비스만 받으면 됨

notification Queue 바인딩: "enrollment.#"  (모든 enrollment 이벤트)
```

> **언제 쓰나:** StudyOlle notification-service처럼 이벤트 종류별로 다른 서비스에 라우팅할 때

---

#### ③ Fanout Exchange — 모든 Queue에 뿌리기

Routing Key를 **완전히 무시**하고 연결된 모든 Queue에 동시에 보낸다.

```
Producer → [Fanout Exchange]
                ↓↓↓
       [이메일 Queue] ✅
       [SMS Queue]    ✅
       [푸시 Queue]   ✅
```

> **언제 쓰나:** 전체 공지, 실시간 브로드캐스트

---

#### ④ Headers Exchange — 헤더 값으로 매칭

Routing Key 대신 메시지 헤더 속성으로 라우팅한다.  
실무에서는 거의 안 쓰인다. Topic Exchange가 대부분의 경우를 커버한다.

---

### Spring Boot Exchange/Queue/Binding 구성

```java
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange");
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable("order.queue")
            .withArgument("x-dead-letter-exchange", "dlq.exchange") // 실패 메시지는 DLX로
            .withArgument("x-message-ttl", 86400000)                // TTL 24시간
            .build();
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderQueue)
            .to(orderExchange).with("order.created");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter(); // 자동 JSON 직렬화
    }
}
```

---

## 3. Queue (우체통) — 메시지가 쌓이는 곳

Queue는 단순하다. 메시지가 들어오면 줄 세워서 보관하고, Consumer가 꺼내가면 처리하는 **FIFO(선입선출)** 자료구조다.

### 중요한 Queue 설정

| 설정 | 설명 |
|------|------|
| `durable = true` | 서버 재시작해도 Queue 정의 유지 (**거의 항상 true**) |
| `x-message-ttl` | 메시지 만료 시간 (ms 단위) |
| `x-dead-letter-exchange` | 실패 메시지를 보낼 DLX 지정 |

```java
@Bean
public Queue orderQueue() {
    return QueueBuilder.durable("order.queue")       // 서버 재시작에도 유지
        .withArgument("x-message-ttl", 86400000)     // 24시간 TTL
        .withArgument("x-dead-letter-exchange", "dlq.exchange")
        .build();
}
```

---

## 4. Acknowledgement (ACK) — "잘 받았습니다" 확인 응답

택배를 받을 때 서명하는 것과 같다.  
Consumer가 메시지를 처리한 후 RabbitMQ에게 **"처리 완료!"** 신호를 보낸다.

### ACK를 왜 해야 하나?

```
ACK 안 했을 때 → 서버 죽으면 RabbitMQ가 "아직 처리 안 됐구나" → 다시 전달 ✅
ACK 했을 때   → 서버 죽으면 RabbitMQ가 "처리 됐구나" → 메시지 삭제 → 유실! ❌
```

### ACK 종류

| 명령 | 의미 |
|------|------|
| `basicAck` | 잘 처리했어, 메시지 지워줘 |
| `basicNack (requeue=true)` | 처리 실패, 다시 Queue 맨 앞으로 돌려줘 (재시도) |
| `basicNack (requeue=false)` | 포기, DLQ로 보내줘 |

```java
@RabbitListener(queues = "order.queue")
public void handleOrder(
        OrderEvent event,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
        Channel channel) throws IOException {
    try {
        processOrder(event);
        channel.basicAck(deliveryTag, false);          // 성공 → ACK
    } catch (BusinessException e) {
        channel.basicNack(deliveryTag, false, false);  // 포기 → DLQ
    } catch (Exception e) {
        channel.basicNack(deliveryTag, false, true);   // 재시도
    }
}
```

---

## 5. Dead Letter Queue (DLQ) — 실패 메시지의 안전망

처리에 계속 실패하는 메시지를 그냥 버리면 안 된다.  
DLQ는 **"문제가 생긴 메시지들을 모아두는 별도 Queue"** 다.

### 메시지가 DLQ로 가는 3가지 상황

1. Consumer가 `basicNack(requeue=false)` 호출 → 처리 포기
2. 메시지 TTL 만료 → 오래됐는데 아무도 안 읽음
3. Queue 최대 길이 초과 → Queue가 꽉 참

### DLQ 흐름

```
정상: [order.queue] → Consumer 처리 성공 → 메시지 삭제
실패: [order.queue] → Consumer 처리 실패 → [dlq.queue] → 나중에 수동 확인
```

```java
// 원본 Queue에 DLQ 연결
@Bean
public Queue orderQueue() {
    return QueueBuilder.durable("order.queue")
        .withArgument("x-dead-letter-exchange", "dlq.exchange")
        .withArgument("x-dead-letter-routing-key", "order.failed")
        .build();
}

// DLQ 자체
@Bean
public Queue deadLetterQueue() {
    return QueueBuilder.durable("order.dlq").build();
}
```

---

## 6. 메시지 유실 방지 3단 콤보

> 셋 중 하나라도 빠지면 메시지가 유실될 수 있다.

| 설정 | 역할 |
|------|------|
| **Durable Queue** | 서버 재시작해도 Queue 정의가 살아있음 |
| **Persistent Message** | 메시지를 디스크에 저장 (`delivery_mode=2`) |
| **Publisher Confirm** | 브로커가 "저장 완료!" 확인 후 Producer에게 알림 |

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    publisher-confirm-type: correlated  # Publisher Confirm 활성화
    publisher-returns: true             # 라우팅 실패 메시지 반환
```

---

## 7. StudyOlle에서의 RabbitMQ 활용

### 전체 흐름

```
event-service                          notification-service
     |                                        |
     | 모임 신청/수락/거절 발생                | 알림 저장 + API 제공
     |                                        |
     +── RabbitMQ ──────────────────────────→ +
         Exchange: event.notification          @RabbitListener
         Queue   : enrollment.queue            (queues = "enrollment.queue")
         Routing : enrollment.#
```

### event-service: 메시지 보내기

```java
// RabbitMQ 구성
@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "event.notification";
    public static final String QUEUE    = "enrollment.queue";
    public static final String ROUTING  = "enrollment.#";

    @Bean public TopicExchange exchange() { return new TopicExchange(EXCHANGE); }
    @Bean public Queue queue() { return QueueBuilder.durable(QUEUE).build(); }
    @Bean public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING);
    }
}

// 모임 신청 수락 시 메시지 발행
public void sendAccepted(Long accountId, String eventTitle) {
    EnrollmentEventDto dto = EnrollmentEventDto.builder()
        .eventType("enrollment.accepted")
        .enrollmentAccountId(accountId)
        .eventTitle(eventTitle)
        .occurredAt(LocalDateTime.now())
        .build();

    rabbitTemplate.convertAndSend(
        "event.notification",   // Exchange
        "enrollment.accepted",  // Routing Key → enrollment.# 에 매칭
        dto
    );
}
```

### notification-service: 메시지 받기

```java
@RabbitListener(queues = "enrollment.queue")
public void consume(EnrollmentEventDto event) {
    notificationService.createNotification(
        event.getEnrollmentAccountId(),
        event.getEventTitle() + " 모임 신청이 수락되었습니다.",
        "/events/" + event.getEventId()
    );
}
```

---

## 8. 자주 하는 실수 & 주의사항

| ❌ 실수 | 🔧 해결 |
|--------|--------|
| ACK를 항상 성공으로 처리 | 예외 처리 후 NACK 호출 필수 |
| Queue를 non-durable로 생성 | 프로덕션에서는 반드시 `durable=true` |
| DLQ 없이 운영 | 반드시 DLQ 설정 후 운영 |
| Exchange/Queue 이름 오타 | `static final String` 상수로 이름 관리 |

```java
// 올바른 상수 관리
public class RabbitMQConstants {
    public static final String EXCHANGE       = "event.notification";
    public static final String QUEUE          = "enrollment.queue";
    public static final String ROUTING_ACCEPT = "enrollment.accepted";
    public static final String ROUTING_REJECT = "enrollment.rejected";
}
```

---

## 핵심 용어 한줄 요약

| 용어 | 설명 |
|------|------|
| Producer | 메시지 보내는 서비스 |
| Exchange | 어느 Queue로 보낼지 결정하는 라우터 (우체국) |
| Routing Key | Exchange가 Queue를 선택하는 기준 (우편번호) |
| Binding | Exchange와 Queue를 연결하는 규칙 |
| Queue | 메시지가 쌓여 대기하는 곳 (우체통) |
| Consumer | 메시지 꺼내서 처리하는 서비스 |
| ACK | "처리 완료!" 확인 응답 (택배 서명) |
| NACK | "처리 실패!" 응답 (택배 반송) |
| DLQ | 실패한 메시지 모아두는 특수 Queue |
| durable | 서버 재시작에도 살아남는 Queue/Exchange |
| TTL | 메시지 만료 시간 |

---

*다음: [Kafka_Guide.md](./Kafka_Guide.md)*
