# 🔧 트러블슈팅: Lazy Loading + PostgreSQL LOB 에러 (managers.size() in 템플릿)

## 📌 상황 요약

검색 페이지(`/search/study`)에서 스터디를 검색하면, 검색 결과 대신
**500 에러 페이지**가 표시되었다.

검색 결과 카드에 **멤버 수를 "관리자 + 멤버" 합산으로 표시**하기 위해
fragments.html의 study-list fragment에서 `study.managers.size() + study.memberCount`
표현식을 추가한 직후부터 발생했다.

---

## 📋 에러 로그 전문

### 에러 ①: Thymeleaf 템플릿 파싱 실패

```
org.thymeleaf.exceptions.TemplateInputException: 
    An error happened during template parsing 
    (template: "class path resource [templates/search.html]")
```

### 에러 ②: SpEL 표현식 평가 실패

```
Caused by: org.thymeleaf.exceptions.TemplateProcessingException: 
    Exception evaluating SpringEL expression: 
    "study.managers.size() + study.memberCount" 
    (template: "fragments.html" - line 650, col 35)
```

### 에러 ③: PostgreSQL LOB 접근 에러

```
Caused by: org.postgresql.util.PSQLException: 
    Large Objects may not be used in auto-commit mode.
```

---

## 🔍 디버깅 과정

### STEP 0: 에러 로그가 수백 줄이어도 당황하지 마라

에러 로그를 처음 보면 `at org.xxx...`로 시작하는 줄이 200줄이 넘어서 압도당할 수 있다.

하지만 **전부 읽을 필요 없다.** 핵심은 딱 하나:

> **`Caused by`라는 키워드만 찾아라.**

`Caused by`는 에러의 **인과관계**를 보여준다.
우리 에러에는 `Caused by`가 **3개** 있었다.
이 3개만 찾으면 된다.

```
❶ (맨 위)     org.thymeleaf.exceptions.TemplateInputException: ...
❷ (중간)      Caused by: org.thymeleaf.exceptions.TemplateProcessingException: ...
❸ (맨 아래)   Caused by: org.postgresql.util.PSQLException: ...
```

---

### STEP 1: 맨 아래 `Caused by`부터 읽어라 — 진짜 원인(Root Cause)

에러 로그에서 **가장 아래에 있는 `Caused by`가 진짜 원인**이다.
위에 있는 에러들은 전부 이 원인으로 인해 연쇄적으로 발생한 결과일 뿐이다.

```
❸ Caused by: org.postgresql.util.PSQLException: 
       Large Objects may not be used in auto-commit mode.
```

#### 📖 이 한 줄에서 읽을 수 있는 정보:

| 부분 | 의미 |
|------|------|
| `org.postgresql` | PostgreSQL 데이터베이스에서 발생한 에러 |
| `PSQLException` | PostgreSQL 전용 예외 클래스 |
| `Large Objects` | LOB(Large Object) 데이터 — 큰 텍스트(CLOB)나 바이너리(BLOB) |
| `may not be used in auto-commit mode` | 트랜잭션 없이(auto-commit 상태에서) 접근할 수 없다 |

#### ✅ 이 시점에서 알 수 있는 것:

> "DB 레벨의 문제다. 트랜잭션이 없는 상태에서 LOB 데이터를 읽으려 했구나."

#### ❓ 아직 모르는 것:

> "뭘 읽으려 했는데? 왜 트랜잭션이 없었지?"

→ 이걸 알려면 위의 `Caused by`를 봐야 한다.

---

### STEP 2: 중간 `Caused by`를 읽어라 — 어디서, 뭘 하다가 터졌나?

```
❷ Caused by: org.thymeleaf.exceptions.TemplateProcessingException: 
       Exception evaluating SpringEL expression: 
       "study.managers.size() + study.memberCount" 
       (template: "fragments.html" - line 650, col 35)
```

#### 📖 이 한 줄에서 읽을 수 있는 정보:

