# 서버 타임스탬프 구현 가이드

## 📋 개요

메시지 타임스탬프를 클라이언트가 아닌 서버에서 생성하도록 변경했습니다. 이를 통해 모든 사용자가 동일한 서버 시간을 기준으로 메시지를 확인할 수 있습니다.

---

## 🎯 변경 사항

### Before (클라이언트 타임스탬프)
```
클라이언트 → 메시지 전송
↓
서버 → 메시지 수신
↓
서버 → 다른 클라이언트에게 브로드캐스트
↓
각 클라이언트 → 자신의 로컬 시간으로 타임스탬프 생성 ❌
```

**문제점:**
- 각 클라이언트의 시스템 시간이 다르면 다른 타임스탬프가 표시됨
- 서버 DB에 저장된 시간과 화면 표시 시간이 일치하지 않음

### After (서버 타임스탬프)
```
클라이언트 → 메시지 전송
↓
서버 → 메시지 수신 + 타임스탬프 생성 ✅
↓
서버 → 메시지 + 타임스탬프를 DB에 저장
↓
서버 → 다른 클라이언트에게 브로드캐스트
↓
각 클라이언트 → 서버 타임스탬프 사용 ✅
```

**장점:**
- 모든 사용자가 동일한 시간 기준으로 메시지 확인
- 서버 DB와 화면 표시 시간이 일치
- 서버 로그와 메시지 타임스탬프 일치

---

## 🔧 백엔드 변경 사항

### 1. ChatMessage에 timestamp 필드 추가

**파일:** `ChatMessage.java`

```java
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private String roomId;
    private String sender;
    private UserRole senderRole;
    private String message;
    private MessageType type;
    private String companyId;
    private LocalDateTime timestamp; // ✅ 추가
}
```

---

### 2. CustomerChatController 수정

**파일:** `CustomerChatController.java`

```java
@MessageMapping("/customer/chat")
public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    // ✅ 서버에서 메시지 수신 시간 설정
    message.setTimestamp(LocalDateTime.now());
    
    // ... 기존 로직
    
    // DB 저장 시 서버 타임스탬프 사용
    ChatHistory chatHistory = ChatHistory.builder()
            .roomId(message.getRoomId())
            .senderId(userId)
            .senderName(message.getSender())
            .senderRole(message.getSenderRole().name())
            .message(message.getMessage())
            .messageType(message.getType().name())
            .companyId(message.getCompanyId())
            .createdAt(message.getTimestamp()) // ✅ 서버 타임스탬프 사용
            .build();
    chatHistoryService.saveChatHistory(chatHistory);
    
    // ... 나머지 로직
}
```

---

### 3. AgentChatController 수정

**파일:** `AgentChatController.java`

```java
@MessageMapping("/agent/chat")
public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    // ✅ 서버에서 메시지 수신 시간 설정
    message.setTimestamp(LocalDateTime.now());
    
    // ... 기존 로직
    
    // DB 저장 시 서버 타임스탬프 사용
    ChatHistory chatHistory = ChatHistory.builder()
            .roomId(message.getRoomId())
            .senderId(userId)
            .senderName(message.getSender())
            .senderRole(message.getSenderRole().name())
            .message(message.getMessage())
            .messageType(message.getType().name())
            .companyId(message.getCompanyId())
            .createdAt(message.getTimestamp()) // ✅ 서버 타임스탬프 사용
            .build();
    chatHistoryService.saveChatHistory(chatHistory);
}

@PostMapping("/rooms/{roomId}/assign")
public ResponseEntity<?> assignAgent(@PathVariable String roomId, ...) {
    // ✅ 시스템 메시지에도 타임스탬프 설정
    LocalDateTime now = LocalDateTime.now();
    
    ChatMessage notice = ChatMessage.builder()
            .roomId(roomId)
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message(userInfo.getUserName() + " 상담원과 연결되었습니다.")
            .type(MessageType.TALK)
            .timestamp(now) // ✅ 서버 타임스탬프 설정
            .build();
    
    messageBroker.publish(notice);
    
    // DB 저장 시에도 동일한 타임스탬프 사용
    ChatHistory chatHistory = ChatHistory.builder()
            .roomId(roomId)
            .senderId("SYSTEM")
            .senderName("System")
            .senderRole("SYSTEM")
            .message(notice.getMessage())
            .messageType("TALK")
            .createdAt(now) // ✅ 동일한 타임스탬프
            .build();
}

@DeleteMapping("/rooms/{roomId}")
public ResponseEntity<?> deleteRoom(@PathVariable String roomId, ...) {
    // ✅ 종료 메시지에도 타임스탬프 설정
    LocalDateTime now = LocalDateTime.now();
    
    ChatMessage notice = ChatMessage.builder()
            .roomId(roomId)
            .sender("System")
            .senderRole(UserRole.BOT)
            .message("상담원에 의해 상담이 종료되었습니다.")
            .type(MessageType.LEAVE)
            .timestamp(now) // ✅ 서버 타임스탬프 설정
            .build();
}
```

