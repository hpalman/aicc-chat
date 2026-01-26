# 상담원 상담 종료 시 BOT 모드 복귀 가이드

## 📋 개요

상담원이 "상담 종료" 버튼을 클릭하면 고객이 완전히 상담을 종료하는 것이 아니라, **다시 챗봇과 대화할 수 있도록 BOT 모드로 복귀**하도록 변경했습니다.

---

## 🎯 변경 사항

### Before (상담원 종료 시 방 닫힘) ❌

```
상담원 "상담 종료" 클릭
↓
방 상태: AGENT → CLOSED
↓
고객 화면: "상담 종료됨" (더 이상 채팅 불가)
↓
고객이 새로 "상담 시작" 버튼을 눌러야 함
```

**문제점:**
- 고객이 추가 질문을 하려면 새로운 방을 만들어야 함
- 상담 이력이 단절됨
- 사용자 경험이 불편함

---

### After (상담원 종료 시 BOT 복귀) ✅

```
상담원 "상담 종료" 클릭
↓
방 상태: AGENT → BOT
↓
시스템 메시지: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
↓
고객 화면: "상담원 연결" 버튼 활성화
↓
고객이 동일한 방에서 계속 챗봇과 대화 가능 ✅
```

**장점:**
- ✅ 동일한 방에서 상담 연속성 유지
- ✅ 고객이 추가 질문을 챗봇에게 바로 할 수 있음
- ✅ 필요하면 다시 "상담원 연결" 버튼으로 상담원 요청 가능
- ✅ 상담 이력이 하나의 방에 모두 저장됨

---

## 🔧 백엔드 변경 사항

### 1. AgentChatController.deleteRoom() 메서드 수정

**파일:** `AgentChatController.java`

#### Before (CLOSED 상태로 변경)

```java
@DeleteMapping("/rooms/{roomId}")
public ResponseEntity<?> deleteRoom(@PathVariable String roomId, ...) {
    // ...
    
    // 상담 종료 메시지
    ChatMessage notice = ChatMessage.builder()
            .message("상담원에 의해 상담이 종료되었습니다.")
            .type(MessageType.LEAVE)  // ❌ LEAVE 타입
            .build();
    
    messageBroker.publish(notice);
    
    // 방 상태를 CLOSED로 변경
    roomRepository.setRoutingMode(roomId, "CLOSED");  // ❌ CLOSED
    
    // DB에 종료 기록
    chatSessionService.updateSessionStatus(roomId, "CLOSED");
    chatSessionService.endSession(roomId);
    
    // ...
}
```

#### After (BOT 상태로 복귀)

```java
@DeleteMapping("/rooms/{roomId}")
public ResponseEntity<?> deleteRoom(@PathVariable String roomId, ...) {
    // ...
    
    String currentMode = roomRepository.getRoutingMode(roomId);
    
    // 이미 종료된 상태(CLOSED)면 완전 삭제
    if ("CLOSED".equals(currentMode)) {
        log.info("Permanently deleting closed room: {}", roomId);
        roomRepository.deleteRoom(roomId);
    } else {
        // 상담원이 상담 종료 시 BOT 모드로 복귀 ✅
        log.info("Agent ending consultation, switching room {} back to BOT mode", roomId);
        
        LocalDateTime now = LocalDateTime.now();
        
        // 상담 종료 알림 메시지
        ChatMessage notice = ChatMessage.builder()
                .roomId(roomId)
                .sender("System")
                .senderRole(UserRole.BOT)
                .message("상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다.")
                .type(MessageType.TALK)  // ✅ TALK 타입 (LEAVE 아님)
                .timestamp(now)
                .build();
        
        messageBroker.publish(notice);
        
        // 방 상태를 BOT으로 변경 ✅
        roomRepository.setRoutingMode(roomId, "BOT");
        
        // 상담원 배정 해제 ✅
        roomRepository.setAssignedAgent(roomId, null);
        
        // DB 상태 업데이트 (CLOSED 아님, BOT) ✅
        chatSessionService.updateSessionStatus(roomId, "BOT");
        
        // 이력에 저장
        ChatHistory chatHistory = ChatHistory.builder()
                .roomId(roomId)
                .senderId("SYSTEM")
                .senderName("System")
                .senderRole("SYSTEM")
                .message(notice.getMessage())
                .messageType("TALK")  // ✅ TALK
                .createdAt(now)
                .build();
        chatHistoryService.saveChatHistory(chatHistory);
    }
    
    roomUpdateBroadcaster.broadcastRoomList();
    return ResponseEntity.ok().build();
}
```

