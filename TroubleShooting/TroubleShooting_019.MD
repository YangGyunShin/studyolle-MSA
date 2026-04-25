# TroubleShooting 019 — Java 람다식에서 "변수는 final 또는 유사 final이어야 합니다" 에러

발생일: 2026-04-24
관련 서비스: study-service
관련 파일: `services/study-service/src/main/java/com/studyolle/study/controller/StudyInternalController.java`
증상: `StudyInternalController.getStudyPageData` 메서드에서 람다식 `id -> toMemberInfo(id, accountMap)` 부분에 빨간 밑줄이 그어지고, 컴파일러가 "람다 식에 사용되는 변수는 final 또는 유사 final이어야 합니다" 라는 에러를 표시함.

---

## 증상

IntelliJ 가 다음과 같은 에러 메시지를 띄운다.

```
람다 식에 사용되는 변수는 final 또는 유사 final이어야 합니다
'accountMap'을(를) 실질적 final 임시 변수로 복사
```

문제가 된 코드는 account-service 의 batch 엔드포인트를 호출해서 멤버 정보를 가져온 뒤, 그 결과를 담는 `accountMap` 변수를 람다식 안에서 사용하는 부분이다.

```java
// 문제가 되는 코드
Map<Long, AccountSummaryDto> accountMap;
if (allMemberIds.isEmpty()) {
    accountMap = Collections.emptyMap();
} else {
    try {
        accountMap = accountFeignClient.getAccountsBatch(...);
    } catch (FeignException e) {
        accountMap = Collections.emptyMap();
    }
}

List<StudyPageDataResponse.MemberInfo> managers = study.getManagerIds()
        .stream()
        .map(id -> toMemberInfo(id, accountMap))  // ← 여기서 에러
        .collect(Collectors.toList());
```

빌드를 시도하면 컴파일 자체가 실패한다.

---

## 이 에러는 대체 무슨 말인가 — 용어부터 풀어보기

에러 메시지가 이해하기 어려운 이유는 "final" 과 "유사 final(effectively final)" 이라는 Java 특유의 용어 때문이다. 이 두 개념을 먼저 풀어야 한다.

### "final" 이란 무엇인가

Java 에서 변수 앞에 `final` 이라는 키워드를 붙이면, 그 변수는 **한 번 값이 정해지면 절대로 바꿀 수 없는 변수**가 된다. 수학의 상수와 비슷하다. `final int PI = 3;` 이라고 써두면 그 뒤로 `PI = 4;` 같은 재할당은 컴파일러가 거부한다.

예를 들어:

```java
final int count = 5;
count = 10;  // ❌ 컴파일 에러: "cannot assign a value to final variable"
```

### "유사 final (effectively final)" 이란 무엇인가

Java 8 부터 도입된 개념으로, **`final` 키워드가 붙어있지 않지만, 실제로 한 번 할당된 뒤 다시는 값이 바뀌지 않는 변수**를 의미한다. 번역이 조금 어색한데, 영어 원문 "effectively final" 을 그대로 해석하면 "사실상 final" 에 가깝다.

예를 들어 다음 두 변수는 의미적으로 동일하다.

```java
final int a = 5;       // 명시적으로 final 선언
// vs.
int b = 5;             // final 키워드는 없지만
// ...그 뒤로 b 에 아무것도 할당하지 않으면 "유사 final" 이 된다
```

반면 아래 코드의 `c` 는 유사 final 이 아니다. 두 번 할당되었기 때문이다.

```java
int c = 5;
c = 10;  // 재할당이 있으므로 유사 final 이 아님
```

---

## 람다식은 왜 이걸 요구하는가

이제 핵심이다. 람다식(`id -> toMemberInfo(id, accountMap)` 같은 코드) 은 자기 바깥에 있는 변수를 읽어서 쓸 수 있다. 위 람다 안에서 `accountMap` 은 그 람다 바깥의 메서드에 선언된 변수이지만 람다 내부에서 참조된다. 이렇게 람다가 바깥 변수를 빌려다 쓰는 것을 **변수 캡처(variable capture)** 라고 부른다.