---

### 4. MiChatRoutingStrategy 수정

**파일:** `MiChatRoutingStrategy.java`

```java
@Override
public void handleMessage(String roomId, ChatMessage message) {
    // ... 봇 응답 처리
    
    () -> {
        String responseText = fullResponse.toString();
        if (!responseText.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(); // ✅ 서버 타임스탬프
            
            ChatMessage botMessage = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("Bot")
                    .senderRole(UserRole.BOT)
                    .message(responseText)
                    .type(MessageType.TALK)
                    .timestamp(now) // ✅ 서버 타임스탬프 설정
                    .build();
            
            messageBroker.publish(botMessage);
            
            // DB 저장 시에도 동일한 타임스탬프
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(roomId)
                    .senderId("BOT")
                    .senderName("Bot")
                    .senderRole("BOT")
                    .message(responseText)
                    .messageType("TALK")
                    .companyId(message.getCompanyId())
                    .createdAt(now) // ✅ 동일한 타임스탬프
                    .build();
            chatHistoryService.saveChatHistory(chatHistory);
        }
    }
}

@Override
public void onRoomCreated(ChatRoom room) {
    LocalDateTime now = LocalDateTime.now(); // ✅ 서버 타임스탬프
    
    ChatMessage welcome = ChatMessage.builder()
            .roomId(room.getRoomId())
            .sender("Bot")
            .senderRole(UserRole.BOT)
            .message("안녕하세요! 무엇을 도와드릴까요?")
            .type(MessageType.TALK)
            .timestamp(now) // ✅ 서버 타임스탬프 설정
            .build();
}

private void switchToAgentMode(String roomId) {
    LocalDateTime now = LocalDateTime.now(); // ✅ 서버 타임스탬프
    
    ChatMessage notice = ChatMessage.builder()
            .roomId(roomId)
            .sender("System")
            .senderRole(UserRole.BOT)
            .message("상담원 연결을 요청하였습니다.")
            .type(MessageType.TALK)
            .timestamp(now) // ✅ 서버 타임스탬프 설정
            .build();
}
```

---

### 5. RoomCleanupService 수정

**파일:** `RoomCleanupService.java`

```java
import java.time.LocalDateTime; // ✅ import 추가

private void notifyRoomTimeout(ChatRoom room) {
    LocalDateTime now = LocalDateTime.now(); // ✅ 서버 타임스탬프
    
    ChatMessage timeoutMessage = ChatMessage.builder()
            .roomId(room.getRoomId())
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
            .type(MessageType.LEAVE)
            .companyId(null)
            .timestamp(now) // ✅ 서버 타임스탬프 설정
            .build();
}

private void saveRoomTimeoutToDatabase(ChatRoom room) {
    LocalDateTime now = LocalDateTime.now(); // ✅ 서버 타임스탬프
    
    ChatHistory timeoutHistory = ChatHistory.builder()
            .roomId(room.getRoomId())
            .senderId("system")
            .senderName("System")
            .senderRole("SYSTEM")
            .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
            .messageType("LEAVE")
            .companyId(null)
            .createdAt(now) // ✅ 서버 타임스탬프 사용
            .build();
}
```

---

## 🎨 프론트엔드 변경 사항

### 1. chat-customer.html 수정

```javascript
/**
 * 서버에서 받은 타임스탬프를 "YYYY-MM-DD HH:mm:ss" 형식으로 변환
 * @param {string|array} timestamp - ISO 형식 문자열 또는 배열
 */
function formatTimestamp(timestamp) {
    if (!timestamp) {
        return getCurrentTimestamp(); // fallback to client time
    }
    
    try {
        let date;
        
        // 서버에서 배열 형식으로 온 경우 (예: [2026, 1, 26, 14, 30, 45])
        if (Array.isArray(timestamp)) {
            const [year, month, day, hour, minute, second] = timestamp;
            date = new Date(year, month - 1, day, hour, minute, second);
        } 
        // ISO 문자열 형식인 경우 (예: "2026-01-26T14:30:45")
        else if (typeof timestamp === 'string') {
            date = new Date(timestamp);
        } else {
            return getCurrentTimestamp(); // fallback
        }
        
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    } catch (e) {
        console.error('Failed to format timestamp:', timestamp, e);
        return getCurrentTimestamp(); // fallback to client time
    }
}

/**
 * 클라이언트 현재 시간 (fallback용)
 */
function getCurrentTimestamp() {
    const now = new Date();
    // ... 기존 코드
}

function showMessage(message) {
    const chatBox = document.getElementById("chat-box");
    const div = document.createElement("div");
    
    // ✅ 서버에서 받은 타임스탬프 사용 (없으면 클라이언트 시간 사용)
    const timestamp = formatTimestamp(message.timestamp);

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        div.innerText = `[${timestamp}] ${message.message}`;
    } else {
        div.innerHTML = `
            <div class="fw-bold">${message.sender}</div>
            <div class="content">${message.message}</div>
            <div class="timestamp">${timestamp}</div>
        `;
    }
    
    chatBox.appendChild(div);
}
```

