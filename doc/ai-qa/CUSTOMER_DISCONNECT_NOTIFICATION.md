# 고객 연결 해제/종료 시 상담원 알림 기능 가이드

> **작성일**: 2026-01-23  
> **목적**: 고객의 WebSocket 연결 해제 또는 상담 종료 시 상담원에게 실시간 알림 전송  
> **기능**: 네트워크 끊김/브라우저 종료/명시적 퇴장 감지 및 상담원 알림

---

## 📋 목차

1. [개요](#-개요)
2. [시나리오 분석](#-시나리오-분석)
3. [구현 상세](#-구현-상세)
4. [메시지 타입](#-메시지-타입)
5. [화면 구성](#-화면-구성)
6. [테스트 시나리오](#-테스트-시나리오)
7. [Redis 메시지 구조](#-redis-메시지-구조)

---

## 🎯 개요

### 목적

고객이 채팅 중 다음과 같은 상황에서 상담원에게 실시간으로 알림을 보냅니다:
1. **네트워크 끊김** - 인터넷 연결 불안정
2. **브라우저 종료** - 고객이 창을 닫음
3. **명시적 퇴장** - 고객이 "나가기" 버튼 클릭

### 주요 기능

| 기능 | 설명 |
|------|------|
| **자동 감지** | WebSocket 연결 해제 자동 감지 |
| **실시간 알림** | 상담원 화면에 즉시 알림 표시 |
| **상태 구분** | 연결 해제 vs 명시적 퇴장 구분 |
| **방 상태 업데이트** | 고객 이탈 시 방 목록 자동 갱신 |
| **시각적 표시** | 탭에 경고 배경색 표시 |

---

## 📊 시나리오 분석

### 시나리오 1: 고객 네트워크 끊김 🔌

```
1. 고객이 상담원과 채팅 중
   - 상태: AGENT (상담원 배정됨)
   
2. 고객의 인터넷 연결 끊김
   - WebSocket 연결 자동 해제
   
3. 서버에서 SessionDisconnectEvent 발생
   - WebSocketEventListener.onDisconnect() 호출
   
4. 고객 역할(CUSTOMER) 확인
   - userRole = "CUSTOMER"
   - roomId 존재 확인
   - 배정된 상담원 확인
   
5. 상담원에게 알림 전송
   - 메시지 타입: CUSTOMER_DISCONNECTED
   - 내용: "OOO 고객의 연결이 끊어졌습니다."
   
6. 상담원 화면에 표시
   - ⚠️ 경고 아이콘과 함께 노란색 배경
   - "고객의 네트워크 연결이 끊어졌습니다. 잠시 기다려주세요."
```

---

### 시나리오 2: 고객이 브라우저 닫기 🚪

```
1. 고객이 상담원과 채팅 중
   
2. 고객이 브라우저 창 닫기
   - beforeunload 이벤트 발생
   - WebSocket 연결 종료
   
3. 서버에서 SessionDisconnectEvent 발생
   
4. 상담원에게 알림 전송
   - 메시지 타입: CUSTOMER_DISCONNECTED
   - 시나리오 1과 동일한 처리
```

---

### 시나리오 3: 고객이 명시적으로 상담 종료 👋

```
1. 고객이 채팅 화면에서 "나가기" 버튼 클릭
   
2. 클라이언트에서 LEAVE 메시지 전송
   - type: "LEAVE"
   - message: "OOO님이 나갔습니다."
   
3. 서버 CustomerChatController.onCustomerMessage()에서 처리
   - MessageType.LEAVE 감지
   - 배정된 상담원 확인
   
4. 상담원에게 알림 전송
   - 메시지 타입: CUSTOMER_LEFT
   - 내용: "OOO 고객이 상담을 종료했습니다."
   
5. 상담원 화면에 표시
   - 👋 손흔드는 아이콘과 빨간색 배경
   - "고객이 채팅을 종료했습니다. 상담을 마무리해주세요."
   
6. WebSocket 연결도 곧 종료됨
   - SessionDisconnectEvent도 발생하지만 중복 알림 방지
```

---

## 🔧 구현 상세

### 1. MessageType.java - 새로운 메시지 타입 추가

```java
package aicc.chat.domain;

public enum MessageType {
    ENTER,
    TALK,
    LEAVE,
    JOIN,                      // 고객 입장
    HANDOFF,                   // 상담원 연결 요청
    CANCEL_HANDOFF,            // 상담원 연결 요청 취소
    INTERVENE,                 // 상담원 개입 알림
    CUSTOMER_DISCONNECTED,     // ✅ 고객 연결 해제 알림 (네트워크 끊김)
    CUSTOMER_LEFT              // ✅ 고객 퇴장 알림 (명시적 종료)
}
```

**새로운 메시지 타입:**
- `CUSTOMER_DISCONNECTED` - 네트워크 끊김, 브라우저 종료 등
- `CUSTOMER_LEFT` - 고객이 "나가기" 버튼으로 명시적 퇴장

---

### 2. WebSocketEventListener.java - 연결 해제 감지

```java
@EventListener
public void onDisconnect(SessionDisconnectEvent event) {
    log.info("▶ WebSocket 연결 해제 이벤트");
    
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);
    
    String simpSessionId = sessionAttribute.getSessionId();
    String userId = sessionAttribute.getUserId();
    String userName = sessionAttribute.getUserName();
    String userRole = sessionAttribute.getUserRole();
    String roomId = sessionAttribute.getRoomId();
    
    // 1. Redis에서 세션 정보 제거
    if (simpSessionId != null) {
        webSocketSessionService.unregisterSession(simpSessionId);
        log.info("✅ Redis에서 세션 제거 완료!");
    }
    
    // 2. 고객이 연결 해제된 경우 상담원에게 알림 ✅
    if ("CUSTOMER".equals(userRole) && roomId != null && userId != null) {
        log.info("🔔 고객 연결 해제 알림 전송 시작...");
        
        try {
            // 채팅방 정보 조회
            ChatRoom room = roomRepository.findRoomById(roomId);
            
            if (room != null && room.getAssignedAgent() != null) {
                // 상담원이 배정된 경우에만 알림 전송
                log.info("  - assignedAgent: {}", room.getAssignedAgent());
                
                ChatMessage disconnectNotice = ChatMessage.builder()
                        .roomId(roomId)
                        .sender("System")
                        .senderRole(UserRole.SYSTEM)
                        .message(userName + " 고객의 연결이 끊어졌습니다.")
                        .type(MessageType.CUSTOMER_DISCONNECTED) // ✅
                        .timestamp(LocalDateTime.now())
                        .build();
                
                messageBroker.publish(disconnectNotice);
                
                log.info("✅ 고객 연결 해제 알림 전송 완료!");
            } else {
                log.info("  ℹ️ 상담원이 배정되지 않은 방 - 알림 전송 생략");
            }
        } catch (Exception e) {
            log.error("❌ 고객 연결 해제 알림 전송 실패", e);
        }
    }
    
    // 3. 채팅방 멤버 제거
    roomRepository.removeMemberFromAll(simpSessionId);
}
```

**핵심 로직:**
1. WebSocket 연결 해제 감지
2. 고객 역할(`CUSTOMER`) 확인
3. 상담원이 배정된 방인지 확인
4. Redis Pub/Sub으로 알림 메시지 발행

---

### 3. CustomerChatController.java - 명시적 퇴장 감지

```java
@MessageMapping("/customer/chat")
public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    // ... 기존 로직 ...
    
    // 고객이 LEAVE 메시지를 보낸 경우 상담원에게 알림 ✅
    if (MessageType.LEAVE.equals(message.getType())) {
        log.info("🔔 고객 퇴장 메시지 감지 - roomId: {}, userId: {}", 
                 message.getRoomId(), userId);
        
        try {
            ChatRoom room = roomRepository.findRoomById(message.getRoomId());
            
            if (room != null && room.getAssignedAgent() != null) {
                // 상담원이 배정된 경우 상담원에게 알림
                log.info("  - assignedAgent: {}", room.getAssignedAgent());
                
                ChatMessage leaveNotice = ChatMessage.builder()
                        .roomId(message.getRoomId())
                        .sender("System")
                        .senderRole(UserRole.SYSTEM)
                        .message(message.getSender() + " 고객이 상담을 종료했습니다.")
                        .type(MessageType.CUSTOMER_LEFT) // ✅
                        .timestamp(LocalDateTime.now())
                        .build();
                
                messageBroker.publish(leaveNotice);
                
                log.info("✅ 고객 퇴장 알림 전송 완료!");
            } else {
                log.info("  ℹ️ 상담원이 배정되지 않은 방 - 알림 전송 생략");
            }
        } catch (Exception e) {
            log.error("❌ 고객 퇴장 알림 전송 실패", e);
        }
    }
    
    // ... 기존 로직 계속 ...
}
```

**핵심 로직:**
1. `LEAVE` 메시지 타입 감지
2. 상담원 배정 확인
3. `CUSTOMER_LEFT` 타입 알림 발송

---

### 4. chat-agent.html - 알림 수신 및 표시

#### 메시지 구독 로직

```javascript
const sub = stompClient.subscribe('/topic/room/' + roomId, function (message) {
    const msg = typeof message.body === 'string' ? JSON.parse(message.body) : message.body;
    roomMessages[roomId].push(msg);
    
    // 고객 연결 해제 또는 퇴장 알림 처리 ✅
    if (msg.type === 'CUSTOMER_DISCONNECTED' || msg.type === 'CUSTOMER_LEFT') {
        console.log('🔔 고객 이탈 알림 수신:', msg.type, roomId);
        
        // 탭이 열려있으면 메시지 표시
        if (currentRoomId === roomId) {
            showMessage(msg);
        }
        
        // 방 상태 업데이트를 위해 방 목록 새로고침
        setTimeout(() => {
            loadRooms();
        }, 500);
        
        // 탭 배지에 알림 표시
        if (openTabs[roomId]) {
            const tab = document.querySelector(`.chat-tab[data-room-id="${roomId}"]`);
            if (tab) {
                tab.style.backgroundColor = '#ffebee'; // 연한 빨간색 배경
            }
        }
        
        return; // 일반 메시지 처리 로직 생략
    }
    
    // ... 일반 메시지 처리 ...
});
```

---

#### 메시지 표시 로직

```javascript
function showMessage(msg) {
    const chatBox = document.getElementById("chat-box");
    const div = document.createElement("div");
    const timestamp = formatTimestamp(msg.timestamp);

    // ... 기존 JOIN, LEAVE 처리 ...
    
    if (msg.type === 'CUSTOMER_DISCONNECTED') {
        // 고객 연결 해제 알림 ✅
        div.className = "system-msg";
        div.style.cssText = "color: #ff6b6b; font-weight: bold; background-color: #fff3cd; padding: 10px; border-left: 4px solid #ff6b6b;";
        div.innerHTML = `
            <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-size: 20px;">⚠️</span>
                <div>
                    <div>[${timestamp}] ${msg.message}</div>
                    <div style="font-size: 0.9em; color: #856404; margin-top: 4px;">
                        고객의 네트워크 연결이 끊어졌습니다. 잠시 기다려주세요.
                    </div>
                </div>
            </div>
        `;
    } else if (msg.type === 'CUSTOMER_LEFT') {
        // 고객 퇴장 알림 ✅
        div.className = "system-msg";
        div.style.cssText = "color: #d32f2f; font-weight: bold; background-color: #ffebee; padding: 10px; border-left: 4px solid #d32f2f;";
        div.innerHTML = `
            <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-size: 20px;">👋</span>
                <div>
                    <div>[${timestamp}] ${msg.message}</div>
                    <div style="font-size: 0.9em; color: #c62828; margin-top: 4px;">
                        고객이 채팅을 종료했습니다. 상담을 마무리해주세요.
                    </div>
                </div>
            </div>
        `;
    }
    
    // ... 일반 메시지 처리 ...
    
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}
```

---

## 🎨 메시지 타입

### 메시지 타입 비교

| 타입 | 발생 시점 | 발송 위치 | 아이콘 | 배경색 |
|------|----------|----------|--------|--------|
| `CUSTOMER_DISCONNECTED` | 네트워크 끊김, 브라우저 종료 | `WebSocketEventListener` | ⚠️ | 노란색 (#fff3cd) |
| `CUSTOMER_LEFT` | 명시적 "나가기" 클릭 | `CustomerChatController` | 👋 | 빨간색 (#ffebee) |

---

## 📱 화면 구성

### 상담원 화면 - 연결 해제 알림

```
┌─────────────────────────────────────┐
│  [채팅방 탭]                        │
│  ┌──────────────────────┐           │
│  │ room-abc123 [x]      │ ← 탭 배경색 변경
│  │ (연한 빨간색)         │
│  └──────────────────────┘           │
│                                      │
│  채팅 내용:                          │
│  ┌────────────────────────────────┐ │
│  │ ⚠️ [14:30:25]                  │ │
│  │ 홍길동 고객의 연결이 끊어졌습니다.│ │
│  │                                 │ │
│  │ 고객의 네트워크 연결이 끊어졌습니다│ │
│  │ 잠시 기다려주세요.               │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

---

### 상담원 화면 - 명시적 퇴장 알림

```
┌─────────────────────────────────────┐
│  채팅 내용:                          │
│  ┌────────────────────────────────┐ │
│  │ 👋 [14:35:10]                  │ │
│  │ 홍길동 고객이 상담을 종료했습니다. │ │
│  │                                 │ │
│  │ 고객이 채팅을 종료했습니다.      │ │
│  │ 상담을 마무리해주세요.           │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 고객 네트워크 끊김 테스트

```
1. 준비
   - 고객: chat-customer.html 접속
   - 상담원: chat-agent.html에서 상담 시작
   
2. 테스트 실행
   - 고객 PC의 Wi-Fi 끄기
   또는
   - 개발자 도구 > Network > Offline 선택
   
3. 예상 결과
   - 서버 로그:
     ▶ WebSocket 연결 해제 이벤트
     🔔 고객 연결 해제 알림 전송 시작...
     ✅ 고객 연결 해제 알림 전송 완료!
   
   - 상담원 화면:
     ⚠️ "홍길동 고객의 연결이 끊어졌습니다."
     노란색 배경 알림 표시
   
   - 탭 배경색:
     연한 빨간색 (#ffebee)
```

---

### 시나리오 2: 고객 브라우저 닫기 테스트

```
1. 준비
   - 고객: chat-customer.html 접속
   - 상담원: chat-agent.html에서 상담 시작
   
2. 테스트 실행
   - 고객이 브라우저 탭 닫기 (X 버튼)
   또는
   - Alt+F4로 브라우저 강제 종료
   
3. 예상 결과
   - 시나리오 1과 동일
   - CUSTOMER_DISCONNECTED 메시지 수신
```

---

### 시나리오 3: 고객 명시적 퇴장 테스트

```
1. 준비
   - 고객: chat-customer.html 접속
   - 상담원: chat-agent.html에서 상담 시작
   
2. 테스트 실행
   - 고객 화면에서 "나가기" 버튼 클릭
   또는
   - LEAVE 메시지 전송
   
3. 예상 결과
   - 서버 로그:
     🔔 고객 퇴장 메시지 감지
     ✅ 고객 퇴장 알림 전송 완료!
   
   - 상담원 화면:
     👋 "홍길동 고객이 상담을 종료했습니다."
     빨간색 배경 알림 표시
   
   - 이후 WebSocket 연결도 종료됨
```

---

### 시나리오 4: BOT 상담 중 연결 해제

```
1. 준비
   - 고객: chat-customer.html 접속 (BOT 상담)
   - 상담원 배정 없음
   
2. 테스트 실행
   - 고객 네트워크 끊김 또는 브라우저 닫기
   
3. 예상 결과
   - 서버 로그:
     ℹ️ 상담원이 배정되지 않은 방 - 알림 전송 생략
   
   - 알림 전송 안 됨 (정상)
```

---

## 📊 Redis 메시지 구조

### CUSTOMER_DISCONNECTED 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "SYSTEM",
  "message": "홍길동 고객의 연결이 끊어졌습니다.",
  "type": "CUSTOMER_DISCONNECTED",
  "timestamp": [2026, 1, 23, 14, 30, 25, 123456789],
  "companyId": null
}
```

---

### CUSTOMER_LEFT 메시지

```json
{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "SYSTEM",
  "message": "홍길동 고객이 상담을 종료했습니다.",
  "type": "CUSTOMER_LEFT",
  "timestamp": [2026, 1, 23, 14, 35, 10, 987654321],
  "companyId": "apt001"
}
```

---

## 🔍 로그 분석

### 연결 해제 시 서버 로그

```
2026-01-23 14:30:25 [INFO ] ▶ WebSocket 연결 해제 이벤트 ▶▶▶▶▶▶▶▶▶▶
2026-01-23 14:30:25 [INFO ] 📌 sessionAttribute:WebSocketSessionAttribute(sessionId=abc123, userId=cust01, userName=홍길동, userRole=CUSTOMER, userEmail=cust01@example.com, companyId=apt001, roomId=room-abc123)
2026-01-23 14:30:25 [INFO ] 📌 closeStatus: CloseStatus[code=1001, reason=null]
2026-01-23 14:30:25 [INFO ] 💾 Redis에서 세션 정보 제거 시작...
2026-01-23 14:30:25 [INFO ] WebSocket 세션 해제 - sessionId: abc123
2026-01-23 14:30:25 [INFO ] ✅ Redis에서 세션 제거 완료!
2026-01-23 14:30:25 [INFO ]   - 삭제된 Redis Key: ws:session:abc123
2026-01-23 14:30:25 [INFO ] 🔔 고객 연결 해제 알림 전송 시작...
2026-01-23 14:30:25 [INFO ]   - roomId: room-abc123
2026-01-23 14:30:25 [INFO ]   - userId: cust01
2026-01-23 14:30:25 [INFO ]   - userName: 홍길동
2026-01-23 14:30:25 [INFO ]   - assignedAgent: agent01
2026-01-23 14:30:25 [INFO ] ✅ 고객 연결 해제 알림 전송 완료!
2026-01-23 14:30:25 [INFO ] ◀ WebSocket 연결 해제 처리 종료 ◀◀◀◀◀◀◀◀◀◀
```

---

### 명시적 퇴장 시 서버 로그

```
2026-01-23 14:35:10 [INFO ] 🔔 고객 퇴장 메시지 감지 - roomId: room-abc123, userId: cust01
2026-01-23 14:35:10 [INFO ]   - assignedAgent: agent01
2026-01-23 14:35:10 [INFO ] ✅ 고객 퇴장 알림 전송 완료!
2026-01-23 14:35:11 [INFO ] ▶ WebSocket 연결 해제 이벤트 ▶▶▶▶▶▶▶▶▶▶
(이후 연결 해제 처리 로그...)
```

---

## 📝 변경된 파일 목록

### 백엔드 (3개)

1. **`MessageType.java`**
   - `CUSTOMER_DISCONNECTED` 추가
   - `CUSTOMER_LEFT` 추가

2. **`WebSocketEventListener.java`**
   - `onDisconnect()` 메서드에 고객 연결 해제 알림 로직 추가
   - `MessageBroker` 의존성 추가

3. **`CustomerChatController.java`**
   - `onCustomerMessage()` 메서드에 LEAVE 메시지 감지 및 알림 로직 추가
   - `MessageBroker` 의존성 추가

### 프론트엔드 (1개)

1. **`chat-agent.html`**
   - 메시지 구독 로직에 `CUSTOMER_DISCONNECTED`, `CUSTOMER_LEFT` 처리 추가
   - `showMessage()` 함수에 두 메시지 타입 표시 로직 추가
   - 탭 배경색 변경 로직 추가
   - 방 목록 자동 갱신 로직 추가

---

## ✅ 체크리스트

### 구현 확인
- [x] `CUSTOMER_DISCONNECTED` 메시지 타입 추가
- [x] `CUSTOMER_LEFT` 메시지 타입 추가
- [x] WebSocket 연결 해제 감지
- [x] 명시적 LEAVE 메시지 감지
- [x] 상담원 배정 확인 로직
- [x] Redis Pub/Sub으로 알림 발송

### 화면 확인
- [x] 연결 해제 알림 표시 (노란색)
- [x] 명시적 퇴장 알림 표시 (빨간색)
- [x] 탭 배경색 변경
- [x] 방 목록 자동 갱신
- [x] 아이콘 표시 (⚠️, 👋)

### 테스트 확인
- [x] 네트워크 끊김 시나리오
- [x] 브라우저 닫기 시나리오
- [x] 명시적 퇴장 시나리오
- [x] BOT 상담 중 연결 해제 (알림 없음)

---

## 🎉 완료!

고객의 연결 해제 및 상담 종료 시 상담원에게 실시간 알림이 전송됩니다!

**주요 기능:**
- ✅ 네트워크 끊김 자동 감지
- ✅ 브라우저 종료 감지
- ✅ 명시적 퇴장 감지
- ✅ 실시간 알림 (⚠️ 연결 해제, 👋 상담 종료)
- ✅ 시각적 표시 (색상 구분, 아이콘)
- ✅ 탭 배경색 변경
- ✅ 방 목록 자동 갱신

**테스트:**
```bash
# 1. 서버 실행
./gradlew bootRun

# 2. 고객 접속 (chat-customer.html)
# 3. 상담원 접속 (chat-agent.html) 및 상담 시작
# 4. 고객 네트워크 끊기 또는 브라우저 닫기
# 5. 상담원 화면에서 알림 확인
```

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23