| 부분 | 의미 |
|------|------|
| `org.thymeleaf` | Thymeleaf 템플릿 엔진에서 발생 |
| `Exception evaluating SpringEL expression` | SpEL(Spring Expression Language) 표현식을 평가하다가 실패 |
| `"study.managers.size() + study.memberCount"` | 이 표현식이 문제 |
| `template: "fragments.html"` | fragments.html 파일에서 발생 |
| `line 650, col 35` | 정확히 650번째 줄, 35번째 칸 |

#### ✅ 이 시점에서 알 수 있는 것:

> "fragments.html 650번째 줄에서 `study.managers.size() + study.memberCount` 표현식을 실행하다가 터졌구나."

#### ❓ 아직 모르는 것:

> "이 표현식에서 `managers.size()`가 문제인지, `memberCount`가 문제인지?"

이 한 줄만으로는 **둘 중 뭐가 문제인지 구분이 안 된다.**
이걸 알려면 `Caused by` 사이에 있는 **스택 트레이스**를 봐야 한다.

---

### STEP 3: `Caused by` 사이의 스택 트레이스를 읽어라 — 핵심 단서 찾기

❷와 ❸ 사이에 `at org.xxx...`로 시작하는 줄이 수십 줄 있다.
대부분은 프레임워크 내부 코드라 건너뛰어도 되지만,
**클래스명과 메서드명**을 훑어보면 핵심 단서가 보인다:

```
at org.hibernate.collection.spi.PersistentSet.size()                      ← 단서 ①
at org.hibernate.collection.spi.AbstractPersistentCollection.initialize()  ← 단서 ②
at org.hibernate.loader.ast.internal.CollectionLoaderSingleKey.load()      ← 단서 ③
```

하나씩 해석해보자.

---

#### 📌 단서 ①: `PersistentSet.size()` — 누가 터졌는지 특정하기

에러가 난 표현식을 다시 보자:

```
study.managers.size()  +  study.memberCount
```

여기서 `.size()`를 호출하는 부분이 2개인지 확인해보자:

- `managers.size()` → managers는 `Set<Account>` 타입 → `.size()` 호출 **가능**
- `memberCount` → memberCount는 `int` 타입 → `.size()` 호출 **불가능** (기본 자료형이라 메서드가 없음)

이 표현식에서 `.size()`를 호출하는 건 **`managers` 하나뿐**이다.

그리고 스택 트레이스에 찍힌 `PersistentSet`은 뭘까?
Hibernate는 엔티티의 `Set` 컬렉션을 관리할 때, 일반 `HashSet`이 아닌
`PersistentSet`이라는 특수한 클래스로 감싼다.

```java
// Study.java에서의 선언
private int memberCount;                      // int → PersistentSet과 무관
private Set<Account> managers = new HashSet<>();  // Set → Hibernate가 PersistentSet으로 감쌈
```

따라서:

> ✅ **`PersistentSet.size()` = `managers.size()` 호출에서 터진 것이다!**

만약 표현식이 `study.managers.size() + study.members.size()`처럼
`.size()`를 호출하는 `Set`이 2개였다면 추가 분석이 필요했겠지만,
이번엔 `Set` 타입이 `managers` 하나뿐이라 바로 특정할 수 있다.

---

#### 📌 단서 ②: `AbstractPersistentCollection.initialize()` — 왜 추가 로딩이 필요했나?

`initialize()`는 **"아직 로딩되지 않은 컬렉션을 지금 초기화하겠다"**는 뜻이다.

여기서 핵심 질문:

> "만약 managers가 이미 DB에서 가져와진 상태였다면?"

**`initialize()`가 호출될 이유가 없다.** 이미 메모리에 데이터가 있으니까.

`initialize()`가 호출됐다는 건:

> ✅ **`managers`가 아직 안 가져와진 상태(Lazy)였다는 증거!**

JPA에서 `@ManyToMany` 같은 연관관계는 기본적으로 **LAZY** 로딩이다.
즉, "필요할 때 가져온다"가 기본 전략이다.

```java
// Study.java
@ManyToMany
private Set<Account> managers = new HashSet<>();
// @ManyToMany의 기본 fetch 전략 = FetchType.LAZY
// → 쿼리에서 명시적으로 fetchJoin 하지 않으면 Lazy 상태로 남음
```