그런데 Java 의 람다는 단순히 지금 당장 실행되는 코드가 아니다. 람다는 **나중에 호출될 수 있는 함수 객체**로 저장될 수 있다. `stream().map(람다)` 처럼 바로 실행되는 경우도 있지만, 람다는 변수에 담아서 한참 뒤에 실행시키는 것도 가능하다. 비동기 작업, 이벤트 리스너, 다른 스레드에서 실행되는 작업 등등 언제 실행될지 알 수 없는 상황이 많다.

만약 람다가 바깥 변수 `accountMap` 을 캡처했는데, 람다가 실제로 실행되기 전에 바깥에서 `accountMap` 의 값이 바뀌어버린다면 어떻게 될까? 람다는 "내가 만들어졌을 때의 그 값" 을 써야 할까, 아니면 "내가 실행되는 지금의 값" 을 써야 할까?

이 두 가지 모두 혼란을 일으킨다. 특히 멀티스레드 환경에서는 여러 스레드가 같은 변수를 동시에 바꿀 수 있어서 동기화 문제까지 겹친다. 이런 모호함을 원천적으로 막기 위해 Java 설계자들은 간단한 규칙을 세웠다. **"람다가 캡처할 수 있는 바깥 변수는 final 이거나 유사 final 이어야 한다."** 즉, 람다가 쓰는 변수는 절대로 재할당되지 않음이 보장되어야 한다는 뜻이다.

---

## 그래서 내 코드의 어디가 문제였는가

다시 문제의 코드를 보자.

```java
Map<Long, AccountSummaryDto> accountMap;          // ① 선언만 되고 아직 값이 없음
if (allMemberIds.isEmpty()) {
    accountMap = Collections.emptyMap();          // ② 분기 A 에서 할당
} else {
    try {
        accountMap = accountFeignClient.get...;    // ③ 분기 B-정상 에서 할당
    } catch (FeignException e) {
        accountMap = Collections.emptyMap();       // ④ 분기 B-예외 에서 할당
    }
}

// 이후 람다에서 accountMap 참조
.map(id -> toMemberInfo(id, accountMap))          // ⑤ 여기서 에러
```

사람의 관점에서는 `accountMap` 에 값이 **정확히 한 번** 할당된다. if 가 참이면 ②번에서, 거짓이면 ③번이나 ④번 중 하나에서. 어느 경로로 가든 최종적으로는 한 번의 할당으로 값이 정해진다. 그러니 "유사 final" 이라고 봐도 되지 않을까?

문제는 **Java 컴파일러가 이걸 실행 시점이 아니라 컴파일 시점에 판단한다**는 것이다. 컴파일러는 코드를 실행해보지 않는다. 단지 소스 코드를 읽고 문법 규칙에 따라 판단한다. 컴파일러의 눈에 위 코드는 이렇게 보인다.

> "이 `accountMap` 이라는 변수에 값을 할당하는 문장이 소스 코드상 세 군데(②, ③, ④) 있다. 세 군데 모두에서 `accountMap = ...` 이라는 할당이 가능하다. 그러므로 이 변수는 재할당 가능성이 있다. 따라서 유사 final 이 아니다."

컴파일러는 if-else 나 try-catch 의 흐름을 완벽히 추론할 만큼 똑똑하지 않다. 물론 간단한 패턴 정도는 알 수 있지만, 자바 언어 명세(JLS) 는 이런 흐름 분석을 엄격하게 제한하고 있다. 그래서 "할당문이 두 개 이상 보이면 유사 final 로 인정하지 않는다" 는 단순한 규칙을 적용한다.

쉽게 비유하자면, 은행 창구 직원이 양식에 서명 칸이 여러 개 있는 서류를 받았을 때 "어느 칸에 서명이 들어갔는지" 를 일일이 따지지 않고 "서명 칸이 여러 개네, 뭔가 복잡한 서류니까 반려" 하는 것과 비슷하다. 실제로는 한 칸에만 서명하면 끝나는 서류일 수 있지만, 창구의 단순한 규칙이 그렇다면 어쩔 수 없다.

