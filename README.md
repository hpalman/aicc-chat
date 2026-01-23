# AICC Chat System (REDIS_ONLY Mode)

Redis 기반 실시간 채팅 및 AI 챗봇 통합 시스템

## 📋 목차
- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [설치 가이드](#설치-가이드)
- [사용 방법](#사용-방법)
- [Redis 채널 구조](#redis-채널-구조)

---

## 프로젝트 개요

AICC Chat은 Spring Boot 기반의 실시간 채팅 시스템으로, WebSocket과 Redis Pub/Sub을 통한 양방향 통신과 MiChat AI 엔진을 활용한 챗봇 기능을 제공합니다.

### 기술 스택
- **Backend**: Spring Boot 3.4.1, Java 17
- **Real-time**: WebSocket (STOMP)
- **Message Broker**: Redis Pub/Sub
- **Cache/Session**: Redis
- **Database**: PostgreSQL 14+ (MyBatis)
- **AI Chatbot**: MiChat (자체 AI 엔진)

---

## 주요 기능

### 1. 실시간 채팅
- WebSocket (STOMP) 기반 양방향 통신
- Redis Pub/Sub을 통한 메시지 브로드캐스팅
- 다중 채팅방 지원
- 사용자 입장/퇴장 알림
- roomId 기반 메시지 라우팅

### 2. AI 챗봇 통합 (MiChat)
- MiChat AI 엔진 통합
- 자연어 이해 및 응답
- 세션 기반 대화 맥락 유지
- 스트리밍 응답 지원

### 3. 하이브리드 상담 모드
- BOT 모드: AI 자동 응답
- WAITING 모드: 상담원 연결 대기
- AGENT 모드: 상담원 1:1 상담
- CLOSED 모드: 상담 종료

### 4. 상담 이력 저장
- PostgreSQL + MyBatis를 통한 영구 저장
- 채팅 세션 정보 저장 (고객, 상담원, 상태 등)
- 모든 메시지 이력 저장 (고객, 상담원, BOT, 시스템)
- 시간 기반 조회 및 분석 지원

### 5. Redis 기반 확장 가능한 아키텍처
- Redis Pub/Sub을 통한 다중 서버 인스턴스 지원
- Redis를 통한 채팅방 상태 관리
- 세션 기반 인증 및 권한 관리

---

## 시스템 아키텍처

```
┌─────────────┐     WebSocket      ┌──────────────────┐
│   Client    │ ←─────────────────→ │  Spring Boot     │
│  (Browser)  │     (STOMP)         │  Application     │
└─────────────┘                     └──────────────────┘
                                            │
                                    ┌───────┴────────┐
                                    │                │
                              ┌─────▼─────┐   ┌─────▼─────┐
                              │   Redis   │   │  MiChat   │
                              │ (Pub/Sub) │   │   (AI)    │
                              └───────────┘   └───────────┘
```

### Redis 채널 구조

#### Pub/Sub 채널
- **채널명**: `chat.topic`
- **용도**: 모든 채팅 메시지 브로드캐스트

#### Redis 데이터 키 구조
```
chat:rooms                           # SET: 활성 방 ID 목록
chat:room:{roomId}                   # SET: 방의 멤버 목록
chat:room:{roomId}:name              # STRING: 방 이름
chat:room:{roomId}:mode              # STRING: 방 상태 (BOT/WAITING/AGENT/CLOSED)
chat:room:{roomId}:assignedAgent     # STRING: 배정된 상담원
chat:room:{roomId}:createdAt         # STRING: 방 생성 시간
chat:room:{roomId}:lastActivity      # STRING: 마지막 활동 시간
```

---

## 설치 가이드

### 사전 요구사항
- Java 17+
- Gradle 7.x+
- Redis 6.x+
- PostgreSQL 14+
- MiChat AI 엔진 (선택)

### 1. 저장소 클론
```bash
git clone <repository-url>
cd aicc-chat
```

### 2. PostgreSQL 데이터베이스 설정
```bash
# PostgreSQL 접속
psql -U postgres

# 데이터베이스 및 사용자 생성
CREATE DATABASE aicc_chat;
CREATE USER aicc WITH PASSWORD 'aicc123!';
GRANT ALL PRIVILEGES ON DATABASE aicc_chat TO aicc;

# 테이블 생성
\c aicc_chat
\i src/main/resources/db/schema.sql
```

또는 Docker로 PostgreSQL 실행:
```bash
docker run -d \
  --name aicc-postgres \
  -e POSTGRES_DB=aicc_chat \
  -e POSTGRES_USER=aicc \
  -e POSTGRES_PASSWORD=aicc123! \
  -p 5432:5432 \
  postgres:14-alpine

# 스키마 생성
docker exec -i aicc-postgres psql -U aicc -d aicc_chat < src/main/resources/db/schema.sql
```

### 3. 의존성 설치 및 빌드
```bash
./gradlew clean build
```

### 3. Redis 시작
```bash
# Docker로 Redis 시작
docker run -d -p 16379:6379 --name aicc-redis redis:7-alpine

# 또는 로컬 Redis 사용
redis-server --port 16379
```

### 5. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 5. 접속 확인
- 웹 클라이언트: `http://localhost:28070/websocket-client.html`
- 관리자 클라이언트: `http://localhost:28070/admin-client.html`

---

## 환경 설정

### application.yml
```yaml
server:
  port: 28070

app:
  system-mode: REDIS_ONLY  # Redis 전용 모드 (고정)
  
  ai-bot:
    use-bot: true
    name: "aicess.michat"
    ai-end-point: "http://127.0.0.1:8040"
    
  chat:
    mode: HYBRID      # MiChat -> Agent 전환 모드 (기본값)
    # mode: MICHAT    # MiChat 전용 모드
    # mode: AGENT     # 상담원 전용 모드

spring:
  # Redis 설정 (Spring Data Redis)
  data:
    redis:
      host: 127.0.0.1
      port: 16379
      timeout: 3000
      
  # PostgreSQL 데이터베이스 설정
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://127.0.0.1:5432/aicc_chat
    username: aicc
    password: aicc123!

# MyBatis 설정
mybatis:
  mapper-locations: classpath:mybatis/mapper/**/*.xml
  type-aliases-package: aicc.chat.domain.persistence
  configuration:
    map-underscore-to-camel-case: true
```

### 채팅 모드
- **HYBRID**: AI 봇 자동 응답 + 상담원 전환 지원 (기본값)
- **MICHAT**: AI 봇만 사용
- **AGENT**: 상담원만 사용 (봇 없음)

---

## 사용 방법

### 고객 채팅방 생성
```bash
curl -X POST http://localhost:28070/api/customer/chatbot \
  -H "Authorization: Bearer {token}"
```

### WebSocket 연결
```javascript
const socket = new SockJS('http://localhost:28070/ws-chat?token={token}&roomId={roomId}');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    // 채팅방 구독
    stompClient.subscribe('/topic/room/' + roomId, function(message) {
        const chatMessage = JSON.parse(message.body);
        console.log('Received:', chatMessage);
    });
    
    // 메시지 전송 (고객)
    stompClient.send('/app/customer/chat', {}, JSON.stringify({
        roomId: roomId,
        message: 'Hello!',
        type: 'TALK'
    }));
});
```

### Redis-CLI로 메시지 전송
```bash
# Redis 연결
redis-cli -h 127.0.0.1 -p 16379

# 특정 방으로 메시지 전송
PUBLISH chat.topic '{"roomId":"room-12345678","sender":"System","senderRole":"SYSTEM","message":"공지사항입니다.","type":"TALK","companyId":"apt001"}'

# 활성 방 목록 확인
SMEMBERS chat:rooms

# 특정 방 정보 확인
GET chat:room:room-12345678:mode
SMEMBERS chat:room:room-12345678
```

---

## Redis 채널 구조

### 메시지 흐름
```
[발신자] → WebSocket → [Controller] → [RoutingStrategy]
    ↓
[MessageBroker] → Redis PUBLISH → "chat.topic"
    ↓
[RedisMessageListener] → 모든 서버 인스턴스가 구독
    ↓
[SimpMessagingTemplate] → "/topic/room/{roomId}"
    ↓
[WebSocket 구독자] → 해당 roomId를 구독한 클라이언트만 수신
```

### 메시지 타입
- **ENTER**: 사용자 입장
- **TALK**: 일반 대화
- **LEAVE**: 사용자 퇴장
- **JOIN**: 상담원 참여
- **HANDOFF**: 상담원 연결 요청
- **CANCEL_HANDOFF**: 연결 요청 취소

### ChatMessage 구조
```json
{
  "roomId": "room-12345678",
  "sender": "홍길동",
  "senderRole": "CUSTOMER",  // CUSTOMER, AGENT, BOT, SYSTEM
  "message": "안녕하세요",
  "type": "TALK",
  "companyId": "apt001"
}
```

---

## 프로젝트 구조

```
aicc-chat/
├── src/main/java/aicc/
│   ├── bot/                    # 챗봇 통합
│   │   ├── michat/            # MiChat 구현
│   │   ├── service/           # AI 분석 서비스
│   │   └── web/               # Bot API 컨트롤러
│   └── chat/                   # 채팅 기능
│       ├── config/            # 설정
│       │   ├── mode/          # RedisOnlyConfig
│       │   └── WebSocketConfig.java
│       ├── controller/        # REST & WebSocket 컨트롤러
│       ├── domain/            # 도메인 모델
│       ├── service/           # 비즈니스 로직
│       │   └── impl/          # 구현체 (Redis, Routing)
│       └── websocket/         # WebSocket 이벤트
├── frontend/                   # 프론트엔드 클라이언트
│   ├── websocket-client.html # 일반 사용자 클라이언트
│   └── admin-client.html      # 관리자 클라이언트
└── build.gradle               # Gradle 빌드 설정
```

---

## API 엔드포인트

### 고객 API
- `POST /api/customer/chatbot` - 채팅방 생성
- `WebSocket /app/customer/chat` - 고객 메시지 전송

### 상담원 API
- `GET /api/agent/rooms` - 전체 채팅방 목록
- `GET /api/agent/rooms/{roomId}` - 특정 방 정보
- `POST /api/agent/rooms/{roomId}/assign` - 상담원 배정
- `DELETE /api/agent/rooms/{roomId}` - 상담 종료
- `WebSocket /app/agent/chat` - 상담원 메시지 전송

### WebSocket
- **연결**: `ws://localhost:28070/ws-chat?token={token}&roomId={roomId}`
- **구독**: `/topic/room/{roomId}`

---

## 개발 가이드

### 로컬 개발 환경 설정
```bash
# 1. Redis 시작
docker run -d -p 16379:6379 redis:7-alpine

# 2. 애플리케이션 실행 (개발 모드)
./gradlew bootRun

# 3. Redis 모니터링
redis-cli -h 127.0.0.1 -p 16379 MONITOR
```

### Redis 디버깅
```bash
# Redis 메시지 구독 (모니터링)
redis-cli -h 127.0.0.1 -p 16379
SUBSCRIBE chat.topic

# 활성 방 목록
SMEMBERS chat:rooms

# 방 상세 정보
GET chat:room:room-abc123:name
GET chat:room:room-abc123:mode
GET chat:room:room-abc123:assignedAgent
SMEMBERS chat:room:room-abc123
```

---

## 문제 해결

### Redis 연결 오류
```bash
# Redis 상태 확인
redis-cli -h 127.0.0.1 -p 16379 PING

# Redis 재시작
docker restart aicc-redis
```

### 메시지가 전달되지 않을 때
```bash
# Redis Pub/Sub 모니터링
redis-cli -h 127.0.0.1 -p 16379
SUBSCRIBE chat.topic

# 방 정보 확인
SMEMBERS chat:rooms
GET chat:room:{roomId}:mode
```

### MiChat AI 연결 오류
```bash
# MiChat 엔드포인트 확인
curl http://127.0.0.1:8040/health

# application.yml에서 ai-end-point 확인
```

---

## 라이선스

이 프로젝트는 [라이선스 유형]에 따라 라이선스가 부여됩니다.

---

## 감사의 말

- [Spring Boot](https://spring.io/projects/spring-boot) - 애플리케이션 프레임워크
- [Redis](https://redis.io/) - 인메모리 데이터 저장소 및 Pub/Sub
- [STOMP](https://stomp.github.io/) - WebSocket 메시징 프로토콜
