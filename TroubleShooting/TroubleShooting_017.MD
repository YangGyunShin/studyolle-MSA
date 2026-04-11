# TroubleShooting 017 — 알림 읽음 처리 시 nav 배지 차감 불발 (fetch 비동기 race condition)

발생일: 2026-04-11
관련 서비스: frontend-service (notifications.html), notification-service
증상: 알림 페이지에서 알림 카드를 클릭해 링크로 이동해도 nav 바의 🔔 배지 숫자가
      그대로 유지됨. 새로고침하거나 다른 페이지로 이동해도 숫자가 줄지 않음.

---

## 증상 재현 시나리오

1. 사용자가 로그인 → 홈 화면 nav 바에 🔔 **2** 배지 표시
2. `/notifications` 페이지 진입 → 알림 카드 2개 표시 (STUDY_CREATED, STUDY_PUBLISHED)
3. 첫 번째 알림 카드 클릭 → 해당 스터디 페이지로 이동
4. 홈 또는 다른 페이지로 돌아옴
5. **[버그]** nav 배지가 여전히 🔔 **2** 로 표시됨 (1 로 줄어야 함)

PostgreSQL 과 Redis 상태를 확인하면 해당 알림의 `checked` 필드는 여전히 `false`,
Redis 카운터 `notification:unread:{accountId}` 도 여전히 2 로 남아있었다.
즉 **PATCH 요청 자체가 서버에 도달하지 못했다.**

---

## 문제가 된 원래 코드

`notifications.html` 의 알림 카드 클릭 핸들러:

```javascript
function handleNotificationClick(id, link) {
    // 백그라운드로 읽음 처리 호출 (응답 안 기다림)
    fetch(API_BASE + '/api/notifications/' + id, {
        method: 'PATCH',
        headers: {
            'Authorization': 'Bearer ' + (localStorage.getItem('accessToken') || '')
        }
    }).catch(function (e) {
        console.warn('읽음 처리 실패:', e);
    });

    // 즉시 링크 이동
    window.location.href = link;
}
```

설계 의도: "PATCH 요청은 백그라운드로 쏴두고 사용자는 즉시 다음 페이지로 이동"
→ 사용자 경험(UX) 관점에서는 네트워크 왕복 시간을 기다리지 않아 빠르다는 장점이 있음.

---

## 원인 — 브라우저의 navigation 이 in-flight request 를 취소한다

### 자바스크립트의 비동기 이벤트 루프 동작 순서

`fetch()` 는 즉시 반환하지 않는다. 정확히는 **Promise 를 즉시 반환**하지만,
실제 HTTP 요청이 서버로 전송되는 작업은 브라우저의 내부 네트워크 스택으로 위임된다.

이 코드는 두 작업을 동시에 시작한다:

```
시간축 →

  [0ms]   fetch() 호출
            ├─ Promise 객체 즉시 반환
            └─ 브라우저 네트워크 스택에 "HTTP PATCH 요청 큐잉"
                                        ↓
                                  "DNS 조회 → TCP 연결 → HTTP 전송"
                                  (수십~수백 ms 소요)

  [1ms]   window.location.href = link 실행
            └─ 브라우저: "새 페이지로 이동하라는 명령"
                         ↓
                     현재 페이지 언로드 시작
                         ↓
                     **현재 페이지에서 시작된 모든 in-flight 요청 취소**
```

### 브라우저의 page navigation 정책

페이지가 언로드되면 브라우저는 **그 페이지에서 시작된 모든 진행 중인 네트워크 요청을 취소**한다.
이는 W3C 표준 동작이다. 이유:

1. 언로드된 페이지의 JavaScript 는 더 이상 존재하지 않으므로, 요청의 응답을 받아도 처리할 수 없다.
2. 응답 콜백이 실행되려면 사라진 페이지의 메모리 컨텍스트가 필요한데, 이미 해제되었다.
3. 리소스 낭비 방지 (사용자가 빠르게 페이지를 전환할 때마다 고아 요청이 쌓이는 것을 막음)

즉 우리 코드에서는:
```
fetch() 큐잉 → window.location.href 실행 → 언로드 시작 → fetch 취소됨
```

fetch 요청이 TCP 핸드셰이크조차 완료하기 전에 취소되어, **PATCH 요청은 실제로 서버에
도달하지 못한다**. 서버 로그에도 해당 요청은 찍히지 않는다.

### 왜 "가끔은 될 수도" 있는가

이 버그는 100% 재현되지 않을 수 있다. 몇 가지 경우에는 우연히 성공한다:

- 네트워크가 매우 빠른 로컬 환경에서 **언로드 시작 전에 TCP 패킷이 이미 날아간 경우**
  (이미 전송된 패킷은 취소되지 않음)
- 브라우저가 keep-alive 연결을 재사용해 TCP 핸드셰이크를 건너뛴 경우
- 사용자의 클릭 타이밍과 링크 이동 사이에 브라우저가 렌더링 프레임을 한 번 끼워넣은 경우