**주요 변경점:**
1. **메시지 타입**: `MessageType.LEAVE` → `MessageType.TALK`
   - LEAVE는 완전 종료 의미, TALK는 일반 시스템 메시지
2. **방 상태**: `CLOSED` → `BOT`
   - 고객이 계속 챗봇과 대화 가능
3. **상담원 배정 해제**: `setAssignedAgent(roomId, null)`
   - Redis에서 assignedAgent 키 삭제
4. **DB 상태**: `CLOSED` → `BOT`
   - 세션이 완전 종료되지 않고 BOT 모드로 전환
5. **메시지 내용**: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."

---

### 2. RedisRoomRepository.setAssignedAgent() 메서드 수정

**파일:** `RedisRoomRepository.java`

#### Before (null 처리 없음)

```java
@Override
public void setAssignedAgent(String roomId, String agentName) {
    if (roomId != null && agentName != null) {
        redisTemplate.opsForValue().set(
            ROOM_KEY_PREFIX + roomId + ":assignedAgent", 
            agentName
        );
    }
}
```

#### After (null이면 키 삭제)

```java
@Override
public void setAssignedAgent(String roomId, String agentName) {
    if (roomId != null) {
        if (agentName != null) {
            // 상담원 배정 ✅
            redisTemplate.opsForValue().set(
                ROOM_KEY_PREFIX + roomId + ":assignedAgent", 
                agentName
            );
        } else {
            // agentName이 null이면 키 삭제 (상담원 배정 해제) ✅
            redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        }
    }
}
```

**변경 이유:**
- 상담원 종료 시 `setAssignedAgent(roomId, null)`을 호출하여 배정 정보를 삭제
- Redis에서 assignedAgent 키가 삭제되면 방이 다시 "배정 안 됨" 상태가 됨

---

## 🎨 프론트엔드 변경 사항

### chat-customer.html 수정

**파일:** `chat-customer.html`

#### showMessage() 함수에 조건 추가

```javascript
function showMessage(message) {
    const chatBox = document.getElementById("chat-box");
    const div = document.createElement("div");
    
    const timestamp = formatTimestamp(message.timestamp);

    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        div.className = "system";
        div.innerText = `[${timestamp}] ${message.message}`;
        
        if (message.type === 'LEAVE') {
            updateHandoffButtons('CLOSED');
            
            // 자동 종료 처리 (타임아웃)
            if (message.sender === 'System' && message.message.includes("자동 종료")) {
                // ... 기존 로직
            }
        }
    } else {
        const isMe = message.senderRole === 'CUSTOMER';
        div.className = isMe ? "message my" : "message other";
        div.innerHTML = `...`;
        
        // 시스템 메시지로 상담원 연결 확인
        if (message.sender === 'System' && message.message.includes("상담원과 연결되었습니다.")) {
            updateHandoffButtons('AGENT');
        }
        
        // 상담원 연결 취소 확인
        if (message.sender === 'System' && message.message.includes("상담원 연결 요청을 취소하였습니다.")) {
            updateHandoffButtons('BOT');
        }
        
        // ✅ 상담원 상담 종료 → BOT 모드 복귀 확인 (NEW)
        if (message.sender === 'System' && message.message.includes("상담원과의 상담이 종료되었습니다")) {
            updateHandoffButtons('BOT');
        }
    }
    
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}
```

**추가된 조건:**
```javascript
// 상담원 상담 종료 → BOT 모드 복귀 확인
if (message.sender === 'System' && message.message.includes("상담원과의 상담이 종료되었습니다")) {
    updateHandoffButtons('BOT');
}
```

**동작:**
- 서버에서 "상담원과의 상담이 종료되었습니다" 메시지를 받으면
- `updateHandoffButtons('BOT')` 호출
- "상담원 연결" 버튼이 다시 활성화됨

---