---

## 해결 방법 1 — IDE 퀵픽스 (편하지만 권장하지 않음)

IntelliJ 가 제안하는 **"'accountMap' 을 실질적 final 임시 변수로 복사"** 를 누르면 다음처럼 코드가 바뀐다.

```java
Map<Long, AccountSummaryDto> accountMap;
if (allMemberIds.isEmpty()) {
    accountMap = Collections.emptyMap();
} else {
    try {
        accountMap = accountFeignClient.getAccountsBatch(...);
    } catch (FeignException e) {
        accountMap = Collections.emptyMap();
    }
}

Map<Long, AccountSummaryDto> accountMap1 = accountMap;  // ← 자동 생성된 복사본

List<StudyPageDataResponse.MemberInfo> managers = study.getManagerIds()
        .stream()
        .map(id -> toMemberInfo(id, accountMap1))       // accountMap1 로 바뀜
        .collect(Collectors.toList());
```

원리는 간단하다. `accountMap1` 이라는 새 변수를 하나 만들어서 거기에 `accountMap` 의 값을 한 번만 복사해 넣는다. 그러면 `accountMap1` 은 오직 이 한 줄에서만 할당되므로 유사 final 의 자격을 얻는다. 람다에서는 이 `accountMap1` 을 쓰면 된다.

동작은 하지만 거의 같은 이름의 변수가 두 개 생겨서 코드가 지저분해진다. `accountMap` 과 `accountMap1` 이 어떻게 다른지 나중에 이 코드를 읽는 사람은 한 번 생각해야 한다. 설계적으로 깔끔하지 않다.

---

## 해결 방법 2 — 메서드로 분리 (권장)

근본적인 해결은 "왜 이 변수가 유사 final 이 아닌가?" 라는 질문에서 출발한다. 답은 **값을 결정하는 과정이 여러 분기로 흩어져 있기 때문** 이다. 그러면 그 분기 로직을 **별도의 메서드로 뽑아내서** 값이 한 번의 반환으로 결정되도록 만들면 된다.

### 리팩토링 전과 후

**리팩토링 전 — 한 메서드에 모든 로직이 인라인**

```java
public ResponseEntity<StudyPageDataResponse> getStudyPageData(...) {
    // ... (스터디 조회, 권한 플래그 계산)

    Set<Long> allMemberIds = new HashSet<>();
    allMemberIds.addAll(study.getManagerIds());
    allMemberIds.addAll(study.getMemberIds());

    Map<Long, AccountSummaryDto> accountMap;           // 문제의 발단
    if (allMemberIds.isEmpty()) {
        accountMap = Collections.emptyMap();
    } else {
        try {
            accountMap = accountFeignClient.getAccountsBatch(
                    AccountBatchRequest.of(allMemberIds),
                    "study-service"
            );
        } catch (FeignException e) {
            accountMap = Collections.emptyMap();
        }
    }

    List<StudyPageDataResponse.MemberInfo> managers = study.getManagerIds()
            .stream()
            .map(id -> toMemberInfo(id, accountMap))    // ❌ 컴파일 에러
            .collect(Collectors.toList());
    // ...
}
```

**리팩토링 후 — 값을 결정하는 로직을 private 메서드로 분리**

```java
public ResponseEntity<StudyPageDataResponse> getStudyPageData(...) {
    // ... (스터디 조회, 권한 플래그 계산)

    Set<Long> allMemberIds = new HashSet<>();
    allMemberIds.addAll(study.getManagerIds());
    allMemberIds.addAll(study.getMemberIds());

    Map<Long, AccountSummaryDto> accountMap = fetchAccountMap(allMemberIds);
    // ↑ 한 번의 할당으로 값이 정해진다 → 자동으로 유사 final 이 됨

    List<StudyPageDataResponse.MemberInfo> managers = study.getManagerIds()
            .stream()
            .map(id -> toMemberInfo(id, accountMap))    // ✅ 에러 없음
            .collect(Collectors.toList());
    // ...
}

/**
 * 멤버 id 집합을 받아 account-service 로부터 nickname/profileImage/bio 를 한 번에 조회한다.
 * 빈 입력이거나 원격 호출 실패 시 빈 Map 을 반환한다.
 */
private Map<Long, AccountSummaryDto> fetchAccountMap(Set<Long> accountIds) {
    if (accountIds.isEmpty()) {
        return Collections.emptyMap();
    }
    try {
        return accountFeignClient.getAccountsBatch(
                AccountBatchRequest.of(accountIds),
                "study-service"
        );
    } catch (FeignException e) {
        return Collections.emptyMap();
    }
}
```

