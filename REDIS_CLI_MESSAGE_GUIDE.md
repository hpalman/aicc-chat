# Redis-CLI로 고객에게 메시지 보내기 가이드

## 📋 개요

이 가이드는 `redis-cli`를 사용하여 BOT이나 SYSTEM 역할로 특정 고객에게 메시지를 직접 전송하는 방법을 설명합니다.

---

## 🔍 Redis Pub/Sub 구조 분석

### 채널 정보

**파일:** `RedisOnlyConfig.java`

```java
@Bean
public MessageBroker messageBroker() {
    return message -> {
        String msg = objectMapper.writeValueAsString(message);
        redisTemplate.convertAndSend("chat.topic", msg);  // ✅ Redis 채널
    };
}

@Bean
public RedisMessageListenerContainer redisContainer(MessageListenerAdapter adapter) {
    container.addMessageListener(adapter, new ChannelTopic("chat.topic"));  // ✅ 구독 채널
    return container;
}

@Bean
public MessageListenerAdapter listenerAdapter() {
    return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
        // ✅ WebSocket STOMP로 중계
        messagingTemplate.convertAndSend("/topic/room/" + chatMessage.getRoomId(), chatMessage);
    });
}
```

### 데이터 흐름

```
Redis-CLI
    ↓
PUBLISH "chat.topic" "{JSON}"
    ↓
RedisMessageListenerContainer (구독 중)
    ↓
MessageListenerAdapter
    ↓
ChatMessage 역직렬화
    ↓
SimpMessagingTemplate.convertAndSend("/topic/room/{roomId}", chatMessage)
    ↓
WebSocket STOMP
    ↓
고객 브라우저 (chat-customer.html)
```

---

## 🛠️ redis-cli로 메시지 보내기

### 1. Redis 접속

```bash
# 로컬 Redis 접속
redis-cli

# 또는 호스트/포트 지정
redis-cli -h 127.0.0.1 -p 6379

# 비밀번호가 있는 경우
redis-cli -h 127.0.0.1 -p 6379 -a your_password
```

---

### 2. 메시지 JSON 형식

**ChatMessage 구조:**

```java
public class ChatMessage {
    private String roomId;           // 필수
    private String sender;           // 필수
    private UserRole senderRole;     // 필수 (CUSTOMER, AGENT, BOT, SYSTEM)
    private String message;          // 필수
    private MessageType type;        // 필수 (TALK, JOIN, LEAVE, HANDOFF, CANCEL_HANDOFF)
    private String companyId;        // 선택
    private LocalDateTime timestamp; // 선택 (서버에서 자동 설정)
}
```

---

### 3. BOT 메시지 전송 예시

#### 3-1. 일반 BOT 메시지 (TALK)

```bash
redis-cli

# 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"안녕하세요! 무엇을 도와드릴까요?","type":"TALK","companyId":"apt001"}'
```

**JSON 포맷 (가독성):**
```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "안녕하세요! 무엇을 도와드릴까요?",
  "type": "TALK",
  "companyId": "apt001"
}
```

---

#### 3-2. SYSTEM 메시지 전송

```bash
redis-cli

# 시스템 알림 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"시스템 점검이 예정되어 있습니다.","type":"TALK"}'
```

**JSON 포맷:**
```json
{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "SYSTEM",
  "message": "시스템 점검이 예정되어 있습니다.",
  "type": "TALK"
}
```

---

#### 3-3. 타임스탬프 포함 메시지

서버에서 자동으로 타임스탬프를 추가하지 않으므로, 수동으로 지정할 수 있습니다:

```bash
redis-cli

# 타임스탬프 배열 형식
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"현재 시각입니다.","type":"TALK","timestamp":[2026,1,26,15,30,45]}'
```

**JSON 포맷:**
```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "현재 시각입니다.",
  "type": "TALK",
  "timestamp": [2026, 1, 26, 15, 30, 45]
}
```

---

### 4. 특정 고객에게만 메시지 보내기