---

#### 📌 단서 ③: `CollectionLoaderSingleKey.load()` — 무슨 일이 일어났나?

`load()`는 **"실제로 DB에 SQL 쿼리를 날려서 데이터를 가져오는 단계"**이다.

실제로 에러 로그의 SQL 부분을 보면, managers를 가져오려는 추가 쿼리가 찍혀있었다:

```sql
select
    m1_0.study_id,
    m1_1.id,
    m1_1.bio,
    m1_1.email,
    ...
    m1_1.profile_image,   -- ← 이것이 LOB(큰 데이터) 필드!
    ...
from
    study_managers m1_0 
join
    account m1_1 on m1_1.id = m1_0.managers_id 
where
    m1_0.study_id = ?
```

이 쿼리가 Account 엔티티를 가져오면서 `profile_image` 같은 LOB 필드를 읽으려 했고,
그때 PostgreSQL이 "트랜잭션 없이 LOB 못 읽어!" 에러를 던진 것이다.

---

### STEP 4: 맨 위 에러를 읽어라 — 최종 요약

```
❶ org.thymeleaf.exceptions.TemplateInputException: 
       An error happened during template parsing 
       (template: "class path resource [templates/search.html]")
```

이건 **"search.html을 처리하다가 에러 났어요"**라는 최종 결과이다.
원인 파악에는 직접적인 도움이 안 되지만,
**어떤 페이지에서 에러가 났는지** 알려준다.

---

### STEP 5: 모든 단서를 조합하여 원인 추론하기

여기까지 에러 로그에서 알아낸 사실들을 정리하면:

```
[에러 로그에서 알 수 있는 것]

✅ search.html 렌더링 중 에러 발생 (STEP 4)
✅ fragments.html 650줄의 표현식에서 발생 (STEP 2)
✅ managers.size()가 원인 (STEP 3 - 단서 ①)
✅ managers가 Lazy 상태(아직 안 가져옴)였다 (STEP 3 - 단서 ②)
✅ Hibernate가 DB에 추가 쿼리를 날리려 했다 (STEP 3 - 단서 ③)
✅ 트랜잭션이 없는 상태에서 LOB 데이터 접근 실패 (STEP 1)
```

하지만 에러 로그가 알려주지 않는 것이 하나 있다:

> ❓ **"왜 managers가 Lazy 상태였는지?"**

에러 로그는 "managers가 Lazy였다"까지는 알려주지만,
**"왜 Lazy였는지"**는 알려주지 않는다.
이건 **코드를 직접 추적**해서 확인해야 한다.

---

### STEP 6: 코드 추적 — 에러 로그가 알려주지 않는 "왜?"를 찾기

에러 로그에서 얻은 정보를 출발점으로, 코드를 따라간다.

#### 6-1: search.html을 렌더링하는 컨트롤러는?

에러 로그에서 `search.html`이 문제라는 걸 알았으니,
이 페이지를 렌더링하는 컨트롤러를 찾는다:

```java
// MainController.java
@GetMapping("/search/study")
public String searchStudy(String keyword, 
                          @PageableDefault(...) Pageable pageable, 
                          Model model) {
    Page<Study> studyPage = studyRepository.findByKeyword(keyword, pageable);
    model.addAttribute("studyPage", studyPage);
    return "search";  // ← search.html 렌더링
}
```

`return "search"` → 이 메서드가 search.html을 렌더링하는 곳이다.

#### 6-2: Study 데이터를 가져오는 쿼리는?

컨트롤러에서 Study를 가져오는 메서드는 `findByKeyword()`이다.
이 메서드의 구현을 확인한다:

