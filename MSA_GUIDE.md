# StudyOlle MSA 전환 가이드

> 이 파일은 studyolle-msa 프로젝트의 전체 구성 가이드입니다.
> 순서대로 따라하면 Phase 1 (Spring Cloud 인프라) 까지 완성됩니다.

---

## 전체 디렉토리 구조 (최종 목표)

```
studyolle-msa/
├── infrastructure/
│   ├── eureka-server/
│   │   ├── src/main/java/com/studyolle/eureka/
│   │   │   └── EurekaServerApplication.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   └── build.gradle
│   ├── config-server/
│   │   ├── src/main/java/com/studyolle/config/
│   │   │   └── ConfigServerApplication.java
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   └── build.gradle
│   └── api-gateway/
│       ├── src/main/java/com/studyolle/gateway/
│       │   ├── ApiGatewayApplication.java
│       │   └── filter/
│       │       └── JwtAuthenticationFilter.java
│       ├── src/main/resources/
│       │   └── application.yml
│       └── build.gradle
├── services/
│   ├── account-service/     (Phase 2에서 구현)
│   ├── study-service/       (Phase 3에서 구현)
│   ├── event-service/       (Phase 3에서 구현)
│   └── notification-service/ (Phase 4에서 구현)
├── docker-compose.yml
├── settings.gradle
└── build.gradle
```

---

## STEP 1: 터미널 명령어 - 디렉토리 생성

아래 명령어를 터미널에서 순서대로 실행하세요.

```bash
cd ~/Library/Mobile\ Documents/com~apple~CloudDocs/Spring/study

mkdir studyolle-msa
cd studyolle-msa

mkdir -p infrastructure/eureka-server/src/main/java/com/studyolle/eureka
mkdir -p infrastructure/eureka-server/src/main/resources
mkdir -p infrastructure/config-server/src/main/java/com/studyolle/config
mkdir -p infrastructure/config-server/src/main/resources
mkdir -p infrastructure/api-gateway/src/main/java/com/studyolle/gateway/filter
mkdir -p infrastructure/api-gateway/src/main/resources
mkdir -p services/account-service/src/main/java/com/studyolle/account
mkdir -p services/account-service/src/main/resources
mkdir -p services/study-service/src/main/java/com/studyolle/study
mkdir -p services/study-service/src/main/resources
mkdir -p services/event-service/src/main/java/com/studyolle/event
mkdir -p services/event-service/src/main/resources
mkdir -p services/notification-service/src/main/java/com/studyolle/notification
mkdir -p services/notification-service/src/main/resources
```

---

## STEP 2: 루트 settings.gradle

경로: studyolle-msa/settings.gradle

```groovy
rootProject.name = 'studyolle-msa'

// 인프라 모듈
include 'infrastructure:eureka-server'
include 'infrastructure:config-server'
include 'infrastructure:api-gateway'

// 서비스 모듈 (Phase 2~4에서 순차적으로 구현)
include 'services:account-service'
include 'services:study-service'
include 'services:event-service'
include 'services:notification-service'
```

---

## STEP 3: 루트 build.gradle

경로: studyolle-msa/build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.7' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

// 모든 서브모듈에 공통 적용
subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'com.studyolle'
    version = '0.0.1-SNAPSHOT'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    // Spring Boot + Spring Cloud BOM (버전 관리 중앙화)
    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:3.4.7"
            mavenBom "org.springframework.cloud:spring-cloud-dependencies:2024.0.1"
        }
    }

    // 모든 서브모듈 공통 의존성
    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }

    tasks.named('test') {
        useJUnitPlatform()
    }
}
```

---

## STEP 4: Eureka Server

### 파일 1: infrastructure/eureka-server/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
}
```

---

### 파일 2: infrastructure/eureka-server/src/main/resources/application.yml

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    # Eureka Server 자신은 자기 자신에게 등록할 필요 없음
    register-with-eureka: false
    fetch-registry: false
  server:
    # 시작 시 대기 시간 0으로 설정 (개발 환경에서만 사용)
    wait-time-in-ms-when-sync-empty: 0
  instance:
    hostname: localhost
```

---

### 파일 3: infrastructure/eureka-server/src/main/java/com/studyolle/eureka/EurekaServerApplication.java

```java
package com.studyolle.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server - 서비스 디스커버리 서버
 *
 * [역할]
 * MSA 환경에서 각 서비스(account, study, event 등)가 기동될 때
 * 자신의 위치(IP + Port)를 이 서버에 등록한다.
 *
 * API Gateway나 다른 서비스가 "account-service의 주소가 어디야?" 라고 물으면
 * Eureka Server가 등록된 정보를 바탕으로 답해준다.
 * 이것이 "서비스 디스커버리(Service Discovery)" 패턴이다.
 *
 * [접속 주소]
 * 기동 후 http://localhost:8761 에서 등록된 서비스 목록을 확인할 수 있다.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