Redis Pub/Sub 채널 `chat.topic`은 **모든 서버 인스턴스**에 브로드캐스트됩니다.
특정 고객에게만 메시지를 보내려면 **roomId**를 사용합니다.

#### 4-1. roomId 확인

```bash
redis-cli

# 모든 채팅방 목록 확인
SMEMBERS chat:rooms

# 결과 예시:
# 1) "room-abc123"
# 2) "room-def456"
# 3) "room-ghi789"

# 특정 방 정보 확인
GET chat:room:room-abc123:name
# 결과: "홍길동"

GET chat:room:room-abc123:mode
# 결과: "BOT"

# 방의 고객 ID 확인
SMEMBERS chat:room:room-abc123
# 결과:
# 1) "cust01"
```

---

#### 4-2. 특정 roomId로 메시지 전송

```bash
redis-cli

# room-abc123 방에 있는 고객에게만 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"room-abc123 고객님께만 보내는 메시지입니다.","type":"TALK"}'

# room-def456 방에 있는 고객에게만 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-def456","sender":"Bot","senderRole":"BOT","message":"room-def456 고객님께만 보내는 메시지입니다.","type":"TALK"}'
```

**동작 원리:**
1. Redis에 `PUBLISH` 명령으로 메시지 발행
2. 서버의 `MessageListenerAdapter`가 메시지 수신
3. `messagingTemplate.convertAndSend("/topic/room/" + roomId, chatMessage)` 실행
4. WebSocket STOMP가 `/topic/room/{roomId}`를 구독 중인 클라이언트에게만 전송
5. 해당 roomId를 구독하는 고객만 메시지 수신 ✅

---

## 📊 메시지 타입별 예시

### TALK (일반 대화)

```bash
# BOT이 보내는 일반 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"배송은 2-3일 소요됩니다.","type":"TALK"}'

# SYSTEM이 보내는 알림 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"상담원이 곧 연결됩니다.","type":"TALK"}'
```

---

### JOIN (입장 알림)

```bash
# 시스템 입장 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"관리자가 입장했습니다.","type":"JOIN"}'
```

**고객 화면 표시:**
```
[2026-01-26 15:30:45] 관리자가 입장했습니다.
```

---

### LEAVE (퇴장 알림)

```bash
# 시스템 퇴장 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"시스템 점검으로 상담이 종료됩니다.","type":"LEAVE"}'
```

**고객 화면 표시:**
```
[2026-01-26 15:30:45] 시스템 점검으로 상담이 종료됩니다.
```

**주의사항:**
- `LEAVE` 타입이면 `updateHandoffButtons('CLOSED')` 호출됨
- 버튼이 비활성화될 수 있으므로 주의

---

## 🧪 실전 테스트

### 테스트 시나리오 1: BOT 메시지 전송

```bash
# 1. 고객 로그인 및 상담 시작
# 브라우저: http://localhost:28070/chat-customer.html
# "상담 시작" 클릭

# 2. roomId 확인
redis-cli
SMEMBERS chat:rooms
# 결과: room-xyz123 (예시)

# 3. BOT 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-xyz123","sender":"Bot","senderRole":"BOT","message":"테스트 메시지입니다.","type":"TALK"}'

# 4. 고객 화면 확인
# Bot
# 테스트 메시지입니다.
# 2026-01-26 15:30:45  ← 타임스탬프 (없으면 클라이언트 시간)
```

---

### 테스트 시나리오 2: SYSTEM 공지 전송

```bash
# 1. 모든 활성 방 확인
redis-cli
SMEMBERS chat:rooms
# 결과:
# room-abc123
# room-def456
# room-ghi789

# 2. 각 방에 공지 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"[공지] 오늘 오후 6시 시스템 점검 예정입니다.","type":"TALK"}'

PUBLISH "chat.topic" '{"roomId":"room-def456","sender":"System","senderRole":"SYSTEM","message":"[공지] 오늘 오후 6시 시스템 점검 예정입니다.","type":"TALK"}'

PUBLISH "chat.topic" '{"roomId":"room-ghi789","sender":"System","senderRole":"SYSTEM","message":"[공지] 오늘 오후 6시 시스템 점검 예정입니다.","type":"TALK"}'

# 3. 각 고객 화면에 메시지 표시 확인
```

