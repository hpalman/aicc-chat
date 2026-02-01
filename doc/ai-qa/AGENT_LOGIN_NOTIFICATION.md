# 상담원 로그인/로그아웃 실시간 알림 기능

> **작성일**: 2026-02-01  
> **목적**: 상담원이 로그인/로그아웃할 때 대기 중인 고객에게 실시간으로 알림을 보내 버튼 상태를 즉시 업데이트

---

## 📋 개요

상담원이 로그인하면 **모든 대기 중인 고객의 화면에서 "상담원 대기 중" 버튼이 즉시 "상담원 연결"로 활성화**됩니다.

### 기존 문제점
- 고객이 30초마다 폴링으로 상담원 가용성 체크
- 상담원이 로그인해도 최대 30초 지연 발생
- 고객 경험 저하

### 해결 방법
- **MessageBroker (Redis Pub/Sub)** 를 통한 실시간 브로드캐스트
- WebSocket `/topic/agent-availability` 채널 구독
- 상담원 로그인/로그아웃 시 즉시 알림

---

## 🔧 구현 상세

### 1. 백엔드 - AgentAuthService.java

#### 상담원 로그인 시 알림 발송

```java
public UserInfo login(String id, String password) {
    // ... 기존 로그인 로직 ...
    
    // Redis에 온라인 상담원 등록 (10분 TTL)
    String agentKey = Constants.ONLINE_AGENTS_KEY + ":" + account.getUserId();
    redisTemplate.opsForValue().set(agentKey, account.getUserName(), 10, TimeUnit.MINUTES);
    log.info("Agent {} registered as online in Redis", account.getUserId());

    // ✅ 상담원 로그인 알림 브로드캐스트 (MessageBroker 사용)
    messageBroker.publish(ChatMessage.builder()
        .roomId("SYSTEM_BROADCAST")  // 시스템 브로드캐스트 전용 roomId
        .sender("System")
        .senderRole(UserRole.SYSTEM)
        .message("AGENT_AVAILABLE")  // 상담원 가용 메시지
        .type(MessageType.SYSTEM)
        .timestamp(LocalDateTime.now())
        .build());
    
    return userInfo;
}
```

**동작 흐름:**
```
1. 상담원 로그인
   ↓
2. Redis에 온라인 상담원 등록
   ↓
3. MessageBroker.publish() 호출
   ↓
4. Redis Pub/Sub "chat.topic" 채널로 발행
   ↓
5. 모든 서버 인스턴스의 Redis Listener가 수신
   ↓
6. /topic/agent-availability 채널로 WebSocket 전송
   ↓
7. 모든 고객의 브라우저가 즉시 수신
   ↓
8. checkAgentAvailability() 자동 실행
   ↓
9. 버튼 상태 업데이트 ✅
```

---

### 2. 백엔드 - ChatAgentController.java