---

## STEP 5: Config Server

### 파일 1: infrastructure/config-server/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

---

### 파일 2: infrastructure/config-server/src/main/resources/application.yml

```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        # 로컬 파일 시스템에서 설정 파일 읽기 (개발용)
        # 운영에서는 git.uri를 GitHub 주소로 변경
        native:
          search-locations: classpath:/config-repo
    # 로컬 파일 시스템 프로파일 활성화
  profiles:
    active: native

# Eureka 서버에 Config Server 자신도 등록
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

---

### 파일 3: infrastructure/config-server/src/main/resources/config-repo/application.yml

> 이 파일은 모든 서비스에 공통으로 적용되는 설정입니다.

```yaml
# 모든 마이크로서비스 공통 설정
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  thymeleaf:
    cache: false

# Eureka 클라이언트 공통 설정
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

### 파일 4: infrastructure/config-server/src/main/java/com/studyolle/config/ConfigServerApplication.java

```java
package com.studyolle.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server - 중앙 설정 관리 서버
 *
 * [역할]
 * 모든 마이크로서비스의 application.yml 설정을 한 곳에서 관리한다.
 *
 * 각 서비스는 기동 시 Config Server에 접속하여 자신의 설정을 가져온다.
 * 설정이 변경되면 각 서비스를 재시작하지 않고 /actuator/refresh 엔드포인트로
 * 설정을 동적으로 반영할 수 있다.
 *
 * [설정 파일 우선순위]
 * config-repo/account-service.yml > config-repo/application.yml
 * (서비스별 설정이 공통 설정보다 우선 적용됨)
 *
 * [접속 주소]
 * http://localhost:8888/application/default → 공통 설정 확인
 * http://localhost:8888/account-service/default → account-service 설정 확인
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

---

## STEP 6: API Gateway

### 파일 1: infrastructure/api-gateway/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    // Spring Cloud Gateway (WebFlux 기반, Tomcat 아님)
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'

    // Eureka 클라이언트 (lb:// 로드밸런싱을 위해 필요)
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    // JWT 처리
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

---

### 파일 2: infrastructure/api-gateway/src/main/resources/application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      # 전역 CORS 설정
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods: "*"
            allowed-headers: "*"
      routes:
        # ── Account Service ───────────────────────────────────────
        - id: account-service-auth
          uri: lb://ACCOUNT-SERVICE        # Eureka에서 ACCOUNT-SERVICE를 찾아 로드밸런싱
          predicates:
            - Path=/api/auth/**            # 로그인, 토큰 갱신 (인증 불필요)

        - id: account-service-api
          uri: lb://ACCOUNT-SERVICE
          predicates:
            - Path=/api/accounts/**
          filters:
            - name: JwtAuthentication      # JWT 검증 필터 적용

        # ── Study Service ─────────────────────────────────────────
        - id: study-service
          uri: lb://STUDY-SERVICE
          predicates:
            - Path=/api/studies/**
          filters:
            - name: JwtAuthentication

        # ── Event Service ─────────────────────────────────────────
        - id: event-service
          uri: lb://EVENT-SERVICE
          predicates:
            - Path=/api/events/**
          filters:
            - name: JwtAuthentication

        # ── Notification Service ──────────────────────────────────
        - id: notification-service
          uri: lb://NOTIFICATION-SERVICE
          predicates:
            - Path=/api/notifications/**
          filters:
            - name: JwtAuthentication

# Eureka 등록
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# JWT 시크릿 키 (운영에서는 환경변수로 관리)
jwt:
  secret: studyolle-msa-jwt-secret-key-must-be-at-least-256-bits-long
  expiration: 1800000   # 30분 (ms)
```

---

### 파일 3: infrastructure/api-gateway/src/main/java/com/studyolle/gateway/ApiGatewayApplication.java