### 2. chat-agent.html 수정

**CSS 추가:**

```css
.message .timestamp { 
    font-size: 10px; 
    color: #999; 
    margin-top: 2px; 
}
.message.my .timestamp { 
    text-align: right; 
}
.message.other .timestamp { 
    text-align: left; 
}
```

**JavaScript 수정:**

```javascript
/**
 * 서버에서 받은 타임스탬프를 "YYYY-MM-DD HH:mm:ss" 형식으로 변환
 */
function formatTimestamp(timestamp) {
    if (!timestamp) {
        return getCurrentTimestamp(); // fallback
    }
    
    try {
        let date;
        
        // 배열 형식 처리
        if (Array.isArray(timestamp)) {
            const [year, month, day, hour, minute, second] = timestamp;
            date = new Date(year, month - 1, day, hour, minute, second);
        } 
        // ISO 문자열 형식 처리
        else if (typeof timestamp === 'string') {
            date = new Date(timestamp);
        } else {
            return getCurrentTimestamp();
        }
        
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    } catch (e) {
        console.error('Failed to format timestamp:', timestamp, e);
        return getCurrentTimestamp();
    }
}

/**
 * 클라이언트 현재 시간 (fallback용)
 */
function getCurrentTimestamp() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function showMessage(msg) {
    const chatBox = document.getElementById("chat-box");
    const div = document.createElement("div");
    
    // ✅ 서버에서 받은 타임스탬프 사용
    const timestamp = formatTimestamp(msg.timestamp);

    if (msg.type === 'JOIN') {
        div.className = "system-msg";
        div.innerText = `[${timestamp}] ${msg.message}`;
    } else if (msg.type === 'LEAVE') {
        div.className = "system-msg";
        div.style.color = "red";
        div.innerText = `[${timestamp}] ─── ${msg.message} (상담 종료) ───`;
    } else {
        const isMe = msg.senderRole === 'AGENT';
        div.className = `message ${isMe ? 'my' : 'other'}`;
        div.innerHTML = `
            <div class="fw-bold mb-1" style="font-size:12px">${msg.sender}</div>
            <div class="bubble">${msg.message}</div>
            <div class="timestamp">${timestamp}</div>
        `;
    }
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}
```

---

## 📊 데이터 흐름

### 고객 메시지 전송 시

```
1. 고객 → "안녕하세요" 전송 (타임스탬프 없음)
   ↓
2. CustomerChatController.onCustomerMessage()
   - message.setTimestamp(LocalDateTime.now())  // 2026-01-26T14:30:45
   ↓
3. PostgreSQL 저장
   - chatHistory.createdAt = message.getTimestamp()
   ↓
4. MessageBroker.publish(message)
   - message에 timestamp 포함
   ↓
5. 모든 클라이언트 수신
   - message.timestamp = [2026, 1, 26, 14, 30, 45]
   ↓
6. 클라이언트 표시
   - formatTimestamp([2026, 1, 26, 14, 30, 45])
   - 표시: "2026-01-26 14:30:45"
```

### 봇 응답 시

```
1. MiChatRoutingStrategy.handleMessage()
   ↓
2. AI 봇 응답 수신
   ↓
3. 서버 타임스탬프 생성
   - LocalDateTime now = LocalDateTime.now()
   ↓
4. ChatMessage 생성
   - botMessage.timestamp = now
   ↓
5. PostgreSQL 저장
   - chatHistory.createdAt = now
   ↓
6. 클라이언트에 브로드캐스트
   - timestamp 포함
   ↓
7. 클라이언트 표시
   - 서버 타임스탬프 사용
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 서버 타임스탬프 확인

```
1. 로그인 후 상담 시작
2. 메시지 "테스트" 전송
3. 브라우저 개발자 도구 → Network → WS
4. 수신된 메시지 확인:
   {
     "roomId": "room-abc123",
     "sender": "Bot",
     "message": "...",
     "timestamp": [2026, 1, 26, 14, 30, 45]  ← 서버 타임스탬프
   }
