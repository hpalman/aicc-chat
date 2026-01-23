# PostgreSQL + MyBatis 상담 이력 저장 가이드

## 📋 개요

이 문서는 AICC Chat 시스템에서 PostgreSQL과 MyBatis를 사용하여 상담 내용을 영구 저장하는 기능에 대한 설명입니다.

---

## 🗄️ 데이터베이스 구조

### 1. chat_session (상담 세션 테이블)

채팅방의 전체 세션 정보를 저장합니다.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGSERIAL | 세션 고유 ID (자동 증가) |
| room_id | VARCHAR(100) | 채팅방 ID (고유값) |
| room_name | VARCHAR(255) | 채팅방 이름 |
| customer_id | VARCHAR(100) | 고객 ID |
| customer_name | VARCHAR(255) | 고객 이름 |
| assigned_agent | VARCHAR(255) | 배정된 상담원 이름 |
| status | VARCHAR(50) | 세션 상태 (BOT/WAITING/AGENT/CLOSED) |
| company_id | VARCHAR(100) | 회사 ID |
| started_at | TIMESTAMP | 상담 시작 시간 |
| ended_at | TIMESTAMP | 상담 종료 시간 |
| last_activity_at | TIMESTAMP | 마지막 활동 시간 |
| created_at | TIMESTAMP | 생성 시간 |
| updated_at | TIMESTAMP | 수정 시간 |

### 2. chat_history (채팅 이력 테이블)

모든 채팅 메시지를 시간순으로 저장합니다.

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGSERIAL | 이력 고유 ID (자동 증가) |
| room_id | VARCHAR(100) | 채팅방 ID |
| sender_id | VARCHAR(100) | 발신자 ID |
| sender_name | VARCHAR(255) | 발신자 이름 |
| sender_role | VARCHAR(50) | 발신자 역할 (CUSTOMER/AGENT/BOT/SYSTEM) |
| message | TEXT | 메시지 내용 |
| message_type | VARCHAR(50) | 메시지 타입 (ENTER/TALK/LEAVE/JOIN/HANDOFF/CANCEL_HANDOFF) |
| company_id | VARCHAR(100) | 회사 ID |
| created_at | TIMESTAMP | 생성 시간 |
| updated_at | TIMESTAMP | 수정 시간 |

---

## 🚀 설치 및 설정

### 1. PostgreSQL 설치 및 설정

#### Docker 사용 (권장)

```bash
docker run -d \
  --name aicc-postgres \
  -e POSTGRES_DB=aicc_chat \
  -e POSTGRES_USER=aicc \
  -e POSTGRES_PASSWORD=aicc123! \
  -p 5432:5432 \
  postgres:14-alpine
```

#### 직접 설치

```bash
# PostgreSQL 접속
psql -U postgres

# 데이터베이스 및 사용자 생성
CREATE DATABASE aicc_chat;
CREATE USER aicc WITH PASSWORD 'aicc123!';
GRANT ALL PRIVILEGES ON DATABASE aicc_chat TO aicc;
```

### 2. 스키마 생성

```bash
# Docker를 사용하는 경우
docker exec -i aicc-postgres psql -U aicc -d aicc_chat < src/main/resources/db/schema.sql

# 직접 설치한 경우
psql -U aicc -d aicc_chat -f src/main/resources/db/schema.sql
```

### 3. application.yml 설정

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://127.0.0.1:5432/aicc_chat
    username: aicc
    password: aicc123!
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

mybatis:
  mapper-locations: classpath:mybatis/mapper/**/*.xml
  type-aliases-package: aicc.chat.domain.persistence
  configuration:
    map-underscore-to-camel-case: true
    default-fetch-size: 100
    default-statement-timeout: 30