```java
package com.studyolle.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway - 단일 진입점 (Single Entry Point)
 *
 * [역할]
 * 모든 클라이언트 요청은 반드시 이 게이트웨이를 통과한다.
 *
 * 1. 라우팅: 요청 경로(Path)에 따라 적절한 마이크로서비스로 전달
 * 2. 인증:   JWT 토큰 검증을 게이트웨이에서 중앙 처리
 *            → 각 서비스는 인증 로직을 따로 구현할 필요 없음
 * 3. 부하분산: Eureka와 연동하여 lb:// 방식으로 자동 로드밸런싱
 *
 * [Spring Cloud Gateway vs Zuul]
 * Spring Cloud Gateway는 WebFlux(Reactive) 기반이다.
 * 기존 Spring MVC(Servlet) 방식의 Zuul 1.x보다 성능이 우수하며,
 * Spring Cloud에서 공식적으로 권장하는 게이트웨이이다.
 *
 * [포트]
 * 클라이언트는 항상 8080 포트만 알면 된다.
 * 내부 서비스들의 포트(8081, 8082 등)는 클라이언트에 노출되지 않는다.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

---

### 파일 4: infrastructure/api-gateway/src/main/java/com/studyolle/gateway/filter/JwtAuthenticationFilter.java

```java
package com.studyolle.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 인증 필터 - API Gateway에서 모든 인증을 중앙 처리
 *
 * [동작 흐름]
 * 1. 클라이언트 요청에서 Authorization 헤더 추출
 * 2. "Bearer {token}" 형식에서 토큰 파싱
 * 3. JWT 서명 검증 + 만료 시간 확인
 * 4. 검증 성공 시 → accountId를 X-Account-Id 헤더에 추가하여 하위 서비스로 전달
 * 5. 검증 실패 시 → 401 Unauthorized 반환 (하위 서비스에 요청 전달하지 않음)
 *
 * [하위 서비스에서의 활용]
 * 각 마이크로서비스는 JWT 검증 없이 X-Account-Id 헤더만 읽으면 된다.
 * 게이트웨이가 이미 검증했으므로 신뢰할 수 있는 값이다.
 *
 * [AbstractGatewayFilterFactory 상속 이유]
 * application.yml에서 'name: JwtAuthentication' 으로 선언하면
 * Spring Cloud Gateway가 이 필터를 자동으로 찾아 적용한다.
 * 클래스 이름에서 GatewayFilterFactory 접미사를 제거한 이름이 yml의 name이 된다.
 * JwtAuthenticationGatewayFilterFactory → JwtAuthentication
 */
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String secretKey;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Authorization 헤더 확인
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Authorization 헤더가 없습니다.");
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Bearer 토큰 형식이 아닙니다.");
            }

            // 2. 토큰 추출 및 검증
            String token = authHeader.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // 3. 검증 성공 → accountId를 헤더에 추가하여 하위 서비스로 전달
                String accountId = claims.getSubject();
                String nickname = claims.get("nickname", String.class);

                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-Account-Id", accountId)
                        .header("X-Account-Nickname", nickname)
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (Exception e) {
                return onError(exchange, "유효하지 않은 JWT 토큰입니다: " + e.getMessage());
            }
        };
    }

    /**
     * 인증 실패 시 401 응답 반환
     * Mono<Void>를 반환하여 체인을 종료하고 더 이상 하위 서비스로 전달하지 않는다.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    /**
     * 필터 설정 클래스 (현재는 설정값 없음, 확장 가능)
     * application.yml에서 args를 전달하려면 이 클래스에 필드를 추가한다.
     */
    public static class Config {
    }
}
```

---

## STEP 7: Docker Compose

경로: studyolle-msa/docker-compose.yml

```yaml
version: '3.8'

services:

  # ═══════════════════════════════════════════════
  # 인프라 서버 (Phase 1)
  # ═══════════════════════════════════════════════

  eureka-server:
    build:
      context: ./infrastructure/eureka-server
      dockerfile: Dockerfile
    container_name: eureka-server
    ports:
      - "8761:8761"
    networks:
      - studyolle-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  config-server:
    build:
      context: ./infrastructure/config-server
      dockerfile: Dockerfile
    container_name: config-server
    ports:
      - "8888:8888"
    networks:
      - studyolle-network
    depends_on:
      eureka-server:
        condition: service_healthy

  api-gateway:
    build:
      context: ./infrastructure/api-gateway
      dockerfile: Dockerfile
    container_name: api-gateway
    ports:
      - "8080:8080"
    networks:
      - studyolle-network
    depends_on:
      eureka-server:
        condition: service_healthy

  # ═══════════════════════════════════════════════
  # 미들웨어
  # ═══════════════════════════════════════════════

  rabbitmq:
    image: rabbitmq:3-management
    container_name: rabbitmq
    ports:
      - "5672:5672"      # AMQP 프로토콜 (서비스 간 메시지 통신)
      - "15672:15672"    # 관리 UI (http://localhost:15672, guest/guest)
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    networks:
      - studyolle-network

  # ═══════════════════════════════════════════════
  # 데이터베이스 (서비스별 독립 DB)
  # ═══════════════════════════════════════════════

  postgres-account:
    image: postgres:16
    container_name: postgres-account
    environment:
      POSTGRES_DB: account_db
      POSTGRES_USER: user
      POSTGRES_PASSWORD: "1234"
    ports:
      - "5433:5432"
    volumes:
      - postgres-account-data:/var/lib/postgresql/data
    networks:
      - studyolle-network

  postgres-study:
    image: postgres:16
    container_name: postgres-study
    environment:
      POSTGRES_DB: study_db
      POSTGRES_USER: user
      POSTGRES_PASSWORD: "1234"
    ports:
      - "5434:5432"
    volumes:
      - postgres-study-data:/var/lib/postgresql/data
    networks:
      - studyolle-network

  postgres-event:
    image: postgres:16
    container_name: postgres-event
    environment:
      POSTGRES_DB: event_db
      POSTGRES_USER: user
      POSTGRES_PASSWORD: "1234"
    ports:
      - "5435:5432"
    volumes:
      - postgres-event-data:/var/lib/postgresql/data
    networks:
      - studyolle-network

  # notification-service는 MongoDB 사용 (기술 스택 다양화)
  mongo-notification:
    image: mongo:7
    container_name: mongo-notification
    ports:
      - "27017:27017"
    volumes:
      - mongo-notification-data:/data/db
    networks:
      - studyolle-network