## 📊 데이터 흐름

### 상담원 상담 종료 시 전체 흐름

```
1. 상담원 화면 (chat-agent.html)
   ↓
   "상담 종료" 버튼 클릭
   ↓
2. AgentChatController.deleteRoom()
   ↓
   방 상태 확인: AGENT (현재 상담 중)
   ↓
3. 시스템 메시지 생성 및 발송
   - 메시지: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
   - 타입: TALK
   - 발신자: System
   ↓
4. Redis 상태 변경
   - roomRepository.setRoutingMode(roomId, "BOT")
   - roomRepository.setAssignedAgent(roomId, null)
   ↓
5. PostgreSQL 상태 업데이트
   - chatSessionService.updateSessionStatus(roomId, "BOT")
   - chatHistory에 종료 메시지 저장
   ↓
6. 방 목록 브로드캐스트
   - roomUpdateBroadcaster.broadcastRoomList()
   ↓
7. 고객 화면 (chat-customer.html)
   ↓
   WebSocket으로 메시지 수신
   ↓
   showMessage() 함수 실행
   ↓
   메시지에 "상담원과의 상담이 종료되었습니다" 포함 확인
   ↓
   updateHandoffButtons('BOT') 호출
   ↓
8. 고객 화면 UI 변경
   - "상담원 연결" 버튼 활성화
   - 고객이 챗봇과 대화 가능
   - 원하면 다시 "상담원 연결" 클릭 가능 ✅
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 상담원 종료 후 BOT 복귀

```
1. 고객 로그인 및 상담 시작
   - http://localhost:28070/chat-customer.html
   - "상담 시작" 클릭 → 챗봇과 대화 시작

2. "상담원 연결" 클릭
   - 버튼 상태: "상담원 연결 중"
   - 방 상태: BOT → WAITING

3. 상담원 로그인 및 상담 수락
   - http://localhost:28070/chat-agent.html
   - 대기 중인 방 클릭 → "상담을 수락하시겠습니까?" → 확인
   - 방 상태: WAITING → AGENT

4. 상담원-고객 채팅
   - 상담원: "무엇을 도와드릴까요?"
   - 고객: "배송 문의 드립니다"
   - 상담원: "주문번호 알려주세요"
   - 고객: "ABC123"

5. 상담원 "상담 종료" 클릭 ✅
   - 상담원 화면: "상담 종료" 버튼 클릭
   - 확인 대화상자: "상담을 종료하시겠습니까?" → 확인

6. 고객 화면 확인 ✅
   - 시스템 메시지 표시: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
   - "상담원 연결" 버튼 활성화 확인
   - 방 상태: AGENT → BOT

7. 고객이 챗봇과 대화 계속 ✅
   - 고객: "배송 조회 방법 알려주세요"
   - 챗봇: "주문번호를 입력하시면 배송 상태를 확인하실 수 있습니다"

8. DB 확인 ✅
   ```sql
   SELECT 
       sender_name,
       message,
       created_at
   FROM chat_history
   WHERE room_id = 'room-abc123'
   ORDER BY created_at DESC;
   
   -- 결과:
   -- Bot    | 주문번호를 입력하시면...        | 2026-01-26 15:10:30
   -- 홍길동  | 배송 조회 방법 알려주세요        | 2026-01-26 15:10:25
   -- System | 상담원과의 상담이 종료되었습니다 | 2026-01-26 15:10:15
   -- 김상담  | 주문번호 알려주세요             | 2026-01-26 15:09:50
   -- 홍길동  | 배송 문의 드립니다              | 2026-01-26 15:09:45
   -- ✅ 모든 대화가 하나의 방에 저장됨
   ```

9. Redis 확인 ✅
   ```bash
   redis-cli
   > GET chat:room:room-abc123:mode
   "BOT"  # ✅ BOT 모드로 복귀
   
   > GET chat:room:room-abc123:assignedAgent
   (nil)  # ✅ 상담원 배정 해제됨
   ```

10. PostgreSQL 확인 ✅
    ```sql
    SELECT 
        room_id,
        status,
        agent_name,
        ended_at
    FROM chat_session
    WHERE room_id = 'room-abc123';
    
    -- 결과:
    -- room-abc123 | BOT | NULL | NULL
    -- ✅ 상태: BOT, 상담원: 없음, 종료 시간: NULL (아직 세션 진행 중)
    ```