테스트할 때마다 결과가 달라질 수 있어 "읽음이 간헐적으로만 동작한다"는 오묘한 증상으로
보이기도 한다. 이게 race condition 버그의 전형적인 특성이다.

### 유사 사례: `navigator.sendBeacon` 이 존재하는 이유

이 문제는 흔해서 W3C 는 아예 **전용 API** 를 만들었다:

```javascript
navigator.sendBeacon(url, data)
```

`sendBeacon` 은 "페이지 언로드 중에도 살아남는 POST 요청" 이다.
주로 분석(analytics) 이벤트 전송, 세션 종료 로그 등에 쓰인다.
브라우저가 페이지 언로드 과정에서 이 요청만은 취소하지 않고 끝까지 보낸다.

단, sendBeacon 은 POST 만 지원하고 커스텀 헤더를 넣기 어려워
(Content-Type 이 제한적) Authorization: Bearer 같은 인증 헤더가 필요한 우리 상황에는 부적합하다.

---

## 해결 — await 로 완료 대기

```javascript
async function handleNotificationClick(id, link, isAlreadyRead) {
    // 이미 읽은 알림은 서버 호출 없이 바로 이동 (불필요한 요청 방지)
    if (isAlreadyRead) {
        window.location.href = link;
        return;
    }

    try {
        await fetch(API_BASE + '/api/notifications/' + id, {
            method: 'PATCH',
            headers: {
                'Authorization': 'Bearer ' + (localStorage.getItem('accessToken') || '')
            }
        });
    } catch (e) {
        console.warn('읽음 처리 실패:', e);
        // 실패해도 링크 이동은 진행 (사용자 경험 우선)
    }
    window.location.href = link;
}
```

### 핵심 변경 사항

1. 함수 앞에 `async` 추가 → 내부에서 `await` 를 사용할 수 있도록
2. `fetch()` 앞에 `await` 추가 → **HTTP 응답이 도착할 때까지 대기**
3. `window.location.href` 는 `await` 가 끝난 뒤에 실행 → 페이지 언로드는 요청이 완료된 후
4. try/catch 로 감싸서 네트워크 에러가 나도 링크 이동은 진행되도록 처리
5. `isAlreadyRead` 파라미터 추가 → 이미 읽은 알림은 PATCH 생략 (불필요한 요청 방지)

### await 가 어떻게 문제를 해결하는가

```
시간축 →

  [0ms]    fetch() 호출 → Promise 반환
             └─ 브라우저: HTTP PATCH 요청 시작

  [0ms]    await 로 인해 함수 실행 일시 정지
             (window.location.href 는 아직 실행되지 않음!)

  [~30ms]  서버가 PATCH 처리 완료 → 200 응답 반환

  [30ms]   Promise resolve → await 통과 → 함수 재개

  [30ms]   window.location.href = link 실행 → 페이지 이동
```

이제 PATCH 요청이 확실히 완료된 후에 페이지 이동이 일어나므로,
서버의 DB/Redis 상태가 먼저 갱신되고, 다음 페이지 렌더링 시 nav 배지도 정확한 숫자를 반영한다.

### 성능 영향

- await 로 인해 클릭 → 이동 사이에 네트워크 왕복 시간(약 20~50ms)이 추가된다.
- 사용자 관점에서는 거의 체감되지 않는 지연이다 (보통 인간의 클릭 반응은 100ms 이상).
- 안정성과 일관성을 얻는 대가로 충분히 감수할 만한 지연이다.

---

## 부수적 변경 — 읽음 처리 후 알림이 사라지는 문제

이 버그를 수정하면서 또 다른 UX 이슈가 드러났다:

### 기존 동작
- `GET /api/notifications` 가 **읽지 않은 알림만** 반환 (`getUnreadNotifications()`)
- 알림을 읽으면 목록에서 즉시 사라짐
- 사용자가 "방금 뭘 읽었는지" 확인할 방법 없음

### 변경 동작
- `GET /api/notifications` 가 **전체 알림**을 반환 (`getAllNotifications()`)
- 읽은 알림은 흐릿하게 (opacity 0.6) 표시
- 안 읽은 알림은 진한 색 + 왼쪽 세로 선 + 우측 상단 파란 점 + 굵은 글씨로 강조
- "읽음" 텍스트 라벨로 상태 명시

### 변경이 필요했던 파일

**notification-service (Java):**
- `NotificationRepository.findAllByAccountIdOrderByCreatedAtDesc()` 메서드 추가
- `NotificationService.getAllNotifications()` 메서드 추가
- `NotificationController.getNotifications()` 에서 호출 메서드 교체

**frontend-service (HTML/JS):**
- `notifications.html` 의 `createNotificationCard()` 에서 `n.checked` 값에 따라
  시각 스타일 분기
- "전체 읽음" 버튼은 안 읽은 알림이 1건 이상 있을 때만 활성화되도록
  `Array.some()` 으로 체크

---

## 학습 포인트

