# Idle 채팅방 자동 종료 알림 기능 가이드

## 📋 개요

`RoomCleanupService`의 `cleanupIdleRooms()` 메소드가 일정 시간 동안 활동이 없는 채팅방을 정리할 때, 고객에게 자동 종료 알림을 전송하고 데이터베이스에 기록하는 기능이 추가되었습니다.

---

## 🔧 수정된 파일

### 1. `RoomCleanupService.java`
- **위치**: `src/main/java/aicc/chat/service/RoomCleanupService.java`
- **변경 사항**: 
  - `MessageBroker`, `ChatHistoryService`, `ChatSessionService` 의존성 추가
  - `notifyRoomTimeout()` 메소드 추가 - 고객에게 WebSocket 알림 전송
  - `saveRoomTimeoutToDatabase()` 메소드 추가 - DB에 타임아웃 기록 저장

### 2. `websocket-client.html`
- **위치**: `frontend/websocket-client.html`
- **변경 사항**:
  - `showMessage()` 함수 수정
  - 서버로부터 자동 종료 메시지(`자동 종료` 키워드 포함) 수신 시 처리 로직 추가
  - 3초 후 자동으로 WebSocket 연결 해제 및 초기 화면으로 복귀
  - 사용자에게 alert 알림

---

## 🚀 동작 흐름

### 1. Idle 채팅방 감지 (서버)
```java
@Scheduled(fixedRate = 60000) // 매 1분마다 실행
public void cleanupIdleRooms() {
    List<ChatRoom> allRooms = roomRepository.findAllRooms();
    long now = System.currentTimeMillis();
    
    for (ChatRoom room : allRooms) {
        long idleTime = now - room.getLastActivityAt();
        if (idleTime > IDLE_TIMEOUT) { // 기본 10분
            // 처리 로직 실행
        }
    }
}
```

### 2. 고객에게 알림 전송
```java
private void notifyRoomTimeout(ChatRoom room) {
    ChatMessage timeoutMessage = ChatMessage.builder()
        .roomId(room.getRoomId())
        .sender("System")
        .senderRole(UserRole.SYSTEM)
        .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
        .type(MessageType.LEAVE)
        .build();
    
    // WebSocket을 통해 고객에게 전송
    messageBroker.publish(timeoutMessage);
}
```

### 3. 데이터베이스에 기록
```java
private void saveRoomTimeoutToDatabase(ChatRoom room) {
    // 1. 세션 상태를 CLOSED로 업데이트
    chatSessionService.updateSessionStatus(room.getRoomId(), "CLOSED");
    
    // 2. 세션 종료 시간 기록
    chatSessionService.endSession(room.getRoomId());
    
    // 3. 채팅 이력에 타임아웃 메시지 저장
    ChatHistory timeoutHistory = ChatHistory.builder()
        .roomId(room.getRoomId())
        .senderId("system")
        .senderName("System")
        .senderRole("SYSTEM")
        .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
        .messageType("LEAVE")
        .build();
    
    chatHistoryService.saveChatHistory(timeoutHistory);
}
```

### 4. Redis에서 채팅방 삭제
```java
roomRepository.deleteRoom(room.getRoomId());
```

### 5. 상담원에게 채팅방 목록 업데이트
```java
roomUpdateBroadcaster.broadcastRoomList();
```

---

## 💻 프론트엔드 처리 (고객 측)

### showMessage() 함수 수정
```javascript
function showMessage(message) {
    if (message.type === 'JOIN' || message.type === 'LEAVE') {
        // 시스템 메시지 표시
        div.className = "system";
        div.innerText = message.message;
        
        if (message.type === 'LEAVE') {
            updateHandoffButtons('CLOSED');
            
            // 서버에서 자동 종료 메시지를 받은 경우
            if (message.sender === 'System' && message.message.includes("자동 종료")) {
                // 3초 후 자동으로 연결 해제
                setTimeout(() => {
                    // WebSocket 연결 해제
                    if (stompClient !== null) {
                        stompClient.disconnect();
                        stompClient = null;
                    }
                    
                    // UI 초기화
                    currentRoomId = null;
                    document.getElementById("chat-box").innerHTML = "";
                    document.getElementById("chat-page").style.display = "none";
                    document.getElementById("connect-form").style.display = "block";
                    
                    // 사용자 알림
                    alert("장시간 대화가 없어 상담이 종료되었습니다.");
                }, 3000);
            }
        }
    }
    
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}
```

---

## ⚙️ 설정 옵션

### Idle 타임아웃 시간 변경
**파일**: `RoomCleanupService.java`

```java
// 현재 설정: 1분 (테스트용)
private static final long IDLE_TIMEOUT = 1 * 60 * 1000;

// 프로덕션 권장: 10분
private static final long IDLE_TIMEOUT = 10 * 60 * 1000;

// 30분으로 변경하려면
private static final long IDLE_TIMEOUT = 30 * 60 * 1000;
```

