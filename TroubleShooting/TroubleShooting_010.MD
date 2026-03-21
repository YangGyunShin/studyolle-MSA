# 🔧 트러블슈팅: build.gradle annotationProcessor 누락으로 인한 Lombok 컴파일 오류

## 📌 상황 요약

frontend-service를 빌드하려 했더니 컴파일 단계에서 오류가 발생했다.

---

## 📋 에러 메시지

```
error: variable studyInternalClient not initialized in the default constructor
    private final StudyInternalClient studyInternalClient;
                                      ^
```

`StudyPageController.java`에서 `@RequiredArgsConstructor`가 붙어 있는데
`final` 필드가 생성자에서 초기화되지 않는다고 컴파일 오류가 난 것이다.

---

## 🔍 원인 분석

### @RequiredArgsConstructor는 무엇인가?

Lombok의 `@RequiredArgsConstructor`는 `final` 필드를 파라미터로 받는 생성자를 자동으로 만들어준다.

예를 들어 아래 코드가 있으면:

```java
@RequiredArgsConstructor
public class StudyPageController {
    private final StudyInternalClient studyInternalClient;
    private final AccountInternalClient accountInternalClient;
}
```

Lombok이 자동으로 이런 생성자를 만들어준다:

```java
public StudyPageController(StudyInternalClient studyInternalClient,
                           AccountInternalClient accountInternalClient) {
    this.studyInternalClient = studyInternalClient;
    this.accountInternalClient = accountInternalClient;
}
```

Lombok이 이 코드를 만들어주지 않으면 `final` 필드가 초기화되지 않아 컴파일 오류가 난다.

---

### 왜 Lombok이 동작하지 않았는가?

`frontend-service/build.gradle`을 보면:

```groovy
# ❌ 수정 전
dependencies {
    implementation 'org.projectlombok:lombok'   ← 이것만 있음
    // annotationProcessor가 없음
}
```

Lombok이 `@RequiredArgsConstructor` 같은 어노테이션을 처리해서 실제 코드를 생성하려면
두 가지가 모두 필요하다.

| 설정 | 역할 |
|------|------|
| `implementation` | 런타임에 Lombok 라이브러리를 사용할 수 있게 한다 |
| `annotationProcessor` | 컴파일 시 어노테이션을 처리해서 코드를 생성한다 |

`annotationProcessor`가 없으면 `@RequiredArgsConstructor`를 읽기는 하지만
실제 생성자 코드를 만들어주지 않는다.
결과적으로 `final` 필드만 있고 생성자가 없으니 Java 컴파일러가 오류를 낸다.

---

## ✅ 해결 방법

`build.gradle`에 `annotationProcessor`를 추가한다.

```groovy
# ✅ 수정 후
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'   ← 이 줄 추가

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

추가 후 IntelliJ에서 Gradle을 다시 로드(코끼리 아이콘 클릭)하고 재빌드한다.

---

## 🔎 Lombok 관련 빌드 오류를 만났을 때 확인할 것

```
1. build.gradle에 implementation과 annotationProcessor 모두 있는가?
2. IntelliJ → Settings → Build, Execution, Deployment
   → Compiler → Annotation Processors
   → Enable annotation processing 체크가 되어 있는가?
```

두 가지 모두 충족해야 Lombok이 정상 동작한다.

---

## 📎 관련 파일

- `frontend-service/build.gradle`

---

## 📅 발생일

2026-03-21