volumes:
  postgres-account-data:
  postgres-study-data:
  postgres-event-data:
  mongo-notification-data:

networks:
  studyolle-network:
    driver: bridge
```

---

## STEP 8: 각 모듈 Dockerfile

> 모든 서비스에 동일한 Dockerfile을 사용합니다.
> 각 모듈 루트(build.gradle과 같은 위치)에 생성하세요.

### 파일: Dockerfile (공통 - 각 모듈 루트에 복사)

```dockerfile
# 빌드 스테이지
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app

# 루트 Gradle 설정 복사 (의존성 캐싱 활용)
COPY settings.gradle ../settings.gradle
COPY build.gradle ../build.gradle

# 현재 모듈 파일 복사
COPY . .

# 빌드 (테스트 제외)
RUN gradle bootJar --no-daemon -x test

# 실행 스테이지 (경량 이미지)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 결과물 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출 (서비스마다 다름, 환경변수로 오버라이드 가능)
EXPOSE 8080

# JVM 메모리 최적화 옵션 포함
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## STEP 9: 빈 서비스 모듈 build.gradle (Phase 1 완성용)

> 지금 당장 구현하지 않지만, 멀티모듈 빌드가 통과하려면
> 각 서비스 모듈에 최소한의 build.gradle이 있어야 합니다.

### services/account-service/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

### services/study-service/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

### services/event-service/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

### services/notification-service/build.gradle

```groovy
plugins {
    id 'org.springframework.boot'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

---

## STEP 10: 빌드 및 기동 확인

### 빌드

```bash
# studyolle-msa 루트에서 실행
./gradlew build

# 또는 wrapper가 없으면 (최초 1회)
gradle wrapper --gradle-version 8.10
./gradlew build
```

### 인프라 서버 순서대로 기동

```bash
# 1. Eureka Server 먼저 기동
./gradlew :infrastructure:eureka-server:bootRun

# 2. Config Server 기동 (새 터미널)
./gradlew :infrastructure:config-server:bootRun

# 3. API Gateway 기동 (새 터미널)
./gradlew :infrastructure:api-gateway:bootRun
```

### 또는 Docker Compose로 한 번에 기동

```bash
# 전체 인프라 기동
docker-compose up -d eureka-server config-server api-gateway rabbitmq

# 로그 확인
docker-compose logs -f eureka-server
```

### 기동 확인

```
Eureka Dashboard:  http://localhost:8761
Config Server:     http://localhost:8888/application/default
API Gateway:       http://localhost:8080
RabbitMQ 관리 UI: http://localhost:15672  (guest / guest)
```

---

## Phase 1 완료 체크리스트

- [ ] settings.gradle - 모든 모듈 include 확인
- [ ] 루트 build.gradle - Spring Cloud BOM 버전 설정 확인
- [ ] Eureka Server 기동 → http://localhost:8761 접속 성공
- [ ] Config Server 기동 → http://localhost:8888/application/default 응답 확인
- [ ] API Gateway 기동 → 8080 포트 Listen 확인
- [ ] Docker Compose up → 전체 인프라 기동 확인

완료되면 Phase 2 (Account Service 분리 + JWT 인증)로 진행합니다.

---

## 다음 단계 예고 (Phase 2: Account Service)

Phase 2에서 작업할 내용:

1. account-service에 JWT 발급 로직 구현
   - POST /api/auth/login → Access Token + Refresh Token 반환
   - POST /api/auth/refresh → Access Token 갱신

2. 기존 studyolle 프로젝트의 account 모듈 코드 이관
   - Account 엔티티, Repository, Service
   - 세션 기반 인증 → JWT 기반으로 변경

3. 나머지 서비스들이 JWT의 X-Account-Id 헤더로 사용자 식별

---

*이 파일은 studyolle-msa/ 루트에 저장되어 있습니다.*
*다음 대화에서 이 파일을 참고하여 Phase 2부터 이어서 진행하세요.*