### 정리 작업 실행 주기 변경
```java
// 현재 설정: 매 1분마다 실행
@Scheduled(fixedRate = 60000)

// 5분마다 실행하려면
@Scheduled(fixedRate = 300000)

// 30초마다 실행하려면
@Scheduled(fixedRate = 30000)
```

### 자동 종료 대기 시간 변경 (프론트엔드)
**파일**: `websocket-client.html`

```javascript
// 현재 설정: 3초 후 자동 종료
setTimeout(() => {
    // ... 종료 로직
}, 3000);

// 5초로 변경하려면
setTimeout(() => {
    // ... 종료 로직
}, 5000);
```

---

## 📊 데이터베이스 기록

### chat_session 테이블 업데이트
```sql
-- 타임아웃된 세션 조회
SELECT * FROM chat_session 
WHERE status = 'CLOSED' 
  AND ended_at IS NOT NULL
ORDER BY ended_at DESC;
```

**업데이트되는 컬럼:**
- `status`: `'CLOSED'`로 변경
- `ended_at`: 종료 시간 기록
- `updated_at`: 업데이트 시간 기록

### chat_history 테이블 기록
```sql
-- 타임아웃 메시지 조회
SELECT * FROM chat_history 
WHERE sender_role = 'SYSTEM' 
  AND message LIKE '%자동 종료%'
ORDER BY created_at DESC;
```

**저장되는 데이터:**
- `room_id`: 채팅방 ID
- `sender_id`: `"system"`
- `sender_name`: `"System"`
- `sender_role`: `"SYSTEM"`
- `message`: `"장시간 대화가 없어 상담이 자동 종료되었습니다."`
- `message_type`: `"LEAVE"`

---

## 🧪 테스트 시나리오

### 1. 기본 타임아웃 테스트

**준비:**
```java
// IDLE_TIMEOUT을 1분으로 설정 (테스트용)
private static final long IDLE_TIMEOUT = 1 * 60 * 1000;
```

**테스트 절차:**
1. 고객으로 로그인 (`cust01` / `1234`)
2. 상담 시작 (봇과 채팅방 생성)
3. 1분 동안 아무 메시지도 전송하지 않음
4. 1분 후 자동으로 시스템 메시지 수신 확인
   - "장시간 대화가 없어 상담이 자동 종료되었습니다."
5. 3초 후 자동으로 연결 해제 및 초기 화면 복귀 확인
6. Alert 메시지 표시 확인

### 2. 데이터베이스 기록 확인

**PostgreSQL 쿼리:**
```sql
-- 세션 종료 기록 확인
SELECT 
    room_id,
    customer_name,
    status,
    started_at,
    ended_at,
    EXTRACT(EPOCH FROM (ended_at - last_activity_at)) as idle_seconds
FROM chat_session
WHERE status = 'CLOSED'
  AND ended_at IS NOT NULL
ORDER BY ended_at DESC
LIMIT 10;

-- 타임아웃 메시지 확인
SELECT 
    room_id,
    sender_name,
    message,
    created_at
FROM chat_history
WHERE sender_role = 'SYSTEM'
  AND message_type = 'LEAVE'
  AND message LIKE '%자동 종료%'
ORDER BY created_at DESC
LIMIT 10;
```

### 3. 로그 확인

**application.yml 로그 레벨 설정:**
```yaml
logging:
  level:
    aicc.chat.service.RoomCleanupService: debug
```

**확인할 로그:**
```
INFO  - Cleaning up idle room: room-abc123 (Idle for 60123 ms)
INFO  - Timeout notification sent to room: room-abc123
INFO  - Timeout record saved to database for room: room-abc123
```

### 4. WebSocket 메시지 확인

**브라우저 개발자 도구 콘솔:**
```javascript
// WebSocket 메시지 수신 확인
// STOMP 프레임:
MESSAGE
destination:/topic/room/room-abc123
content-type:application/json

{
  "roomId": "room-abc123",
  "sender": "System",
  "senderRole": "SYSTEM",
  "message": "장시간 대화가 없어 상담이 자동 종료되었습니다.",
  "type": "LEAVE",
  "companyId": null
}
```

---

## 🔍 트러블슈팅

### 1. 타임아웃 메시지가 전송되지 않음

**원인:**
- `MessageBroker` 의존성 주입 실패
- WebSocket 연결이 이미 끊어짐

**해결:**
```bash
# 로그 확인
tail -f logs/aicc-chat.log | grep "RoomCleanupService"

# MessageBroker 빈 등록 확인
# RedisOnlyConfig.java에 MessageBroker 빈이 정의되어 있는지 확인
```

### 2. 데이터베이스에 기록되지 않음

**원인:**
- PostgreSQL 연결 실패
- 트랜잭션 롤백

