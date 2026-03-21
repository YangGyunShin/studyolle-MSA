# 🔧 트러블슈팅: PostgreSQL Docker 컨테이너 설정 오류 (3단계 연속 실패)

## 📌 상황 요약

account-service를 처음 기동하려 했는데 세 번 연속으로 서로 다른 오류가 발생했다.
각 오류는 서로 다른 원인이었지만 모두 PostgreSQL Docker 컨테이너 설정과 관련이 있었다.

---

## 📋 1차 오류: 포트 연결 거부

### 오류 메시지

```
Connection to localhost:5433 refused.
Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

### 원인

Docker 컨테이너가 아예 실행되지 않은 상태였다.
`localhost:5433`에 아무것도 없으니 연결 자체가 거부된 것이다.

**이 오류가 나왔을 때 확인해야 할 것:**

```bash
# 현재 실행 중인 Docker 컨테이너 목록 확인
docker ps

# account-db라는 이름의 컨테이너가 없으면 → 컨테이너가 없는 것
# STATUS가 Exited라면 → 컨테이너가 있지만 꺼져 있는 것
```

### 해결

```bash
docker run -d \
  --name account-db \
  -e POSTGRES_DB=account_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5433:5432 \
  postgres:15
```

---

## 📋 2차 오류: 비밀번호 인증 실패

### 오류 메시지

```
FATAL: password authentication failed for user "studyolle"
```

### 원인

컨테이너는 올라왔지만, 처음에 `POSTGRES_USER=postgres`로 만든 컨테이너를 삭제하고
다시 만들 때 계정을 `studyolle`로 바꿨는데 기존 컨테이너가 완전히 삭제되지 않았거나,
`postgres` 계정으로 만들어진 컨테이너에 `studyolle`로 접속을 시도했다.

`account-service`의 `application.yml`을 보면:

```yaml
spring:
  datasource:
    username: studyolle
    password: studyolle
```

이 설정과 컨테이너 생성 시 지정한 계정이 일치해야 한다.

### 해결

기존 컨테이너를 완전히 삭제하고 올바른 계정으로 다시 만든다.

```bash
# 기존 컨테이너 강제 삭제
docker rm -f account-db

# 올바른 계정으로 재생성
docker run -d \
  --name account-db \
  -e POSTGRES_DB=account_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5433:5432 \
  postgres:15
```

---

## 📋 3차 오류: 데이터베이스 없음

### 오류 메시지

```
FATAL: database "account_db" does not exist
```

### 원인

컨테이너는 올라왔고 계정도 맞지만, `account_db`라는 데이터베이스가 없다.
Docker 컨테이너 생성 시 `POSTGRES_DB` 환경변수를 지정해야 PostgreSQL이 초기화될 때
해당 데이터베이스를 자동으로 만들어준다. 이 값이 누락됐거나 오타가 있었을 가능성이 있다.

### 해결

```bash
# 컨테이너 삭제 후 POSTGRES_DB 포함하여 재생성
docker rm -f account-db

docker run -d \
  --name account-db \
  -e POSTGRES_DB=account_db \      ← 반드시 포함
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5433:5432 \
  postgres:15

# 생성 후 5초 대기 후 접속 테스트
docker exec -it account-db psql -U studyolle -d account_db -c "\l"
# account_db가 목록에 보이면 정상
```

---

## 📋 study-service용 DB도 동일하게 설정

study-service는 포트 5434를 사용한다.

```yaml
# study-service/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/study_db
    username: studyolle
    password: studyolle
```

```bash
docker run -d \
  --name study-db \
  -e POSTGRES_DB=study_db \
  -e POSTGRES_USER=studyolle \
  -e POSTGRES_PASSWORD=studyolle \
  -p 5434:5432 \
  postgres:15
```

---

## 🔎 오류 해석 방법 — 이 세 오류를 구분하는 법

```
Connection refused   → 컨테이너 자체가 없거나 꺼져 있음
password authentication failed → 컨테이너는 있지만 계정 정보 불일치
database does not exist → 컨테이너는 있고 계정도 맞지만 DB 이름 불일치
```

세 오류 모두 `application.yml`의 datasource 설정값과
Docker 컨테이너 생성 시 지정한 환경변수가 **완전히 일치**해야 해결된다.

---

## 📎 관련 파일

- `account-service/src/main/resources/application.yml`
- `study-service/src/main/resources/application.yml`

---

## 📅 발생일

2026-03-21
