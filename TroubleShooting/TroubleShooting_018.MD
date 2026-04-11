# TroubleShooting 018 — 모임 수정 시 eventType(선착순/관리자확인) 변경 불가

발생일: 2026-04-11
관련 서비스: event-service
증상: 모임 수정 페이지에서 선착순(FCFS) ↔ 관리자확인(CONFIRMATIVE) 을
      전환하고 저장해도 서버 DB 의 event_type 필드가 그대로 유지됨.

---

## 증상

1. 관리자가 기존 모임(예: FCFS) 수정 페이지 진입
2. "관리자 확인" 라벨 클릭 → UI 상 강조 표시 정상 동작
3. "수정 완료" 버튼 클릭
4. 서버 응답 200 OK, 모임 상세 페이지로 정상 리다이렉트
5. **[버그]** 모임 상세 페이지에서 여전히 "선착순" 으로 표시됨
6. DB 직접 확인: `SELECT event_type FROM event WHERE id = ?;` → 여전히 `FCFS`

---

## 디버깅 — 어느 레이어에서 끊기는가

이 문제는 4개 레이어가 관여한다. 하나씩 확인해 원인을 좁혀갔다.

### ① 프론트엔드 (notifications.html 아닌 event/form.html)

```javascript
var data = {
    title: document.getElementById('title').value.trim(),
    description: $('#description').summernote('code'),
    eventType: document.querySelector('input[name="eventType"]:checked')?.value || 'FCFS',
    limitOfEnrollments: parseInt(document.getElementById('limitOfEnrollments').value, 10),
    endEnrollmentDateTime: document.getElementById('endEnrollmentDateTime').value,
    startDateTime: document.getElementById('startDateTime').value,
    endDateTime: document.getElementById('endDateTime').value
};
```

`eventType` 이 JSON 에 포함되어 전송되고 있음. **프론트 정상.**

브라우저 Network 탭의 Request Payload 를 확인하면:
```json
{
  "title": "teetetete",
  "eventType": "CONFIRMATIVE",   ← 정상적으로 담겨 있음
  "limitOfEnrollments": 3,
  ...
}
```

### ② API Gateway 라우팅

`/api/studies/{path}/events/{id}` → `event-service` 로 정상 라우팅되고 있음.
200 OK 가 돌아온다는 것은 이 단계는 문제없음. **정상.**

### ③ event-service 의 UpdateEventRequest DTO

```java
@Data
public class UpdateEventRequest {
    @NotBlank
    private String title;

    private String description;

    @Min(2)
    private int limitOfEnrollments;

    @NotNull
    private LocalDateTime endEnrollmentDateTime;

    @NotNull
    private LocalDateTime startDateTime;

    @NotNull
    private LocalDateTime endDateTime;

    // ❌ eventType 필드 자체가 없음!
}
```

**원인 발견.** Jackson 은 JSON 의 `"eventType": "CONFIRMATIVE"` 필드를 역직렬화하려
하지만, DTO 에 해당하는 setter/필드가 없으면 **기본적으로 조용히 무시**한다.
(Jackson 의 `FAIL_ON_UNKNOWN_PROPERTIES` 기본값은 true 이지만 Spring Boot 의
기본 ObjectMapper 설정은 false 로 완화되어 있다.)

즉:
- 프론트가 `eventType` 을 보냄
- Jackson 이 JSON 을 UpdateEventRequest 로 변환
- `eventType` 필드는 DTO 에 없으므로 **그냥 버려짐**
- UpdateEventRequest 에는 eventType 정보가 존재하지 않음

### ④ Event.update() 도메인 메서드

```java
public void update(UpdateEventRequest req) {
    this.title = req.getTitle();
    this.description = req.getDescription();
    this.limitOfEnrollments = req.getLimitOfEnrollments();
    this.endEnrollmentDateTime = req.getEndEnrollmentDateTime();
    this.startDateTime = req.getStartDateTime();
    this.endDateTime = req.getEndDateTime();
    // ❌ this.eventType 업데이트 없음
}
```

설사 DTO 에 eventType 이 있었다 해도, 도메인 메서드에서 필드를 업데이트하지 않으면
변경되지 않는다. **동일 원인의 두 번째 누락.**

---

## 원인 요약

**UpdateEventRequest DTO 와 Event.update() 도메인 메서드 양쪽에서 eventType 처리가 누락**되어 있었다.

프론트엔드와 Jackson 역직렬화 과정에서는 아무런 에러도 나지 않았기 때문에
(Jackson 이 알 수 없는 필드를 조용히 무시) 버그를 찾기 어려웠다.
**"에러 없이 저장은 되는데, 특정 필드만 갱신되지 않는"** 조용한 버그의 전형이다.

---

## 해결

### 1. UpdateEventRequest — eventType 필드 추가

```java
package com.studyolle.event.dto.request;

import com.studyolle.event.entity.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateEventRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private EventType eventType;   // ← 추가

    @Min(2)
    private int limitOfEnrollments;

    @NotNull
    private LocalDateTime endEnrollmentDateTime;

    @NotNull
    private LocalDateTime startDateTime;

    @NotNull
    private LocalDateTime endDateTime;
}
```

### 2. Event.update() — eventType 반영