#### 상담원 로그아웃 API 추가

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String token) {
    log.info("▶ 상담원 로그아웃 처리:logout 시작./api/agent > /logout S");
    
    // 토큰 검증
    String actualToken = token.substring(7);
    UserInfo userInfo = tokenService.validateToken(actualToken);
    
    // Redis에서 온라인 상담원 제거
    String agentKey = ONLINE_AGENTS_KEY + ":" + userInfo.getUserId();
    redisTemplate.delete(agentKey);
    log.info("Agent {} removed from online list in Redis", userInfo.getUserId());

    // ✅ 상담원 로그아웃 알림 브로드캐스트
    ChatMessage logoutMessage = ChatMessage.builder()
        .roomId("SYSTEM_BROADCAST")
        .sender("System")
        .senderRole(UserRole.SYSTEM)
        .message("AGENT_UNAVAILABLE")  // 상담원 불가 메시지
        .type(MessageType.SYSTEM)
        .timestamp(LocalDateTime.now())
        .build();
    messageBroker.publish(logoutMessage);

    return ResponseEntity.ok().build();
}
```

---

### 3. 백엔드 - RedisOnlyConfig.java

#### Redis Listener에서 시스템 브로드캐스트 처리

```java
@Bean
public MessageListenerAdapter listenerAdapter() {
    return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
            
            // ✅ 시스템 브로드캐스트 메시지 처리
            if ("SYSTEM_BROADCAST".equals(chatMessage.getRoomId())) {
                log.info("System broadcast message received: {}", chatMessage.getMessage());
                
                // 상담원 가용 여부 결정
                boolean available = "AGENT_AVAILABLE".equals(chatMessage.getMessage());
                
                // /topic/agent-availability 채널로 전송
                messagingTemplate.convertAndSend("/topic/agent-availability", 
                    Map.of(
                        "available", available, 
                        "message", chatMessage.getMessage(),
                        "timestamp", System.currentTimeMillis()
                    ));
            } else {
                // 일반 채팅 메시지는 해당 방으로 전송
                messagingTemplate.convertAndSend("/topic/room/" + chatMessage.getRoomId(), chatMessage);
            }
        } catch (Exception e) {
            log.error("Redis Subscribe Error", e);
        }
    });
}
```

**처리 로직:**
- `roomId == "SYSTEM_BROADCAST"` → 시스템 알림
- `message == "AGENT_AVAILABLE"` → 상담원 로그인
- `message == "AGENT_UNAVAILABLE"` → 상담원 로그아웃

---

### 4. 프론트엔드 - chat-customer.html

#### WebSocket 연결 시 상담원 가용성 채널 구독

```javascript
function connect() {
    const socket = new SockJS(`/ws-chat?token=${authToken}&roomId=${currentRoomId}`);
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('WebSocket 연결 성공: ' + frame);
        
        // 기존 방 채널 구독
        stompClient.subscribe('/topic/room/' + currentRoomId, function (message) {
            const payload = typeof message.body === 'string' ? JSON.parse(message.body) : message.body;
            showMessage(payload);
        });
        
        // ✅ 상담원 가용성 실시간 알림 구독
        stompClient.subscribe('/topic/agent-availability', function (message) {
            const payload = typeof message.body === 'string' ? JSON.parse(message.body) : message.body;
            console.log('🔔 상담원 가용성 변경 알림 수신:', payload);
            // 즉시 가용성 체크
            checkAgentAvailability();
        });
        
        // UI 전환
        document.getElementById("connect-form").style.display = "none";
        document.getElementById("chat-page").style.display = "block";
    });
}
```

#### 상담원 가용성 체크 함수 (로그 개선)

```javascript
function checkAgentAvailability() {
    const handoffBtn = document.getElementById("handoffBtn");
    const cancelBtn = document.getElementById("cancelHandoffBtn");
    
    fetch('/api/agent/availability')
        .then(res => res.json())
        .then(data => {
            console.log('✅ 상담원 가용성 체크 결과:', data);
            if (data.available) {
                // 상담원 가용 - 버튼 활성화
                handoffBtn.disabled = false;
                handoffBtn.innerText = "상담원 연결";
                handoffBtn.title = "상담원과 연결할 수 있습니다";
                console.log('✅ 상담원 연결 버튼 활성화 (온라인 상담원:', data.onlineAgentCount + '명)');
            } else {
                // 상담원 불가 - 버튼 비활성화
                handoffBtn.disabled = true;
                handoffBtn.innerText = "상담원 대기 중";
                handoffBtn.title = "모든 상담원이 상담 중입니다. 잠시 후 다시 시도해주세요.";
                console.log('⏳ 상담원 대기 중 (온라인 상담원:', data.onlineAgentCount + '명)');
            }
            cancelBtn.style.display = "none";
        })
        .catch(err => {
            console.error('❌ 상담원 가용성 확인 실패:', err);
        });
}
```

---

### 5. 프론트엔드 - chat-agent.html

#### 로그아웃 시 서버에 알림

```javascript
function logout() {
    // 하트비트 중단
    stopHeartbeat();
    
    // ✅ 서버에 로그아웃 알림 (고객에게 상담원 로그아웃 브로드캐스트)
    fetch('/api/agent/logout', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + authToken }
    })
    .then(() => {
        console.log('로그아웃 알림 전송 완료');
    })
    .catch(err => {
        console.error('로그아웃 알림 전송 실패:', err);
    })
    .finally(() => {
        sessionStorage.removeItem("AGENT_TOKEN");
        window.location.reload();
    });
}
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 상담원 로그인 → 고객 버튼 즉시 활성화 ✅