5. 화면에 표시된 타임스탬프: "2026-01-26 14:30:45"
```

### 시나리오 2: DB와 화면 시간 일치 확인

```sql
-- PostgreSQL에서 확인
SELECT 
    sender_name,
    message,
    created_at
FROM chat_history
WHERE room_id = 'room-abc123'
ORDER BY created_at DESC
LIMIT 5;

-- 결과:
-- Bot     | 안녕하세요!  | 2026-01-26 14:28:11
-- 홍길철   | 배송 문의    | 2026-01-26 14:28:45
-- Bot     | 주문번호는? | 2026-01-26 14:28:46

-- 화면 표시와 비교:
-- Bot     | 안녕하세요!  | 2026-01-26 14:28:11 ✅ 일치
-- 홍길철   | 배송 문의    | 2026-01-26 14:28:45 ✅ 일치
-- Bot     | 주문번호는? | 2026-01-26 14:28:46 ✅ 일치
```

### 시나리오 3: 다른 사용자 동일 시간 확인

```
1. 브라우저 A (고객1) 로그인
2. 브라우저 B (고객2) 로그인
3. 상담원이 메시지 전송: "안녕하세요" (14:30:45)
4. 브라우저 A 확인: "2026-01-26 14:30:45" ✅
5. 브라우저 B 확인: "2026-01-26 14:30:45" ✅
6. 시스템 시간이 달라도 동일한 타임스탬프 표시
```

---

## 🔍 타임스탬프 형식

### 서버 → 클라이언트 전송 형식

Jackson(ObjectMapper)이 LocalDateTime을 JSON으로 직렬화하면 배열 형식이 됩니다:

```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "message": "안녕하세요",
  "timestamp": [2026, 1, 26, 14, 30, 45]
}
```

또는 설정에 따라 ISO 문자열 형식:

```json
{
  "roomId": "room-abc123",
  "sender": "Bot",
  "message": "안녕하세요",
  "timestamp": "2026-01-26T14:30:45"
}
```

### 클라이언트에서 변환

```javascript
// 배열 형식 처리
[2026, 1, 26, 14, 30, 45]
↓
new Date(2026, 0, 26, 14, 30, 45)  // month는 0부터 시작
↓
"2026-01-26 14:30:45"

// ISO 문자열 형식 처리
"2026-01-26T14:30:45"
↓
new Date("2026-01-26T14:30:45")
↓
"2026-01-26 14:30:45"
```

---

## 💡 장점 정리

### 1. 시간 일관성
- ✅ 모든 사용자가 동일한 서버 시간 기준
- ✅ 클라이언트 시스템 시간 오차 무시

### 2. DB와 화면 일치
- ✅ PostgreSQL 저장 시간 = 화면 표시 시간
- ✅ 로그 분석 시 정확한 시간 추적

### 3. 디버깅 용이
- ✅ 서버 로그와 메시지 타임스탬프 일치
- ✅ 문제 발생 시 정확한 시간 순서 파악

### 4. Fallback 지원
- ✅ 서버 타임스탬프가 없으면 클라이언트 시간 사용
- ✅ 하위 호환성 유지

---

## 📝 수정된 파일 목록

### 백엔드
- [x] `ChatMessage.java` - timestamp 필드 추가
- [x] `CustomerChatController.java` - 메시지 수신 시 타임스탬프 설정
- [x] `AgentChatController.java` - 메시지 수신 시 타임스탬프 설정
- [x] `MiChatRoutingStrategy.java` - 봇 메시지에 타임스탬프 설정
- [x] `RoomCleanupService.java` - 타임아웃 메시지에 타임스탬프 설정

### 프론트엔드
- [x] `chat-customer.html` - 서버 타임스탬프 사용하도록 수정
  - `formatTimestamp()` 함수 추가
  - `showMessage()` 함수 수정
- [x] `chat-agent.html` - 서버 타임스탬프 사용하도록 수정
  - `formatTimestamp()` 함수 추가
  - `getCurrentTimestamp()` 함수 추가
  - `showMessage()` 함수 수정
  - CSS에 `.message .timestamp` 스타일 추가

---

## ✅ 컴파일 성공

```bash
.\gradlew compileJava

BUILD SUCCESSFUL in 21s
```

---

## 🎉 완료

메시지 타임스탬프가 서버 기준으로 변경되었습니다!

**주요 변경사항:**
- ✅ ChatMessage에 timestamp 필드 추가
- ✅ 모든 메시지 생성 시 서버 타임스탬프 설정
- ✅ DB 저장 시 서버 타임스탬프 사용
- ✅ 클라이언트에서 서버 타임스탬프 표시
- ✅ Fallback 지원 (서버 타임스탬프 없으면 클라이언트 시간)