```

---

## 📝 사용 방법

### 1. 자동 저장

채팅 메시지는 다음과 같이 자동으로 저장됩니다:

#### 고객 메시지
- 고객이 채팅방에서 메시지를 보낼 때 자동으로 `chat_history`에 저장됩니다.
- `CustomerChatController.onCustomerMessage()` 메서드에서 처리됩니다.

#### 상담원 메시지
- 상담원이 메시지를 보낼 때 자동으로 저장됩니다.
- `AgentChatController.onAgentMessage()` 메서드에서 처리됩니다.

#### BOT 응답
- MiChat AI 봇의 응답도 자동으로 저장됩니다.
- `MiChatRoutingStrategy.handleMessage()` 메서드에서 처리됩니다.

#### 시스템 메시지
- 상담원 배정, 상담 종료 등 시스템 메시지도 저장됩니다.

### 2. 세션 관리

#### 세션 생성
```java
// 채팅방 생성 시 자동으로 세션 생성
ChatSession chatSession = ChatSession.builder()
    .roomId(newRoomId)
    .customerId(custInfo.getUserId())
    .customerName(custInfo.getUserName())
    .status("BOT")
    .companyId(custInfo.getCompanyId())
    .startedAt(LocalDateTime.now())
    .build();
chatSessionService.createChatSession(chatSession);
```

#### 세션 상태 변경
```java
// BOT → WAITING (상담원 연결 요청)
chatSessionService.updateSessionStatus(roomId, "WAITING");

// WAITING → AGENT (상담원 배정)
chatSessionService.updateSessionStatus(roomId, "AGENT");
chatSessionService.assignAgent(roomId, agentName);

// AGENT → CLOSED (상담 종료)
chatSessionService.endSession(roomId);
```

---

## 🔍 데이터 조회

### 1. Service Layer를 통한 조회

#### 채팅 이력 조회
```java
@Autowired
private ChatHistoryService chatHistoryService;

// 특정 방의 전체 대화 이력
List<ChatHistory> history = chatHistoryService.getChatHistoryByRoomId("room-12345678");

// 특정 시간 범위의 이력
List<ChatHistory> history = chatHistoryService.getChatHistoryByRoomIdAndTimeRange(
    "room-12345678",
    LocalDateTime.now().minusDays(7),
    LocalDateTime.now()
);

// 고객별 이력
List<ChatHistory> history = chatHistoryService.getChatHistoryByCustomerId("user001");
```

#### 세션 조회
```java
@Autowired
private ChatSessionService chatSessionService;

// 특정 방의 세션 정보
ChatSession session = chatSessionService.getChatSessionByRoomId("room-12345678");

// 고객별 상담 이력
List<ChatSession> sessions = chatSessionService.getChatSessionsByCustomerId("user001");

// 상담원별 상담 이력
List<ChatSession> sessions = chatSessionService.getChatSessionsByAgent("agent01");

// 활성 세션 목록
List<ChatSession> activeSessions = chatSessionService.getActiveChatSessions();
```

### 2. SQL 직접 조회

```sql
-- 특정 방의 전체 대화 내역 (시간순)
SELECT * FROM chat_history 
WHERE room_id = 'room-12345678' 
ORDER BY created_at ASC;

-- 오늘 진행된 상담 건수
SELECT COUNT(*) FROM chat_session 
WHERE DATE(started_at) = CURRENT_DATE;

-- 상담원별 처리 건수
SELECT assigned_agent, COUNT(*) as session_count
FROM chat_session 
WHERE assigned_agent IS NOT NULL
GROUP BY assigned_agent
ORDER BY session_count DESC;

-- 활성 상담 세션 조회
SELECT * FROM chat_session 
WHERE status != 'CLOSED' 
ORDER BY last_activity_at DESC;

-- 회사별 일별 상담 건수
SELECT 
    company_id,
    DATE(started_at) as date,
    COUNT(*) as session_count