---

### 테스트 시나리오 3: 타임스탬프 포함 메시지

```bash
redis-cli

# 타임스탬프 지정 (배열 형식)
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"정확한 시간이 기록된 메시지입니다.","type":"TALK","timestamp":[2026,1,26,15,30,45]}'

# 고객 화면 확인
# Bot
# 정확한 시간이 기록된 메시지입니다.
# 2026-01-26 15:30:45  ← 지정한 타임스탬프
```

---

## 💡 유용한 Redis 명령어

### 채팅방 정보 조회

```bash
redis-cli

# 모든 채팅방 목록
SMEMBERS chat:rooms

# 특정 방의 이름
GET chat:room:room-abc123:name

# 특정 방의 상태 (BOT/WAITING/AGENT/CLOSED)
GET chat:room:room-abc123:mode

# 특정 방에 배정된 상담원
GET chat:room:room-abc123:assignedAgent

# 특정 방의 멤버 (고객 ID)
SMEMBERS chat:room:room-abc123

# 특정 방의 마지막 활동 시간 (Unix timestamp, ms)
GET chat:room:room-abc123:lastActivity

# 특정 방의 생성 시간
GET chat:room:room-abc123:createdAt
```

---

### Redis Pub/Sub 모니터링

```bash
redis-cli

# 실시간으로 발행되는 메시지 모니터링
SUBSCRIBE chat.topic

# 결과:
# Reading messages... (press Ctrl-C to quit)
# 1) "subscribe"
# 2) "chat.topic"
# 3) (integer) 1
# ...
# 1) "message"
# 2) "chat.topic"
# 3) "{\"roomId\":\"room-abc123\",\"sender\":\"Bot\",\"senderRole\":\"BOT\",\"message\":\"안녕하세요\",\"type\":\"TALK\"}"
```

---

## 📝 메시지 JSON 템플릿

### BOT 일반 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "메시지 내용",
  "type": "TALK"
}
```

**redis-cli 명령:**
```bash
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"메시지 내용","type":"TALK"}'
```

---

### SYSTEM 공지 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "SYSTEM",
  "message": "[공지] 공지 내용",
  "type": "TALK"
}
```

**redis-cli 명령:**
```bash
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"[공지] 공지 내용","type":"TALK"}'
```

---

### BOT 입장 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "Bot이 입장했습니다.",
  "type": "JOIN"
}
```

**redis-cli 명령:**
```bash
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"Bot이 입장했습니다.","type":"JOIN"}'
```

**고객 화면:**
```
[2026-01-26 15:30:45] Bot이 입장했습니다.
```

---

### SYSTEM 상담 종료 메시지 (BOT 복귀)

```json
{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "BOT",
  "message": "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다.",
  "type": "TALK"
}
```

**redis-cli 명령:**
```bash
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"BOT","message":"상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다.","type":"TALK"}'
```

**고객 화면 효과:**
- 메시지 표시
- "상담원 연결" 버튼 활성화 ✅

---

### 타임스탬프 포함 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "2026년 1월 26일 15시 30분 45초 메시지",
  "type": "TALK",
  "timestamp": [2026, 1, 26, 15, 30, 45]
}
```

**redis-cli 명령:**
```bash
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"2026년 1월 26일 15시 30분 45초 메시지","type":"TALK","timestamp":[2026,1,26,15,30,45]}'
```

**고객 화면:**
```
Bot
2026년 1월 26일 15시 30분 45초 메시지
2026-01-26 15:30:45  ← 지정한 타임스탬프
```

---

## 🔍 실전 예시

### 예시 1: 긴급 공지 전송