```
1. 초기 상태
   - 모든 상담원 로그아웃
   - 고객 A가 상담 시작
   - 버튼: "상담원 대기 중" (비활성화)

2. 상담원 로그인
   - agent01 로그인
   - 서버: MessageBroker.publish("AGENT_AVAILABLE")
   - Redis: "chat.topic" 채널로 발행

3. Redis Listener 수신
   - 모든 서버 인스턴스가 수신
   - /topic/agent-availability 채널로 WebSocket 전송

4. 고객 브라우저 수신
   - 고객 A의 WebSocket이 즉시 수신
   - console.log: "🔔 상담원 가용성 변경 알림 수신"
   - checkAgentAvailability() 자동 실행

5. 버튼 상태 업데이트 ✅
   - API 호출: GET /api/agent/availability
   - 응답: { "available": true, "onlineAgentCount": 1 }
   - 버튼: "상담원 연결" (활성화)
   - console.log: "✅ 상담원 연결 버튼 활성화 (온라인 상담원: 1명)"

결과: 지연 없이 즉시 버튼 활성화! ✅
```

---

### 시나리오 2: 상담원 로그아웃 → 고객 버튼 즉시 비활성화 ✅

```
1. 초기 상태
   - agent01 온라인
   - 고객 A가 상담 중
   - 버튼: "상담원 연결" (활성화)

2. 상담원 로그아웃
   - agent01 로그아웃 버튼 클릭
   - fetch('/api/agent/logout') 호출
   - 서버: Redis에서 상담원 제거
   - 서버: MessageBroker.publish("AGENT_UNAVAILABLE")

3. Redis Listener 수신
   - /topic/agent-availability 채널로 전송
   - { "available": false, "message": "AGENT_UNAVAILABLE" }

4. 고객 브라우저 수신
   - 고객 A의 WebSocket이 즉시 수신
   - checkAgentAvailability() 자동 실행

5. 버튼 상태 업데이트 ✅
   - API 호출: GET /api/agent/availability
   - 응답: { "available": false, "onlineAgentCount": 0 }
   - 버튼: "상담원 대기 중" (비활성화)
   - console.log: "⏳ 상담원 대기 중 (온라인 상담원: 0명)"

결과: 지연 없이 즉시 버튼 비활성화! ✅
```

---

### 시나리오 3: 다중 서버 환경 테스트 ✅

```
서버 구성:
- 서버 A: 고객 1, 고객 2 연결
- 서버 B: 고객 3 연결, 상담원 로그인

동작:
1. 서버 B에서 상담원 로그인
   ↓
2. MessageBroker.publish() → Redis "chat.topic"
   ↓
3. 서버 A, B 모두 Redis Listener 수신
   ↓
4. 서버 A: 고객 1, 2에게 WebSocket 전송
   서버 B: 고객 3에게 WebSocket 전송
   ↓
5. 모든 고객의 버튼이 동시에 활성화 ✅

결과: 분산 시스템에서도 모든 고객에게 알림 전달! ✅
```

---

## 📊 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                     상담원 로그인 알림 흐름                      │
└─────────────────────────────────────────────────────────────┘

[상담원 브라우저]
    │
    │ POST /api/agent/login
    ↓
[AgentAuthService]
    │
    │ 1. Redis 등록: chat:online:agents:agent01
    │ 2. messageBroker.publish(AGENT_AVAILABLE)
    ↓
[MessageBroker]
    │
    │ redisTemplate.convertAndSend("chat.topic", message)
    ↓
[Redis Pub/Sub]
    │
    │ 모든 서버 인스턴스로 브로드캐스트
    ├──────────────┬──────────────┐
    ↓              ↓              ↓
[서버 A]       [서버 B]       [서버 C]
    │              │              │
    │ Redis Listener 수신
    │ roomId == "SYSTEM_BROADCAST" 체크
    │ messagingTemplate.convertAndSend("/topic/agent-availability")
    ↓              ↓              ↓
[고객 1, 2]    [고객 3]       [고객 4, 5]
    │              │              │
    │ WebSocket 수신: /topic/agent-availability
    │ checkAgentAvailability() 실행
    │ 버튼 상태 업데이트 ✅
    ↓              ↓              ↓