```

---

### 시나리오 2: 상담원 재연결 가능

```
1. 시나리오 1 완료 후 상태
   - 고객 화면: "상담원 연결" 버튼 활성화
   - 방 상태: BOT

2. 고객이 다시 "상담원 연결" 클릭 ✅
   - 버튼 상태: "상담원 연결 중"
   - 방 상태: BOT → WAITING

3. 상담원이 다시 상담 수락 ✅
   - 동일한 방에서 다시 상담 시작
   - 이전 대화 이력 모두 유지됨

4. 결과 ✅
   - 고객과 상담원이 이전 대화 내용을 보면서 상담 가능
   - 모든 대화가 하나의 roomId에 기록됨
```

---

## 💡 장점 정리

### 1. 사용자 경험 향상
- ✅ 고객이 동일한 방에서 챗봇과 계속 대화 가능
- ✅ 추가 질문이 있으면 챗봇에게 바로 물어볼 수 있음
- ✅ 필요하면 다시 "상담원 연결" 버튼으로 재연결 가능

### 2. 상담 연속성 유지
- ✅ 모든 대화가 하나의 roomId에 저장됨
- ✅ 고객-봇-상담원-봇 전환 과정이 모두 기록됨
- ✅ 상담 이력 추적이 용이함

### 3. 데이터 일관성
- ✅ chat_session 테이블에 세션이 계속 유지됨
- ✅ chat_history 테이블에 모든 대화가 순서대로 기록됨
- ✅ 분석 및 리포트 생성이 용이함

### 4. 유연한 상담 흐름
- ✅ 챗봇 → 상담원 → 챗봇 → 상담원 (여러 번 전환 가능)
- ✅ 상담원이 부담 없이 "종료" 가능 (고객은 챗봇과 계속 대화)
- ✅ 상담원 부재 시에도 고객이 챗봇으로 문제 해결 가능

---

## 📝 수정된 파일 목록

### 백엔드 (2개)
- [x] `AgentChatController.java` - deleteRoom() 메서드 수정 (BOT 복귀 로직)
- [x] `RedisRoomRepository.java` - setAssignedAgent() 메서드 수정 (null 처리)

### 프론트엔드 (1개)
- [x] `chat-customer.html` - showMessage() 함수에 BOT 복귀 감지 로직 추가

---

## ✅ 컴파일 성공

```bash
.\gradlew compileJava

BUILD SUCCESSFUL in 20s
```

---

## 🔄 상태 전환 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                     채팅방 상태 전환                          │
└─────────────────────────────────────────────────────────────┘

고객: "상담 시작"
    ↓
┌───────┐
│  BOT  │ ← 챗봇과 대화
└───────┘
    ↓ 고객: "상담원 연결"
┌─────────┐
│ WAITING │ ← 상담원 대기
└─────────┘
    ↓ 상담원: "상담 수락"
┌───────┐
│ AGENT │ ← 상담원과 대화
└───────┘
    ↓ 상담원: "상담 종료" ✅
┌───────┐
│  BOT  │ ← 다시 챗봇과 대화 가능! ✅
└───────┘
    ↓ 고객: "상담원 연결" (재연결 가능) ✅
┌─────────┐
│ WAITING │ ← 다시 상담원 대기
└─────────┘
    ↓ 상담원: "상담 수락"
┌───────┐
│ AGENT │ ← 다시 상담원과 대화 (동일한 방) ✅
└───────┘
```

---

## 🎉 완료

상담원이 "상담 종료"를 클릭하면 고객이 **동일한 방에서 다시 챗봇과 대화**할 수 있습니다!

**주요 개선사항:**
- ✅ 방 상태: AGENT → BOT (CLOSED 아님)
- ✅ 메시지: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
- ✅ 고객 화면: "상담원 연결" 버튼 활성화
- ✅ 고객이 동일한 방에서 챗봇과 계속 대화 가능
- ✅ 필요하면 다시 "상담원 연결" 버튼으로 재연결 가능
- ✅ 모든 대화 이력이 하나의 roomId에 저장됨