```java
// StudyRepositoryExtensionImpl.java
@Override
public Page<Study> findByKeyword(String keyword, Pageable pageable) {
    QStudy study = QStudy.study;
    JPQLQuery<Study> query = querydsl.applyPagination(pageable,
        queryFactory
            .selectDistinct(study)
            .from(study)
            .where(
                study.published.isTrue()
                    .and(study.title.containsIgnoreCase(keyword)
                        .or(study.tags.any().title.containsIgnoreCase(keyword))
                        .or(study.zones.any().localNameOfCity.containsIgnoreCase(keyword)))
            )
            .leftJoin(study.tags, QTag.tag).fetchJoin()            // ✅ tags 가져옴
            .leftJoin(study.zones, QZone.zone).fetchJoin()         // ✅ zones 가져옴
            .leftJoin(study.members, QAccount.account).fetchJoin() // ✅ members 가져옴
            // ⚠️ managers는 fetchJoin에 없다!
    );
    ...
}
```

**이 코드를 직접 보고 나서야** 원인을 확정할 수 있다:

> ✅ **fetchJoin에 managers가 빠져있어서 Lazy 상태였던 것이다!**

#### 6-3: fetchJoin이 뭐고, 왜 중요한가?

fetchJoin은 "연관된 데이터를 **메인 쿼리와 함께** 한 번에 가져와라"라는 명령이다.

```
fetchJoin 있음 → 메인 쿼리 실행할 때 같이 가져옴 → 메모리에 이미 있음 → 바로 사용 가능
fetchJoin 없음 → 메인 쿼리에서 안 가져옴 → Lazy 상태 → 나중에 접근하면 추가 쿼리 필요
```

이번 경우:

```
tags     → fetchJoin ✅ → 바로 사용 가능
zones    → fetchJoin ✅ → 바로 사용 가능
members  → fetchJoin ✅ → 바로 사용 가능
managers → fetchJoin ❌ → Lazy 상태! → 나중에 접근하면 추가 쿼리 필요
```

---

### STEP 7: 왜 "나중에 접근"하면 에러가 나는가? — 트랜잭션 생명주기

Lazy 로딩이 항상 에러를 내는 것은 아니다.
**트랜잭션 안에서** Lazy 로딩을 하면 정상 동작한다.
문제는 **트랜잭션이 끝난 후에** Lazy 로딩을 시도할 때 발생한다.

Spring의 요청 처리 흐름을 시간순으로 보면:

```
🕐 요청 시작
    │
    ▼
🕐 트랜잭션 시작 (서비스 레이어 진입)
    │
    ├─ findByKeyword() 쿼리 실행 → Study 가져옴 (tags, zones, members 함께)
    │                               (managers는 Lazy 상태로 남음)
    │
🕐 트랜잭션 종료 (서비스 레이어 빠져나옴) ← ⚠️ 여기서 DB 연결이 auto-commit 모드로 전환
    │
    ▼
🕐 Thymeleaf 템플릿 렌더링 시작 (트랜잭션 밖!)
    │
    ├─ study.tags → ✅ 이미 메모리에 있음 → 정상
    ├─ study.zones → ✅ 이미 메모리에 있음 → 정상
    ├─ study.memberCount → ✅ int 필드, 이미 가져옴 → 정상
    ├─ study.managers.size() → ❌ Lazy 상태! → Hibernate가 추가 쿼리 시도
    │                          → Account의 profile_image(LOB) 읽기 시도
    │                          → 트랜잭션 없음 → auto-commit 모드
    │                          → 💥 PostgreSQL: "LOB은 auto-commit에서 못 읽어!"
```

비유하자면:

> 도서관(트랜잭션)이 열려있을 때는 책(데이터)을 자유롭게 빌릴 수 있다.
> 도서관이 닫힌 후(트랜잭션 종료)에 책을 빌리려 하면 거절당한다.
> fetchJoin은 "도서관이 열려있을 때 필요한 책을 미리 다 빌려오는 것"이다.

---

## 📊 전체 에러 흐름 다이어그램