```java
public void update(UpdateEventRequest req) {
    this.title = req.getTitle();
    this.description = req.getDescription();
    this.eventType = req.getEventType();    // ← 추가
    this.limitOfEnrollments = req.getLimitOfEnrollments();
    this.endEnrollmentDateTime = req.getEndEnrollmentDateTime();
    this.startDateTime = req.getStartDateTime();
    this.endDateTime = req.getEndDateTime();
}
```

### 3. 데이터 정합성 — 기존 updateEvent() 로직이 이미 안전

`EventService.updateEvent()` 는 이미 `event.acceptWaitingList()` 를 호출하고 있어서,
CONFIRMATIVE → FCFS 로 변경 시 대기 중인 신청자를 자동으로 승인 처리한다.

```java
public void updateEvent(Long eventId, Long accountId, UpdateEventRequest req) {
    Event event = getEventWithEnrollments(eventId);
    validateManager(event, accountId);
    event.update(req);             // 도메인 메서드로 위임
    event.acceptWaitingList();     // 정원 증가 or 타입 변경 시 대기자 자동 승인
}
```

타입 변경 시나리오별 동작:

| 변경 방향 | 기존 승인자 | 대기자 처리 |
|---------|-----------|-----------|
| CONFIRMATIVE → FCFS | 그대로 승인 유지 | `acceptWaitingList()` 가 자동 승인 처리 ✅ |
| FCFS → CONFIRMATIVE | 그대로 승인 유지 | 기존 대기자는 수동 처리 필요 (정상) |

---

## 학습 포인트

### 1. Jackson 의 "조용한" 알 수 없는 필드 처리

Spring Boot 의 기본 ObjectMapper 설정은 `FAIL_ON_UNKNOWN_PROPERTIES = false` 다.
이 설정은 JSON 에 DTO 에 없는 필드가 있어도 예외를 던지지 않고 무시한다.

**장점**
- API 의 전후방 호환성을 유지하기 쉽다 (새 필드 추가 시 기존 클라이언트가 깨지지 않음)
- 부분 업데이트 패턴에서 유용하다

**단점**
- 오타(`evetnType`) 나 누락된 필드가 조용히 무시되어 디버깅이 어렵다
- API 명세와 실제 구현이 일치하지 않는 문제가 숨겨진다

### 2. DTO 와 도메인 메서드는 항상 쌍으로 확인

필드 하나가 제대로 반영되려면:

```
프론트 JSON 필드
    ↓
DTO 필드 (Jackson 역직렬화)
    ↓
도메인 메서드 (update)
    ↓
JPA dirty checking 또는 save()
    ↓
DB UPDATE
```

이 체인 중 어느 한 고리라도 빠지면 필드는 반영되지 않는다.
새 필드를 추가하거나 기존 필드 업데이트 기능을 만들 때는 **DTO 와 도메인 메서드를
반드시 함께 확인**해야 한다.

### 3. 이런 버그를 방지하는 테스트 패턴

모든 필드를 다른 값으로 바꿔서 PUT/POST 한 뒤, 응답 또는 DB 에서 실제로 반영됐는지
각 필드별로 검증하는 통합 테스트를 작성하면 이런 누락을 사전에 잡을 수 있다.

```java
// 예시 (작성되지 않음 — 학습용 패턴 설명)
@Test
void updateEvent_allFieldsShouldBeReflected() {
    // given: 기존 모임 FCFS, 제목 "old"
    Event event = createTestEvent(EventType.FCFS, "old");

    // when: 모든 필드를 다른 값으로 업데이트
    UpdateEventRequest req = new UpdateEventRequest();
    req.setTitle("new");
    req.setEventType(EventType.CONFIRMATIVE);
    // ... 모든 필드 세팅

    eventService.updateEvent(event.getId(), accountId, req);

    // then: DB 에서 모든 필드가 바뀌었는지 확인
    Event updated = eventRepository.findById(event.getId()).get();
    assertThat(updated.getTitle()).isEqualTo("new");
    assertThat(updated.getEventType()).isEqualTo(EventType.CONFIRMATIVE);  // ← 이 검증이 누락 버그를 잡는다
}
```

### 4. 디버깅 순서: 프론트 → 네트워크 → DTO → 도메인 → DB

조용한 버그는 "어디서 끊기는지" 를 단계별로 좁혀야 한다:

1. **프론트**: DevTools Network 탭에서 Request Payload 확인 (필드가 실제로 나갔는지)
2. **네트워크**: 200 응답이 돌아왔는지, 에러는 없는지
3. **DTO**: 서버 디버거로 DTO 의 필드 값 확인 (Jackson 이 잘 담았는지)
4. **도메인/Service**: 엔티티의 setter 가 호출되는지
5. **DB**: SQL 로직 확인 (JPA SQL 로그, 실제 테이블 값)

이번 사례는 1단계에서 정상, 5단계에서 비정상 → 3~4단계 사이에서 문제 발견.
각 단계를 건너뛰지 않고 순차적으로 확인하는 습관이 조용한 버그 디버깅의 핵심이다.

---

## 최종 확인 결과

```bash
# 수정 전
event_type
----------
FCFS

# 수정 후 (관리자 확인으로 변경)
event_type
----------
CONFIRMATIVE
```

모임 수정 페이지에서 선착순 ↔ 관리자 확인 전환이 정상 반영됨을 확인.