```bash
# 1. 모든 활성 방 확인
redis-cli
SMEMBERS chat:rooms

# 2. 각 방에 긴급 공지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"[긴급] 서버 점검으로 인해 10분 후 서비스가 일시 중단됩니다.","type":"TALK","timestamp":[2026,1,26,15,50,0]}'

PUBLISH "chat.topic" '{"roomId":"room-def456","sender":"System","senderRole":"SYSTEM","message":"[긴급] 서버 점검으로 인해 10분 후 서비스가 일시 중단됩니다.","type":"TALK","timestamp":[2026,1,26,15,50,0]}'
```

---

### 예시 2: 특정 고객에게 프로모션 메시지

```bash
redis-cli

# 고객 홍길동의 방 확인
GET chat:room:room-abc123:name
# 결과: "홍길동"

# 프로모션 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"[프로모션] 홍길동 고객님, 오늘 주문 시 10% 할인 쿠폰이 제공됩니다!","type":"TALK"}'
```

---

### 예시 3: 디버깅용 테스트 메시지

```bash
redis-cli

# 테스트 메시지 1
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"테스트 1","type":"TALK"}'

# 테스트 메시지 2
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"테스트 2","type":"TALK"}'

# 테스트 메시지 3
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"테스트 3","type":"TALK"}'
```

---

## ⚠️ 주의사항

### 1. JSON 형식 검증

JSON이 올바르지 않으면 메시지가 전송되지 않거나 오류가 발생합니다.

```bash
# ❌ 잘못된 예시 (따옴표 누락)
PUBLISH "chat.topic" '{roomId:"room-abc123",sender:"Bot"}'

# ✅ 올바른 예시
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot"}'
```

---

### 2. 필수 필드 확인

**필수 필드:**
- `roomId` - 채팅방 ID (필수)
- `sender` - 발신자 이름 (필수)
- `senderRole` - 발신자 역할 (필수: BOT, SYSTEM, AGENT, CUSTOMER)
- `message` - 메시지 내용 (필수)
- `type` - 메시지 타입 (필수: TALK, JOIN, LEAVE, HANDOFF, CANCEL_HANDOFF)

**선택 필드:**
- `companyId` - 회사 ID
- `timestamp` - 타임스탬프 (없으면 클라이언트에서 생성)

---

### 3. roomId 존재 확인

존재하지 않는 roomId로 메시지를 보내면 아무도 받지 못합니다.

```bash
# roomId 존재 확인
redis-cli
SISMEMBER chat:rooms room-abc123
# 결과: 1 (존재함) 또는 0 (존재하지 않음)

# 존재하는 경우에만 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"메시지","type":"TALK"}'
```

---

### 4. 타임스탬프 형식

**배열 형식 (권장):**
```json
"timestamp": [2026, 1, 26, 15, 30, 45]
```

**ISO 문자열 형식 (가능):**
```json
"timestamp": "2026-01-26T15:30:45"
```

**타임스탬프 없음 (fallback):**
- 클라이언트에서 현재 시간을 자동으로 사용

---

## 🛠️ 편리한 스크립트 예시

### Bash 스크립트 (Linux/Mac)

```bash
#!/bin/bash

# send-bot-message.sh
ROOM_ID=$1
MESSAGE=$2

if [ -z "$ROOM_ID" ] || [ -z "$MESSAGE" ]; then
    echo "Usage: ./send-bot-message.sh <roomId> <message>"
    exit 1
fi

JSON=$(cat <<EOF
{
  "roomId": "$ROOM_ID",
  "sender": "Bot",
  "senderRole": "BOT",
  "message": "$MESSAGE",
  "type": "TALK"
}
EOF
)

redis-cli PUBLISH "chat.topic" "$JSON"
echo "Message sent to room: $ROOM_ID"
```

**사용 예시:**
```bash
chmod +x send-bot-message.sh
./send-bot-message.sh room-abc123 "안녕하세요! 배송 문의이신가요?"
```

---

### PowerShell 스크립트 (Windows)

```powershell
# send-bot-message.ps1
param(
    [Parameter(Mandatory=$true)]
    [string]$RoomId,
    
    [Parameter(Mandatory=$true)]
    [string]$Message
)

$json = @{
    roomId = $RoomId
    sender = "Bot"
    senderRole = "BOT"
    message = $Message
    type = "TALK"
} | ConvertTo-Json -Compress

redis-cli PUBLISH "chat.topic" $json
Write-Host "Message sent to room: $RoomId"
```