```
사용자가 /search/study?keyword=스프링 검색
    │
    ▼
MainController.searchStudy() 실행
    │
    ▼
studyRepository.findByKeyword("스프링", pageable) 쿼리 실행
    │
    ├─ tags, zones, members → fetchJoin으로 함께 가져옴 ✅
    ├─ managers → fetchJoin 없음 → Lazy 상태 ⚠️
    │
    ▼
트랜잭션 종료 (DB 연결이 auto-commit 모드로 전환)
    │
    ▼
Thymeleaf가 search.html → fragments.html의 study-list fragment 렌더링
    │
    ▼
650줄: ${study.managers.size() + study.memberCount} 표현식 평가
    │
    ├─ managers.size() 호출
    │
    ├─ Hibernate: "managers가 아직 Lazy 상태네? DB에서 가져올게!"
    │
    ├─ 추가 SELECT 쿼리 실행 (study_managers JOIN account)
    │
    ├─ Account의 profile_image(LOB 필드) 읽기 시도
    │
    ├─ PostgreSQL: "auto-commit 모드에서 LOB 접근 불가!" 💥
    │
    ▼
TemplateProcessingException → TemplateInputException → 500 에러
```

---

## 🔍 에러 로그 디버깅 정리 — 읽는 순서 요약

| 순서 | 어디를 보나 | 무엇을 알 수 있나 |
|------|-----------|-----------------|
| 1단계 | 맨 아래 `Caused by` | **진짜 원인** — PostgreSQL LOB 에러 (트랜잭션 없이 큰 데이터 접근) |
| 2단계 | 중간 `Caused by` | **어디서, 뭘 하다가** — fragments.html 650줄, managers.size() 표현식 |
| 3단계 | Caused by 사이 스택 트레이스 | **왜 그런 상황이 됐나** — PersistentSet.size()(managers), initialize()(Lazy), load()(추가 쿼리) |
| 4단계 | 맨 위 에러 | **어떤 페이지에서** — search.html 렌더링 중 |
| 5단계 | 코드 추적 (에러 로그 밖) | **왜 Lazy였는지** — findByKeyword()에서 managers fetchJoin 누락 확인 |

> 💡 **핵심**: 에러 로그는 **"무슨 문제가 일어났는지"**까지 알려준다.
> **"왜 그런 상황이 됐는지"**는 코드를 직접 따라가면서 확인해야 한다.
> 에러 로그는 **출발점**이고, 코드 추적이 **목적지까지 가는 길**이다.

---

## 💊 수정 방법

### 방법 A: fetchJoin에 managers 추가 (추가 쿼리 없이 해결)

```java
// StudyRepositoryExtensionImpl.java
.leftJoin(study.tags, QTag.tag).fetchJoin()
.leftJoin(study.zones, QZone.zone).fetchJoin()
.leftJoin(study.members, QAccount.account).fetchJoin()
.leftJoin(study.managers, /* managers용 QAccount */).fetchJoin()  // ← 추가
```

⚠️ **문제점**: fetchJoin이 많아지면 카테시안 곱(Cartesian Product)이 발생하여
결과 행이 급증하고 성능이 저하될 수 있다.
또한 `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`
경고가 이미 발생하고 있어서, fetchJoin을 더 추가하면 이 문제가 악화된다.

### 방법 B: 템플릿에서 managers.size() 제거 (✅ 채택)

```html
<!-- 변경 전 (에러 발생) -->
<span th:text="${study.managers.size() + study.memberCount}"></span>명

<!-- 변경 후 (에러 해결) -->
<span th:text="${study.memberCount}"></span>명
```

`memberCount`는 Study 테이블에 있는 **int 필드**이다.
이미 메인 쿼리에서 가져온 값이므로 추가 DB 조회가 필요 없다.

### 방법 B의 보완: 백엔드에서 addManager() 시 memberCount 증가

현재 `memberCount`에 관리자가 포함되지 않는 문제를 백엔드에서 해결한다:

```java
// Study.java
public void addManager(Account account) {
    this.managers.add(account);
    this.memberCount++;  // ← 이 한 줄 추가
}
```

이렇게 하면 스터디 생성 시 관리자가 등록될 때 `memberCount`가 1이 되고,
템플릿에서는 `memberCount`만 읽으면 관리자 포함 총원이 정확히 표시된다.

기존 데이터 보정이 필요하다면:

```sql
UPDATE study SET member_count = member_count + (
    SELECT COUNT(*) FROM study_managers WHERE study_managers.study_id = study.id
);
```

---

## ❓ 왜 방법 A 대신 방법 B를 선택했는가?

### 1. 성능 문제 회피