[상담원 연결]  [상담원 연결]  [상담원 연결]
```

---

## 📝 변경된 파일 목록

### 백엔드 (3개)
1. ✅ `AgentAuthService.java`
   - 로그인 시 MessageBroker.publish() 추가 (이미 구현됨)

2. ✅ `ChatAgentController.java`
   - `/api/agent/logout` 엔드포인트 추가
   - 로그아웃 시 Redis 제거 및 브로드캐스트

3. ✅ `RedisOnlyConfig.java`
   - Redis Listener에서 SYSTEM_BROADCAST 처리
   - /topic/agent-availability 채널로 전송

### 프론트엔드 (2개)
4. ✅ `chat-customer.html`
   - /topic/agent-availability 채널 구독
   - checkAgentAvailability() 로그 개선

5. ✅ `chat-agent.html`
   - logout() 함수에서 /api/agent/logout 호출

---

## 🔍 디버깅 가이드

### 1. Redis 메시지 확인

```bash
# Redis CLI 접속
redis-cli

# Redis Pub/Sub 채널 구독 (실시간 모니터링)
SUBSCRIBE chat.topic

# 결과 예시:
# 1) "message"
# 2) "chat.topic"
# 3) "{\"roomId\":\"SYSTEM_BROADCAST\",\"sender\":\"System\",\"message\":\"AGENT_AVAILABLE\",...}"
```

### 2. 브라우저 콘솔 로그 확인

**고객 화면 (chat-customer.html):**
```
✅ WebSocket 연결 성공
🔔 상담원 가용성 변경 알림 수신: {available: true, message: "AGENT_AVAILABLE", timestamp: 1738387200000}
✅ 상담원 가용성 체크 결과: {available: true, onlineAgentCount: 1, agentCount: 0, agentRoomCount: {}}
✅ 상담원 연결 버튼 활성화 (온라인 상담원: 1명)
```

**상담원 화면 (chat-agent.html):**
```
WebSocket Connected for Admin
로그아웃 알림 전송 완료
```

### 3. 서버 로그 확인

```
[AgentAuthService] Agent agent01 registered as online in Redis
[RedisOnlyConfig] System broadcast message received: AGENT_AVAILABLE
```

---

## ⚠️ 주의사항

### 1. MessageBroker vs SimpMessagingTemplate

| 방식 | 전파 범위 | 사용 용도 |
|------|----------|----------|
| **MessageBroker.publish()** | 모든 서버 → 모든 클라이언트 | 채팅 메시지, 시스템 알림 |
| **simpMessagingTemplate** | 현재 서버 → 연결된 클라이언트만 | 방 목록 업데이트 (단일 서버) |

**본 기능에서는 MessageBroker 사용 (분산 시스템 지원) ✅**

### 2. roomId = "SYSTEM_BROADCAST"

- 일반 채팅방과 구분하기 위한 특수 roomId
- Redis Listener에서 이 값을 체크하여 시스템 알림 처리
- 절대 실제 채팅방 ID로 사용하지 말 것

### 3. 폴링 주기 유지

- 실시간 알림이 있어도 폴링은 유지 (백업)
- 네트워크 문제로 WebSocket 메시지 누락 대비
- 현재: 30초 주기 (필요시 60초로 조정 가능)

---

## 🎉 완료!

상담원이 로그인/로그아웃하면 **모든 대기 중인 고객에게 즉시 알림**이 전달됩니다!

**주요 개선사항:**
- ✅ MessageBroker (Redis Pub/Sub) 기반 분산 시스템 지원
- ✅ 상담원 로그인 시 즉시 버튼 활성화 (지연 없음)
- ✅ 상담원 로그아웃 시 즉시 버튼 비활성화
- ✅ 다중 서버 환경에서도 모든 고객에게 알림 전달
- ✅ 상세한 콘솔 로그로 디버깅 용이

**테스트 체크리스트:**
```
□ 상담원 로그인 → 고객 버튼 즉시 활성화
□ 상담원 로그아웃 → 고객 버튼 즉시 비활성화
□ 다중 고객 동시 알림 수신 확인
□ 브라우저 콘솔 로그 확인
□ Redis Pub/Sub 메시지 확인
```