**해결:**
```yaml
# application.yml에서 DB 연결 확인
spring:
  datasource:
    url: jdbc:postgresql://127.0.0.1:5432/postgres
    username: postgres
    password: postgres

# 로그 레벨 상향
logging:
  level:
    aicc.chat.service.impl: debug
    org.springframework.jdbc: debug
```

### 3. 프론트엔드에서 자동 종료 안됨

**원인:**
- 메시지 키워드 불일치
- JavaScript 오류

**해결:**
```javascript
// 브라우저 콘솔에서 확인
console.log('Received message:', message);

// 키워드 확인
if (message.sender === 'System' && message.message.includes("자동 종료")) {
    console.log('Auto-close triggered!');
}
```

### 4. Redis에서 채팅방이 삭제되지 않음

**원인:**
- Redis 연결 실패
- `roomRepository.deleteRoom()` 실패

**해결:**
```bash
# Redis CLI로 확인
redis-cli -p 6379
> KEYS chat:room:*
> SMEMBERS chat:rooms

# 수동 삭제 테스트
> DEL chat:room:room-abc123
> SREM chat:rooms room-abc123
```

---

## 📈 모니터링

### 1. 타임아웃 발생 통계

**쿼리:**
```sql
-- 일별 타임아웃 발생 건수
SELECT 
    DATE(ended_at) as date,
    COUNT(*) as timeout_count,
    AVG(EXTRACT(EPOCH FROM (ended_at - last_activity_at))) as avg_idle_seconds
FROM chat_session
WHERE status = 'CLOSED'
  AND ended_at IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM chat_history 
      WHERE chat_history.room_id = chat_session.room_id 
        AND message LIKE '%자동 종료%'
  )
GROUP BY DATE(ended_at)
ORDER BY date DESC;
```

### 2. 평균 Idle 시간 분석

**쿼리:**
```sql
-- Idle 시간 분포
SELECT 
    CASE 
        WHEN EXTRACT(EPOCH FROM (ended_at - last_activity_at)) < 300 THEN '0-5분'
        WHEN EXTRACT(EPOCH FROM (ended_at - last_activity_at)) < 600 THEN '5-10분'
        WHEN EXTRACT(EPOCH FROM (ended_at - last_activity_at)) < 1800 THEN '10-30분'
        ELSE '30분 이상'
    END as idle_range,
    COUNT(*) as count
FROM chat_session
WHERE status = 'CLOSED'
  AND ended_at IS NOT NULL
GROUP BY idle_range
ORDER BY idle_range;
```

---

## 🎯 권장 사항

### 1. 프로덕션 환경 설정
```java
// RoomCleanupService.java
private static final long IDLE_TIMEOUT = 10 * 60 * 1000; // 10분

@Scheduled(fixedRate = 60000) // 1분마다 체크
```

### 2. 사용자 경험 개선
- 타임아웃 5분 전에 경고 메시지 전송 (추가 기능으로 구현 가능)
- 자동 종료 전 "계속하시겠습니까?" 확인 메시지 (선택사항)

### 3. 로그 보관
```yaml
logging:
  file:
    name: logs/room-cleanup.log
    max-size: 10MB
    max-history: 30
```

### 4. 알림 커스터마이징
```java
// RoomCleanupService.java
// 메시지를 상황에 맞게 변경 가능
.message("장시간 대화가 없어 상담이 자동 종료되었습니다.")

// 예시:
.message(String.format("%d분간 대화가 없어 상담이 종료되었습니다.", 
         IDLE_TIMEOUT / 60000))
```

---

## 📝 추가 개선 사항 (선택)

### 1. 타임아웃 전 경고 메시지
```java
// RoomCleanupService에 추가
private static final long WARNING_TIMEOUT = 8 * 60 * 1000; // 8분 (10분 중 8분)

// cleanupIdleRooms()에서
if (idleTime > WARNING_TIMEOUT && idleTime < IDLE_TIMEOUT) {
    sendWarningMessage(room);
}
```

### 2. 타임아웃 시간 동적 설정
```yaml
# application.yml
app:
  chat:
    idle-timeout-minutes: 10
    cleanup-interval-seconds: 60
```

### 3. 고객별 타임아웃 설정
```java
// 특정 고객은 더 긴 타임아웃 적용
long timeout = getTimeoutForCustomer(room.getCustId());
```

---

## 🔗 관련 파일

- `RoomCleanupService.java` - 메인 로직
- `websocket-client.html` - 프론트엔드 처리
- `MessageBroker.java` - 메시지 전송 인터페이스
- `ChatHistoryService.java` - DB 이력 저장
- `ChatSessionService.java` - DB 세션 관리
- `RedisRoomRepository.java` - Redis 채팅방 관리

---

## 📞 지원

추가 질문이나 문제가 있으면 로그를 확인하고 다음 정보를 포함해주세요:
- 에러 메시지
- 관련 로그
- PostgreSQL 및 Redis 연결 상태
- 브라우저 콘솔 로그