이미 에러 로그의 WARN에 이런 경고가 찍히고 있었다:

```
HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory
```

이 경고는 **"페이징 + fetchJoin을 함께 쓰면 DB가 아닌 메모리에서 페이징한다"**는 뜻이다.
즉, 데이터 전체를 메모리로 가져온 후 Java 코드에서 잘라내는 것이다.
fetchJoin을 더 추가하면 이 문제가 악화된다.

### 2. 설계 원칙: "이미 있는 데이터를 활용하라"

`memberCount`라는 비정규화된 숫자 필드가 이미 존재한다.
이 필드의 값을 정확하게 관리하는 것이, 매번 컬렉션을 로딩하여 `.size()`를 구하는 것보다
훨씬 효율적이고 안전하다.

### 3. 근본적 해결

추가 쿼리를 허용하는 방식(fetchJoin 추가)은 다른 곳에서도
같은 패턴의 문제가 재발할 수 있다.
템플릿에서 Lazy 컬렉션에 접근하지 않는 습관을 들이는 것이 근본적인 해결이다.

---

## 📁 수정된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `fragments.html` | `study.managers.size() + study.memberCount` → `study.memberCount`로 변경 |

### 적용 필요 (백엔드)

| 파일 | 변경 내용 |
|------|-----------|
| `Study.java` | `addManager()` 메서드에 `this.memberCount++` 추가 |
| (DB) | 기존 스터디 데이터 `member_count` 보정 SQL 실행 |

---

## 🎓 이번 트러블슈팅에서 배운 것

### 1. 에러 로그 읽는 법: "Caused by를 아래→위로 읽어라"

```
맨 아래 Caused by  →  "진짜 원인이 뭐야?" (Root Cause)
         ↑
중간 Caused by     →  "어디서 뭘 하다가 터졌어?"
         ↑
맨 위 에러          →  "최종적으로 어떤 결과야?"
```

### 2. 스택 트레이스의 클래스명/메서드명에서 단서를 읽어라

| 스택 트레이스 | 단서 |
|-------------|------|
| `PersistentSet.size()` | Set 타입의 컬렉션에서 size() 호출 |
| `AbstractPersistentCollection.initialize()` | 컬렉션이 Lazy 상태였다 |
| `CollectionLoaderSingleKey.load()` | DB에 추가 쿼리를 시도했다 |

### 3. 에러 로그는 출발점이지, 답이 아니다

에러 로그는 **"무슨 문제가 일어났는지"**까지 알려준다.
**"왜 그런 상황이 됐는지"**는 코드를 직접 추적해서 확인해야 한다.

### 4. 템플릿에서 Lazy 컬렉션에 접근하지 마라

Thymeleaf 템플릿은 트랜잭션이 끝난 후에 렌더링된다.
이 시점에서 Lazy 상태인 컬렉션에 접근하면:
- H2 등: `LazyInitializationException` 발생
- PostgreSQL: `Large Objects may not be used in auto-commit mode` 발생

해결 원칙:
- 템플릿에서 필요한 데이터는 **쿼리 시점에 fetchJoin으로 가져오거나**
- **비정규화된 필드(memberCount 등)를 활용**하라

### 5. PostgreSQL LOB의 특수성

PostgreSQL은 LOB(Large Object) 데이터를 **반드시 트랜잭션 안에서만** 읽을 수 있다.
H2나 MySQL에서는 발생하지 않을 수 있는 에러가 PostgreSQL에서 발생하는 이유이다.
Account 엔티티의 `profileImage`(`@Lob` 또는 TEXT 타입)가 이에 해당한다.

---

## 🔗 관련 트러블슈팅

- **TroubleShooting_001**: ExceptionAdvice 파라미터 주입 실패 + MainController null 에러
  → 예외 처리 코드에서 발생하는 에러와 연쇄 에러 패턴
- **TroubleShooting_003**: 로그인 후 status 999 에러
  → /error 경로가 차단되어 에러 페이지 자체가 표시되지 않는 문제
  → 이번 에러도 /error가 차단되어 있었다면 status 999로 나타났을 수 있음