### 1. Fire-and-forget 패턴의 위험성

"요청 쏘고 응답 안 기다리고 다음 작업" 패턴은 코드상 간결해 보이지만,
**네트워크 요청이 실제로 완료된다는 보장이 없다**. 특히 페이지 이동, 창 닫기,
컴포넌트 언마운트 등 컨텍스트가 사라지는 순간과 겹치면 요청이 취소된다.

이 패턴을 정말로 쓰고 싶다면:
- `navigator.sendBeacon` 을 사용 (단, POST 에 Content-Type 제약 있음)
- Service Worker 에 메시지를 보내 Service Worker 가 백그라운드로 요청을 보내도록 함
- `fetch` 에 `keepalive: true` 옵션 추가 (비교적 최근 기능, POST 용도, 64KB 크기 제한)

우리 사례는 단순한 알림 읽음 처리이고, 수십 ms 지연은 UX 에 무해하므로
**await 로 대기하는 것이 가장 간단하고 확실한 해법**이었다.

### 2. 비동기 코드에서 실행 순서를 오해하지 말 것

자바스크립트의 `fetch()` 는 **"실행 시작"** 을 의미하지 **"완료"** 를 의미하지 않는다.
Promise 는 "미래에 완료될 작업의 핸들" 일 뿐이다.
이어지는 코드는 완료를 기다리지 않고 즉시 실행된다.

race condition 을 피하려면:
- 결과에 의존하는 후속 작업은 반드시 `await` 또는 `.then()` 안에 배치
- 여러 비동기 작업을 동시에 시작하되 모두 끝나길 기다려야 한다면 `Promise.all()`
- 순서가 중요하면 `await` 를 순차적으로 배치

### 3. 서버 상태와 UI 상태 동기화 검증

이 버그를 빠르게 잡을 수 있었던 이유는 **서버 상태를 직접 확인할 수 있었기 때문**이다:

```bash
# Redis 카운터 확인
docker exec -it redis redis-cli GET notification:unread:452

# PostgreSQL 상태 확인
docker exec -it notification-db psql -U studyolle -d notification_db \
  -c "SELECT id, checked FROM notifications WHERE account_id = 452;"
```

UI 에서는 "읽음 처리가 안 된다" 라고 보이지만, 서버 상태를 확인해보면
`checked` 가 그대로 `false` 였다. 이 사실 하나로 **"클라이언트의 PATCH 요청이 서버에
도달하지 못하고 있다"** 는 결론으로 바로 이어졌다. 브라우저 Network 탭에서
PATCH 요청이 "Canceled" 상태로 찍히는 것도 같은 증거였다.

클라이언트 측 증상과 서버 측 상태를 양쪽에서 확인하는 습관이 race condition 같은
미묘한 버그 디버깅에 필수다.

### 4. 브라우저 개발자도구 Network 탭의 중요성

fetch 요청이 **cancelled** 상태로 찍히면 페이지 언로드로 인한 취소가 강하게 의심된다.
이 상태를 놓치면 "서버 로그에 요청이 없다" 만 보고 라우팅 설정 문제, 인증 문제 등
엉뚱한 곳을 뒤지게 된다.

개발자도구에서 확인할 것:
- **Status**: cancelled / failed / pending 인지
- **Time**: 요청 시작 후 취소되기까지 얼마나 걸렸는지
- **Initiator**: 어느 JS 코드에서 요청이 시작되었는지
- **Preserve log** 체크박스: 페이지 이동 후에도 이전 요청 기록 유지

### 5. async/await 는 UX 지연의 대가로 정확성을 산다

30~50ms 의 지연이 "느려진 것처럼 느껴지지 않느냐" 고 걱정할 수 있지만, 인간 인지 한계상
100ms 이내의 지연은 대부분 체감되지 않는다 (이것이 UI 설계에서 말하는 "즉각적 반응" 기준).

안정성 vs 미세한 속도 향상이 trade-off 일 때, 대부분의 경우 안정성이 옳다.
특히 "동작하기도 하고 안 하기도 하는" 비결정적 동작은 사용자 신뢰를 가장 빠르게 무너뜨린다.

---

## 최종 동작 확인

수정 후 테스트 결과:

```
[클릭 전] Redis: GET notification:unread:452 → "2"
          PostgreSQL: id=1 checked=f, id=2 checked=f
          nav 배지: 2

[첫 번째 알림 클릭 → /study/test 이동]
          Network 탭: PATCH /api/notifications/1 → 200 OK (정상 완료)

[돌아와서 확인]
          Redis: GET notification:unread:452 → "1"
          PostgreSQL: id=1 checked=t, id=2 checked=f
          nav 배지: 1
          /notifications 페이지: id=1 카드는 흐릿하게 표시, id=2 는 진한 색 유지
```

클릭 → 읽음 처리 → Redis/DB 갱신 → 다음 페이지 렌더링 시 정확한 배지 숫자 반영
전체 흐름이 정상 동작함을 확인.