### 왜 이렇게 바꾸니까 에러가 사라지는가

핵심은 `Map<Long, AccountSummaryDto> accountMap = fetchAccountMap(allMemberIds);` 이 한 줄이다. 이 줄에서는 **할당이 정확히 한 번** 일어난다. 분기도 없고 try-catch 도 없다. 컴파일러의 눈에 이 변수는 명백히 "할당 한 번, 재할당 없음" 이므로 자동으로 유사 final 로 인정된다.

분기 로직은 모두 `fetchAccountMap` 이라는 별도 메서드 안으로 옮겨졌다. 그 메서드 안에서는 `return` 문이 여러 개 있을 수 있지만, 호출부의 `accountMap` 은 그 여러 `return` 중 하나의 결과값을 **한 번** 받아올 뿐이다.

### 이 리팩토링이 주는 또 다른 이점

에러 해결이 주 목적이긴 하지만, 이렇게 메서드를 분리하면 코드의 질 자체가 좋아진다. 몇 가지 부가 효과가 있다.

첫째, `getStudyPageData` 메서드가 읽기 쉬워진다. 원래 이 메서드는 "스터디 페이지 데이터를 집계한다" 라는 하나의 임무를 수행해야 하는데, 기존 코드에는 "계정 정보를 원격으로 가져오고 예외 처리를 한다" 라는 세부 작업이 인라인으로 섞여 있었다. 메서드를 분리하면 호출부는 `fetchAccountMap(allMemberIds)` 이라는 의도만 보여주고, 세부 구현은 이름 뒤로 숨는다. 이것을 **추상화 계층을 나눈다** 고 부른다.

둘째, 나중에 확장하기 쉬워진다. 만약 account-service 호출에 **재시도 로직** 이나 **서킷 브레이커** (예: Resilience4j) 를 넣고 싶다면, `fetchAccountMap` 메서드 안쪽만 건드리면 된다. `getStudyPageData` 는 "어떻게 가져오든 나는 Map 만 받으면 된다" 라는 인터페이스 관점에서 영향받지 않는다.

셋째, 테스트하기 쉬워진다 (이 프로젝트에서는 아직 단위 테스트를 쓰진 않지만). fetch 로직만 따로 떼어서 "빈 id 를 넣으면 빈 Map 이 나오는가?" "Feign 예외가 날 때 빈 Map 이 나오는가?" 같은 질문을 각각 검증할 수 있다.

---

## 해결 방법 3 — 삼항연산자 (간단한 경우 한정)

값을 결정하는 로직이 아주 단순하다면 삼항연산자 한 줄로도 해결할 수 있다.

```java
Map<Long, AccountSummaryDto> accountMap =
        allMemberIds.isEmpty() ? Collections.emptyMap() : accountFeignClient.getAccountsBatch(...);
```

이 문장도 할당이 한 번뿐이므로 유사 final 이 된다. 다만 이번 경우처럼 try-catch 가 필요한 상황에서는 삼항연산자로 깔끔하게 쓰기 어렵다. 삼항연산자는 "값 두 개 중 하나를 고른다" 까지는 잘 어울리지만 예외 처리가 섞이면 가독성이 나빠지기 때문에, try-catch 가 필요한 상황에서는 해결 방법 2(메서드 분리) 가 낫다.

---

## 왜 `final` 을 붙이는 해결책은 안 되는가