**사용 예시:**
```powershell
.\send-bot-message.ps1 -RoomId "room-abc123" -Message "안녕하세요! 배송 문의이신가요?"
```

---

## 📊 메시지 흐름 요약

```
┌─────────────────────────────────────────────────────────────┐
│                    Redis Pub/Sub 메시지 흐름                 │
└─────────────────────────────────────────────────────────────┘

1. redis-cli
   ↓
   PUBLISH "chat.topic" '{"roomId":"room-abc123",...}'
   ↓
2. Redis Server
   ↓
   chat.topic 채널에 메시지 발행
   ↓
3. RedisMessageListenerContainer (구독 중)
   ↓
   MessageListenerAdapter.onMessage()
   ↓
4. ChatMessage 역직렬화
   ↓
   {
     "roomId": "room-abc123",
     "sender": "Bot",
     "senderRole": "BOT",
     "message": "안녕하세요",
     "type": "TALK"
   }
   ↓
5. SimpMessagingTemplate
   ↓
   convertAndSend("/topic/room/room-abc123", chatMessage)
   ↓
6. WebSocket STOMP
   ↓
   /topic/room/room-abc123 구독자에게만 전송
   ↓
7. chat-customer.html (room-abc123 방의 고객)
   ↓
   stompClient.subscribe('/topic/room/room-abc123', callback)
   ↓
8. showMessage(chatMessage) 실행
   ↓
9. 화면에 메시지 표시 ✅
```

---

## 🎯 핵심 포인트

### 1. Redis 채널
- **채널명:** `chat.topic`
- **발행 명령:** `PUBLISH "chat.topic" '{JSON}'`

### 2. 특정 고객 지정
- **방법:** JSON의 `roomId` 필드 사용
- **원리:** WebSocket STOMP에서 `/topic/room/{roomId}` 구독

### 3. 메시지 역할
- **BOT:** `"senderRole":"BOT"`
- **SYSTEM:** `"senderRole":"SYSTEM"`

### 4. 메시지 타입
- **TALK:** 일반 대화
- **JOIN:** 입장 알림 (시스템 메시지 스타일)
- **LEAVE:** 퇴장 알림 (시스템 메시지 스타일, 버튼 비활성화)

---

## 🧪 빠른 테스트

```bash
# 1. Redis 접속
redis-cli

# 2. 활성 채팅방 확인
SMEMBERS chat:rooms
# 예: room-abc123

# 3. 테스트 메시지 전송
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"Redis-CLI 테스트 메시지","type":"TALK"}'

# 4. 고객 브라우저에서 메시지 확인
# Bot
# Redis-CLI 테스트 메시지
# 2026-01-26 15:30:45

# 5. SYSTEM 메시지도 테스트
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"SYSTEM 테스트 메시지","type":"TALK"}'

# 6. 고객 브라우저에서 메시지 확인
# System
# SYSTEM 테스트 메시지
# 2026-01-26 15:30:50
```

---

## 🎉 완료

redis-cli를 사용하여 BOT이나 SYSTEM 역할로 고객에게 직접 메시지를 전송할 수 있습니다!

**핵심 명령:**
```bash
# BOT 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"Bot","senderRole":"BOT","message":"메시지 내용","type":"TALK"}'

# SYSTEM 메시지
PUBLISH "chat.topic" '{"roomId":"room-abc123","sender":"System","senderRole":"SYSTEM","message":"공지 내용","type":"TALK"}'
```

**주요 포인트:**
- ✅ 채널: `chat.topic`
- ✅ roomId로 특정 고객 지정
- ✅ senderRole로 발신자 역할 지정 (BOT, SYSTEM)
- ✅ type으로 메시지 타입 지정 (TALK, JOIN, LEAVE)
- ✅ timestamp 선택적 지정 가능
