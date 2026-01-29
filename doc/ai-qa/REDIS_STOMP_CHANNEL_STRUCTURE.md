# Redis & STOMP 채널 정보 및 Redis 키 구조 종합 가이드

> **작성일**: 2026-01-23  
> **목적**: 프로젝트에서 사용하는 Redis Pub/Sub, STOMP, Redis 키/값 구조 종합 정리  
> **범위**: 채널, 토픽, 키 네이밍, 데이터 구조, 흐름도

---

## 📋 목차

1. [Redis Pub/Sub 채널](#-redis-pubsub-채널)
2. [STOMP 토픽](#-stomp-토픽)
3. [Redis 키 구조](#-redis-키-구조)
4. [데이터 흐름](#-데이터-흐름)
5. [전체 구조도](#-전체-구조도)

---

## 🔴 Redis Pub/Sub 채널

### 1. 메시지 브로커 채널

#### `chat.topic` (메인 채널)

**파일**: `RedisOnlyConfig.java`

```java
@Bean
public MessageBroker messageBroker() {
    return message -> {
        String msg = objectMapper.writeValueAsString(message);
        redisTemplate.convertAndSend("chat.topic", msg); // ✅ Redis 채널
    };
}

@Bean
public RedisMessageListenerContainer redisContainer(MessageListenerAdapter adapter) {
    container.addMessageListener(adapter, new ChannelTopic("chat.topic")); // ✅ 구독
    return container;
}
```

**용도:**
- 모든 채팅 메시지 발행/구독
- ChatMessage 객체를 JSON으로 직렬화하여 전송

**메시지 형식:**
```json
{
  "roomId": "room-abc123",
  "sender": "홍길동",
  "senderRole": "CUSTOMER",
  "message": "안녕하세요",
  "type": "TALK",
  "timestamp": [2026, 1, 23, 15, 30, 45, 123456789],
  "companyId": "apt001"
}
```

**메시지 타입 (type):**
- `ENTER` - 입장
- `TALK` - 일반 대화
- `LEAVE` - 퇴장
- `JOIN` - 고객 입장
- `HANDOFF` - 상담원 연결 요청
- `CANCEL_HANDOFF` - 상담원 연결 취소
- `INTERVENE` - 상담원 개입
- `CUSTOMER_DISCONNECTED` - 고객 연결 해제
- `CUSTOMER_LEFT` - 고객 퇴장

---

### 데이터 흐름: Redis Pub/Sub

```
서버 (Controller/EventListener)
    ↓
messageBroker.publish(chatMessage)
    ↓
RedisTemplate.convertAndSend("chat.topic", JSON)
    ↓
Redis Pub/Sub 채널 "chat.topic"
    ↓
RedisMessageListenerContainer (구독 중)
    ↓
MessageListenerAdapter
    ↓
JSON → ChatMessage 역직렬화
    ↓
SimpMessagingTemplate.convertAndSend("/topic/room/{roomId}", chatMessage)
    ↓
STOMP WebSocket
    ↓
클라이언트 (브라우저)
```

---

## 🟢 STOMP 토픽

### 1. 서버 → 클라이언트 (Broker Prefix)

**설정**: `WebSocketConfig.java`

```java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic"); // ✅ 서버 → 클라이언트
    registry.setApplicationDestinationPrefixes("/app"); // ✅ 클라이언트 → 서버
}
```

#### `/topic/room/{roomId}` (개별 채팅방)

**용도**: 특정 채팅방의 메시지 브로드캐스트

**구독 예시 (클라이언트):**
```javascript
stompClient.subscribe('/topic/room/room-abc123', function (message) {
    const msg = JSON.parse(message.body);
    console.log('메시지 수신:', msg);
});
```

**발행 예시 (서버):**
```java
messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage);
```

---

#### `/topic/rooms` (전체 방 목록)

**용도**: 상담원에게 전체 채팅방 목록 실시간 업데이트

**구독 예시 (상담원 화면):**
```javascript
stompClient.subscribe('/topic/rooms', function (message) {
    const rooms = JSON.parse(message.body);
    updateRoomListUI(rooms);
});
```

**발행 예시 (서버):**
```java
roomUpdateBroadcaster.broadcastRoomList();
// → messagingTemplate.convertAndSend("/topic/rooms", roomList);
```

---

### 2. 클라이언트 → 서버 (Application Prefix)

#### `/app/customer/chat` (고객 메시지)

**파일**: `CustomerChatController.java`

```java
@MessageMapping("/customer/chat") // ✅ /app/customer/chat
public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    // 메시지 처리
}
```

**전송 예시 (고객 화면):**
```javascript
stompClient.send('/app/customer/chat', {}, JSON.stringify({
    roomId: 'room-abc123',
    sender: '홍길동',
    message: '안녕하세요',
    type: 'TALK'
}));
```

---

#### `/app/agent/chat` (상담원 메시지)

**파일**: `AgentChatController.java`

```java
@MessageMapping("/agent/chat") // ✅ /app/agent/chat
public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    // 메시지 처리
}
```

**전송 예시 (상담원 화면):**
```javascript
stompClient.send('/app/agent/chat', {}, JSON.stringify({
    roomId: 'room-abc123',
    sender: '상담원01',
    message: '도와드리겠습니다',
    type: 'TALK'
}));
```

---

### 3. WebSocket 엔드포인트

#### `/ws-chat` (공통 엔드포인트)

**설정**: `WebSocketConfig.java`

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws-chat") // ✅ WebSocket 엔드포인트
            .setAllowedOriginPatterns("*")
            .addInterceptors(handshakeInterceptor)
            .withSockJS();
}
```

**연결 예시 (클라이언트):**
```javascript
const socket = new SockJS('/ws-chat?token=' + authToken + '&roomId=' + roomId);
const stompClient = Stomp.over(socket);
```

---

## 💾 Redis 키 구조

### 1. 채팅방 관리 (RedisRoomRepository)

#### 키 Prefix

```java
private static final String ROOM_KEY_PREFIX = "chat:room:"; // 방별 정보
private static final String ROOM_INDEX_KEY = "chat:rooms";  // 전체 방 인덱스
```

---

#### `chat:rooms` (Set) - 전체 방 ID 인덱스

**타입**: Set  
**값**: roomId 목록

```bash
SMEMBERS chat:rooms
# 1) "room-abc123"
# 2) "room-def456"
# 3) "room-ghi789"
```

**용도**: 전체 채팅방 목록 관리

---

#### `chat:room:{roomId}` (Set) - 방 멤버 목록

**타입**: Set  
**값**: memberId (userId 또는 sessionId)

```bash
SMEMBERS chat:room:room-abc123
# 1) "cust01"
# 2) "agent01"
```

**용도**: 특정 방의 참여자 관리

---

#### `chat:room:{roomId}:name` (String) - 방 이름

**타입**: String  
**값**: 방 이름

```bash
GET chat:room:room-abc123:name
# "cust01"
```

**용도**: 방 이름 저장

---

#### `chat:room:{roomId}:mode` (String) - 방 상태

**타입**: String  
**값**: BOT | WAITING | AGENT | CLOSED

```bash
GET chat:room:room-abc123:mode
# "AGENT"
```

**용도**: 현재 방의 라우팅 모드

**상태 설명:**
- `BOT` - 챗봇 상담
- `WAITING` - 상담원 대기 중
- `AGENT` - 상담원 상담 중
- `CLOSED` - 종료됨

---

#### `chat:room:{roomId}:assignedAgent` (String) - 배정된 상담원

**타입**: String  
**값**: 상담원 이름

```bash
GET chat:room:room-abc123:assignedAgent
# "김상담"
```

**용도**: 현재 방에 배정된 상담원 정보

---

#### `chat:room:{roomId}:createdAt` (String) - 생성 시간

**타입**: String  
**값**: 밀리초 타임스탬프

```bash
GET chat:room:room-abc123:createdAt
# "1706011234567"
```

**용도**: 방 생성 시간 (long → String)

---

#### `chat:room:{roomId}:lastActivity` (String) - 마지막 활동 시간

**타입**: String  
**값**: 밀리초 타임스탬프

```bash
GET chat:room:room-abc123:lastActivity
# "1706011345678"
```

**용도**: 마지막 메시지 또는 활동 시간 (유휴 방 정리용)

---

### 2. WebSocket 세션 관리 (WebSocketSessionService)

#### 키 Prefix

```java
private static final String WS_SESSION_TO_USER_PREFIX  = "ws:session:";
private static final String WS_USER_TO_SESSIONS_PREFIX = "ws:user:";
private static final String WS_ALL_SESSIONS_KEY        = "ws:sessions:all";
```

---

#### `ws:session:{sessionId}` (String) - 세션 → 사용자 매핑

**타입**: String  
**값**: userId  
**TTL**: 24시간

```bash
GET ws:session:abc123
# "cust01"
```

**용도**: 세션 ID로 사용자 ID 조회

---

#### `ws:session:{sessionId}:role` (String) - 세션 역할

**타입**: String  
**값**: CUSTOMER | AGENT | SYSTEM  
**TTL**: 24시간

```bash
GET ws:session:abc123:role
# "CUSTOMER"
```

**용도**: 세션의 사용자 역할 저장

---

#### `ws:user:{userId}` (Set) - 사용자 → 세션 매핑

**타입**: Set  
**값**: sessionId 목록  
**TTL**: 24시간

```bash
SMEMBERS ws:user:cust01
# 1) "abc123"
# 2) "def456"
```

**용도**: 한 사용자의 모든 활성 세션 (다중 디바이스 지원)

---

#### `ws:sessions:all` (Set) - 전체 활성 세션

**타입**: Set  
**값**: 모든 sessionId

```bash
SMEMBERS ws:sessions:all
# 1) "abc123"
# 2) "def456"
# 3) "ghi789"
```

**용도**: 전체 활성 세션 목록 관리

---

### 3. 온라인 상담원 관리 (AgentAuthService)

#### 키 Prefix

```java
private static final String ONLINE_AGENTS_KEY = "chat:online:agents";
```

---

#### `chat:online:agents:{userId}` (String) - 온라인 상담원

**타입**: String  
**값**: userName  
**TTL**: 10분

```bash
GET chat:online:agents:agent01
# "김상담"
```

**용도**: 로그인한 상담원 추적 (하트비트로 TTL 갱신)

---

## 🔄 데이터 흐름

### 1. 고객 메시지 전송 흐름

```
고객 (chat-customer.html)
    ↓
stompClient.send('/app/customer/chat', {}, JSON)
    ↓
WebSocket STOMP
    ↓
CustomerChatController.onCustomerMessage()
    ↓
messageBroker.publish(chatMessage)
    ↓
Redis Pub/Sub "chat.topic"
    ↓
RedisMessageListenerContainer
    ↓
messagingTemplate.convertAndSend("/topic/room/{roomId}")
    ↓
STOMP WebSocket
    ↓
구독 중인 클라이언트들
    ↓
채팅 화면에 메시지 표시
```

---

### 2. 상담원 방 목록 업데이트 흐름

```
서버 (RoomUpdateBroadcaster)
    ↓
roomUpdateBroadcaster.broadcastRoomList()
    ↓
messagingTemplate.convertAndSend("/topic/rooms", roomList)
    ↓
STOMP WebSocket
    ↓
상담원 (chat-agent.html)
    ↓
stompClient.subscribe('/topic/rooms', callback)
    ↓
updateRoomListUI(rooms)
```

---

### 3. WebSocket 연결/해제 흐름

```
[연결]
클라이언트 → /ws-chat?token={token}&roomId={roomId}
    ↓
HandshakeInterceptor
    ↓
토큰 검증 → SessionAttributes 설정
    ↓
SessionConnectedEvent 발생
    ↓
WebSocketEventListener.onConnected()
    ↓
webSocketSessionService.registerSession()
    ↓
Redis 저장:
  - ws:session:{sessionId} = {userId}
  - ws:user:{userId} += {sessionId}
  - ws:sessions:all += {sessionId}

[해제]
클라이언트 연결 종료
    ↓
SessionDisconnectEvent 발생
    ↓
WebSocketEventListener.onDisconnect()
    ↓
webSocketSessionService.unregisterSession()
    ↓
Redis 삭제:
  - ws:session:{sessionId}
  - ws:user:{userId}에서 {sessionId} 제거
  - ws:sessions:all에서 {sessionId} 제거
```

---

## 🗺️ 전체 구조도

### Redis 채널 & STOMP 토픽 맵

```
┌─────────────────────────────────────────────────────────────┐
│                    Redis Pub/Sub 채널                        │
├─────────────────────────────────────────────────────────────┤
│  chat.topic                                                 │
│  - 모든 채팅 메시지 발행/구독                                   │
│  - ChatMessage JSON 전송                                     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Redis → STOMP 변환                        │
├─────────────────────────────────────────────────────────────┤
│  RedisMessageListenerContainer                               │
│  → messagingTemplate.convertAndSend()                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      STOMP 토픽                              │
├─────────────────────────────────────────────────────────────┤
│  서버 → 클라이언트 (Broker: /topic)                         │
│  ├─ /topic/room/{roomId}  : 개별 채팅방 메시지              │
│  └─ /topic/rooms          : 전체 방 목록 (상담원용)         │
│                                                              │
│  클라이언트 → 서버 (Application: /app)                      │
│  ├─ /app/customer/chat    : 고객 메시지                     │
│  └─ /app/agent/chat       : 상담원 메시지                   │
│                                                              │
│  WebSocket 엔드포인트                                        │
│  └─ /ws-chat              : SockJS 연결                     │
└─────────────────────────────────────────────────────────────┘
```

---

### Redis 키 구조 트리

```
Redis Keys
├─ chat:rooms (Set)
│  └─ roomId 목록
│
├─ chat:room:{roomId} (Set)
│  └─ 멤버 목록 (userId/sessionId)
│
├─ chat:room:{roomId}:name (String)
│  └─ 방 이름
│
├─ chat:room:{roomId}:mode (String)
│  └─ BOT | WAITING | AGENT | CLOSED
│
├─ chat:room:{roomId}:assignedAgent (String)
│  └─ 상담원 이름
│
├─ chat:room:{roomId}:createdAt (String)
│  └─ 생성 시간 (밀리초)
│
├─ chat:room:{roomId}:lastActivity (String)
│  └─ 마지막 활동 시간 (밀리초)
│
├─ ws:session:{sessionId} (String, TTL: 24h)
│  └─ userId
│
├─ ws:session:{sessionId}:role (String, TTL: 24h)
│  └─ CUSTOMER | AGENT
│
├─ ws:user:{userId} (Set, TTL: 24h)
│  └─ sessionId 목록
│
├─ ws:sessions:all (Set)
│  └─ 전체 활성 세션 ID
│
└─ chat:online:agents:{userId} (String, TTL: 10m)
   └─ userName (상담원 이름)
```

---

## 📊 키 사용 예시

### 채팅방 생성 및 상담 시작

```bash
# 1. 방 생성
SADD chat:rooms "room-abc123"
SET chat:room:room-abc123:name "cust01"
SET chat:room:room-abc123:mode "BOT"
SET chat:room:room-abc123:createdAt "1706011234567"
SET chat:room:room-abc123:lastActivity "1706011234567"

# 2. 고객 입장
SADD chat:room:room-abc123 "cust01"

# 3. WebSocket 세션 등록
SET ws:session:abc123 "cust01" EX 86400
SET ws:session:abc123:role "CUSTOMER" EX 86400
SADD ws:user:cust01 "abc123"
SADD ws:sessions:all "abc123"

# 4. 상담원 배정
SET chat:room:room-abc123:assignedAgent "김상담"
SET chat:room:room-abc123:mode "AGENT"
SADD chat:room:room-abc123 "agent01"
```

---

### 상담원 로그인 및 가용성 체크

```bash
# 1. 상담원 로그인
SET chat:online:agents:agent01 "김상담" EX 600

# 2. 온라인 상담원 확인
KEYS chat:online:agents:*
# 1) "chat:online:agents:agent01"
# 2) "chat:online:agents:agent02"

# 3. 특정 상담원 정보
GET chat:online:agents:agent01
# "김상담"

# 4. TTL 확인
TTL chat:online:agents:agent01
# 582 (초)
```

---

### 고객 다중 세션 (PC + 모바일)

```bash
# PC 접속
SET ws:session:abc123 "cust01" EX 86400
SET ws:session:abc123:role "CUSTOMER" EX 86400
SADD ws:user:cust01 "abc123"
SADD ws:sessions:all "abc123"

# 모바일 접속 (같은 사용자)
SET ws:session:def456 "cust01" EX 86400
SET ws:session:def456:role "CUSTOMER" EX 86400
SADD ws:user:cust01 "def456"
SADD ws:sessions:all "def456"

# 확인
SMEMBERS ws:user:cust01
# 1) "abc123"
# 2) "def456"

# PC 연결 해제
DEL ws:session:abc123
DEL ws:session:abc123:role
SREM ws:user:cust01 "abc123"
SREM ws:sessions:all "abc123"

# 확인
SMEMBERS ws:user:cust01
# 1) "def456"  (모바일만 남음)
```

---

## 🔧 Redis CLI 명령어 모음

### 채팅방 관리

```bash
# 전체 방 목록
SMEMBERS chat:rooms

# 특정 방 정보
GET chat:room:room-abc123:name
GET chat:room:room-abc123:mode
GET chat:room:room-abc123:assignedAgent

# 방 멤버
SMEMBERS chat:room:room-abc123

# 방 삭제
SREM chat:rooms "room-abc123"
DEL chat:room:room-abc123
DEL chat:room:room-abc123:name
DEL chat:room:room-abc123:mode
DEL chat:room:room-abc123:assignedAgent
DEL chat:room:room-abc123:createdAt
DEL chat:room:room-abc123:lastActivity
```

---

### WebSocket 세션 관리

```bash
# 전체 활성 세션
SMEMBERS ws:sessions:all

# 세션 정보
GET ws:session:abc123
GET ws:session:abc123:role

# 사용자의 모든 세션
SMEMBERS ws:user:cust01

# 세션 TTL 확인
TTL ws:session:abc123
```

---

### 온라인 상담원 관리

```bash
# 온라인 상담원 목록
KEYS chat:online:agents:*

# 특정 상담원 정보
GET chat:online:agents:agent01

# TTL 확인
TTL chat:online:agents:agent01

# 강제 로그아웃
DEL chat:online:agents:agent01
```

---

## 📝 요약

### Redis Pub/Sub
- **채널**: `chat.topic` (단일 채널, 모든 메시지)
- **용도**: 메시지 브로커 역할

### STOMP 토픽
- **서버 → 클라이언트**: `/topic/room/{roomId}`, `/topic/rooms`
- **클라이언트 → 서버**: `/app/customer/chat`, `/app/agent/chat`
- **엔드포인트**: `/ws-chat` (SockJS 지원)

### Redis 키 구조
- **채팅방**: `chat:room:*` (방 정보, 멤버, 상태)
- **방 인덱스**: `chat:rooms` (전체 방 ID)
- **세션 관리**: `ws:session:*`, `ws:user:*` (WebSocket 세션)
- **온라인 상담원**: `chat:online:agents:*` (로그인 상태)

### TTL 설정
- WebSocket 세션: 24시간
- 온라인 상담원: 10분 (하트비트 갱신)
- 채팅방: TTL 없음 (수동 삭제)

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23

## ✅ Redis & STOMP 채널/키 구조 종합 정리 완성!

### 📊 정리된 내용

#### 1. Redis Pub/Sub 채널
```text
chat.topic
├─ 모든 채팅 메시지 발행/구독
├─ ChatMessage JSON 전송
└─ 9가지 메시지 타입 지원
```

#### 2. STOMP 토픽

##### 서버 → 클라이언트:
```
/topic/room/{roomId}  - 개별 채팅방 메시지
/topic/rooms          - 전체 방 목록 (상담원용)
```

##### 클라이언트 → 서버:
```
/app/customer/chat    - 고객 메시지
/app/agent/chat       - 상담원 메시지
```
##### 엔드포인트:
```
/ws-chat              - WebSocket 연결 (SockJS 지원)
```

#### 3. Redis 키 구조 (3가지 카테고리)
##### A. 채팅방 관리 (RedisRoomRepository)
```
chat:rooms                           - 전체 방 ID 인덱스 (Set)
chat:room:{roomId}                   - 방 멤버 목록 (Set)
chat:room:{roomId}:name              - 방 이름 (String)
chat:room:{roomId}:mode              - 방 상태 (String: BOT/WAITING/AGENT/CLOSED)
chat:room:{roomId}:assignedAgent     - 배정된 상담원 (String)
chat:room:{roomId}:createdAt         - 생성 시간 (String, 밀리초)
chat:room:{roomId}:lastActivity      - 마지막 활동 시간 (String, 밀리초)
```

##### B. WebSocket 세션 관리 (WebSocketSessionService)
```
ws:session:{sessionId}               - 세션 → 사용자 매핑 (String, TTL: 24h)
ws:session:{sessionId}:role          - 세션 역할 (String, TTL: 24h)
ws:user:{userId}                     - 사용자 → 세션 Set (Set, TTL: 24h)
ws:sessions:all                      - 전체 활성 세션 (Set)
```

##### C. 온라인 상담원 관리 (AgentAuthService)
```
chat:online:agents:{userId}          - 온라인 상담원 (String, TTL: 10m)
```

#### 4. 데이터 흐름
##### 메시지 전송:
```
고객/상담원
  ↓ STOMP
서버 Controller
  ↓ messageBroker.publish()
Redis Pub/Sub (chat.topic)
  ↓ RedisMessageListenerContainer
STOMP (messagingTemplate)
  ↓ /topic/room/{roomId}
구독 중인 클라이언트들
```

##### 방 목록 업데이트:
```
서버
  ↓ roomUpdateBroadcaster
STOMP /topic/rooms
  ↓
상담원 화면
```

#### 5. 실제 사용 예시
##### 채팅방 생성:
```
SADD chat:rooms "room-abc123"
SET chat:room:room-abc123:name "cust01"
SET chat:room:room-abc123:mode "BOT"
```
##### WebSocket 세션:
```
SET ws:session:abc123 "cust01" EX 86400
SADD ws:user:cust01 "abc123"
```

##### 온라인 상담원:
```
SET chat:online:agents:agent01 "김상담" EX 600
```

### 📁 문서 위치
<b>저장 경로</b>: e:\aicc-dev\aicc\aicc-chat\doc\ai-qa\REDIS_STOMP_CHANNEL_STRUCTURE.md<br/>
<b>포함 내용:</b><br/>
✅ Redis Pub/Sub 채널 상세 설명<br/>
✅ STOMP 토픽 및 엔드포인트<br/>
✅ Redis 키 구조 (3가지 카테고리, 16개 키 패턴)<br/>
✅ 데이터 흐름도<br/>
✅ 전체 구조도<br/>
✅ Redis CLI 명령어 예시<br/>
✅ 실제 사용 시나리오<br/>

### 🎉 완료!
프로젝트의 Redis Pub/Sub, STOMP, Redis 키 구조를 종합 정리했습니다!