"그럼 처음부터 `final Map<Long, AccountSummaryDto> accountMap;` 이라고 선언하면 되지 않나?" 라는 아이디어가 자연스럽게 떠오를 수 있다. 그러나 이 방법은 문제를 더 크게 만든다.

```java
final Map<Long, AccountSummaryDto> accountMap;
if (allMemberIds.isEmpty()) {
    accountMap = Collections.emptyMap();     // ← 첫 번째 할당은 OK
} else {
    try {
        accountMap = accountFeignClient.getAccountsBatch(...);   // ← OK (실행 경로 상 첫 할당)
    } catch (FeignException e) {
        accountMap = Collections.emptyMap();  // ❌ 에러: 이미 할당된 final 변수에 재할당
    }
}
```

`final` 을 붙이면 컴파일러가 "이 변수에는 할당이 **정확히 한 번만** 있어야 한다" 라고 더 엄격히 요구하게 된다. 실행 경로상으로는 한 번의 할당만 일어나지만, try 블록의 할당과 catch 블록의 할당이 모두 "할당 시도" 이므로 컴파일러가 "final 변수를 여러 번 할당하려 한다" 고 에러를 낸다. 결국 문제는 더 악화된다.

Java 의 `final` 지역 변수는 **단 한 번의 할당 구문** 만 허용한다는 점을 기억해두면 유용하다.

---

## 같은 패턴을 다른 곳에서 만났을 때 — 진단 체크리스트

이 에러는 자바 개발을 하다 보면 꽤 자주 마주치는 편이다. 다음 체크리스트를 따라가면 대부분 해결된다.

첫째, 에러가 난 람다식이 참조하는 바깥 변수가 무엇인지 확인한다. IntelliJ 의 에러 메시지가 변수 이름을 정확히 알려준다(예: `'accountMap'`).

둘째, 그 변수가 소스 코드에서 몇 번 할당되는지 세어본다. `변수명 =` 이라는 할당 문을 전부 찾는다. 재할당이 두 번 이상이면 유사 final 이 아니다.

셋째, 재할당이 왜 일어나는지 본다. 대부분의 경우 세 가지 패턴 중 하나다.

하나는 **if-else 또는 switch 분기에서 각각 할당** 되는 경우다. 이번에 마주친 사례가 정확히 이 경우다. 해결은 삼항연산자로 한 줄로 합치거나, 메서드로 추출하면 된다.

또 하나는 **try-catch 안에서 각각 할당** 되는 경우다. 예외 처리가 있으면 삼항연산자로는 어렵고 메서드 추출이 가장 깔끔하다.

마지막 패턴은 **루프 안에서 변수가 갱신** 되는 경우다. 예를 들어 `int sum = 0;` 으로 선언하고 `for` 안에서 `sum += ...;` 로 누적하는 경우다. 이건 설계상 자연스러운 상태 변경이므로 메서드 추출이 아니라 `reduce`, `sum`, `collect` 같은 스트림 연산으로 바꾸는 것이 보통의 해결법이다. 또는 아예 루프 전체를 별도 메서드로 빼서 합계를 반환받는 구조로 만들 수도 있다.

---

## 한 줄 요약

람다식은 바깥 변수를 캡처하는데, 그 변수가 나중에 바뀌면 혼란스러워지니 **"할당은 단 한 번만"** 이라는 규칙을 강제한다. 소스 코드에 할당문이 여러 군데 보이면 컴파일러는 설령 실행 시점에 한 번만 할당되더라도 거부한다. 가장 깔끔한 해결은 **값을 결정하는 분기 로직을 별도 메서드로 추출** 해서, 호출부에서는 한 줄의 할당(`var = helper(...)`) 으로 받게 만드는 것이다.

---

## 📎 관련 파일

- `services/study-service/src/main/java/com/studyolle/study/controller/StudyInternalController.java` — `getStudyPageData` 와 신규 `fetchAccountMap` private 헬퍼

## 📚 참고 개념

- Java Language Specification 4.12.4 — final variables & effectively final
- Java Language Specification 15.27.2 — Lambda body (capture rules)
- 리팩토링 패턴: Extract Method (메서드 추출)