FROM chat_session
GROUP BY company_id, DATE(started_at)
ORDER BY date DESC;
```

---

## 📊 주요 기능

### 1. 전체 메시지 저장
- 고객 메시지 (CUSTOMER)
- 상담원 메시지 (AGENT)
- BOT 응답 (BOT)
- 시스템 메시지 (SYSTEM)

### 2. 세션 상태 추적
- BOT: AI 자동 응답 중
- WAITING: 상담원 연결 대기 중
- AGENT: 상담원과 상담 중
- CLOSED: 상담 종료됨

### 3. 시간 기반 조회
- 특정 기간의 상담 이력 조회
- 일별/월별 통계 생성
- 마지막 활동 시간 추적

### 4. 다중 조건 검색
- 고객 ID로 검색
- 상담원 이름으로 검색
- 회사 ID로 검색
- 상태별 필터링

---

## 🔧 유지보수

### 1. 오래된 데이터 정리

```java
// 30일 이상 오래된 이력 삭제
LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
chatHistoryService.deleteOldChatHistory(thirtyDaysAgo);
```

또는 SQL:
```sql
DELETE FROM chat_history 
WHERE created_at < NOW() - INTERVAL '30 days';
```

### 2. 인덱스 최적화

주요 인덱스가 자동으로 생성되어 있습니다:
- room_id
- sender_id
- created_at
- company_id
- (room_id, created_at) 복합 인덱스

### 3. 백업

```bash
# 전체 데이터베이스 백업
pg_dump -U aicc aicc_chat > backup_$(date +%Y%m%d).sql

# 특정 테이블만 백업
pg_dump -U aicc -t chat_history aicc_chat > chat_history_backup.sql

# 복원
psql -U aicc aicc_chat < backup_20260123.sql
```

---

## 🎯 활용 예시

### 1. 상담 품질 분석
```sql
-- 평균 상담 시간
SELECT 
    AVG(EXTRACT(EPOCH FROM (ended_at - started_at))/60) as avg_minutes
FROM chat_session 
WHERE ended_at IS NOT NULL;

-- 상담원별 평균 응답 시간
-- (고객 메시지와 상담원 응답 사이의 시간 계산)
```

### 2. 고객 이력 조회
```java
// 고객의 모든 상담 이력
List<ChatSession> sessions = chatSessionService.getChatSessionsByCustomerId("user001");

for (ChatSession session : sessions) {
    List<ChatHistory> messages = chatHistoryService.getChatHistoryByRoomId(session.getRoomId());
    // 각 상담의 전체 대화 내용 표시
}
```

### 3. 실시간 대시보드
```sql
-- 현재 활성 상담 건수
SELECT COUNT(*) FROM chat_session WHERE status IN ('BOT', 'WAITING', 'AGENT');

-- 대기 중인 상담 건수
SELECT COUNT(*) FROM chat_session WHERE status = 'WAITING';

-- 상담원별 처리 중인 상담 건수
SELECT assigned_agent, COUNT(*) 
FROM chat_session 
WHERE status = 'AGENT' 
GROUP BY assigned_agent;
```

---

## 🚨 주의사항

1. **DB 저장 실패 시에도 채팅은 계속 진행됩니다.**
   - 채팅의 실시간성을 우선시합니다.
   - 실패 로그는 기록됩니다.

2. **트랜잭션 관리**
   - 모든 쓰기 작업은 `@Transactional`로 보호됩니다.
   - 읽기 작업은 `@Transactional(readOnly = true)`를 사용합니다.

3. **대용량 데이터 처리**
   - 오래된 데이터는 정기적으로 아카이빙하세요.
   - 필요시 파티셔닝을 고려하세요 (schema.sql 참고).

4. **보안**
   - 민감한 정보는 암호화하여 저장하세요.
   - 데이터베이스 비밀번호는 환경변수로 관리하세요.

---

## 📚 참고 자료

- [MyBatis 공식 문서](https://mybatis.org/mybatis-3/)
- [PostgreSQL 공식 문서](https://www.postgresql.org/docs/)
- [Spring Boot Data Access](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html)
