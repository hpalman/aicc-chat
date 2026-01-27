# Idle 채팅방 자동 종료 알림 - 요약

## 📌 개요

일정 시간(기본 10분) 동안 활동이 없는 채팅방을 자동으로 정리하면서 고객에게 알림을 전송하고 데이터베이스에 기록하는 기능입니다.

---

## 🔄 전체 동작 흐름

```
┌─────────────────────────────────────────────────────────────────────┐
│                     RoomCleanupService                               │
│                  (@Scheduled - 매 1분마다)                           │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │ Idle 채팅방 검색  │
                    │  (10분 이상)     │
                    └──────────────────┘
                              │
                              ▼
        ┌─────────────────────┴─────────────────────┐
        │                                            │
        ▼                                            ▼
┌──────────────────┐                    ┌──────────────────────┐
│  1. 고객 알림     │                    │  2. DB 기록          │
│  (WebSocket)     │                    │  (PostgreSQL)        │
└──────────────────┘                    └──────────────────────┘
        │                                            │
        ▼                                            ▼
┌──────────────────┐                    ┌──────────────────────┐
│ MessageBroker    │                    │ ChatSessionService   │
│ .publish()       │                    │ - updateStatus()     │
│                  │                    │ - endSession()       │
│ ChatMessage:     │                    │                      │
│ - type: LEAVE    │                    │ ChatHistoryService   │
│ - sender: System │                    │ - saveChatHistory()  │
│ - message:       │                    └──────────────────────┘
│   "자동 종료..."  │
└──────────────────┘
        │
        ▼
┌──────────────────────────────────────┐
│     고객 브라우저 (WebSocket)         │
│     /topic/room/{roomId}             │
└──────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────┐
│   showMessage() 함수 실행             │
│   - 시스템 메시지 표시                 │
│   - 3초 후 자동 종료                   │
│   - Alert 알림                        │
└──────────────────────────────────────┘
        │
        ▼
        ┌─────────────────────┐
        │  3. Redis 정리       │
        │  roomRepository     │
        │  .deleteRoom()      │
        └─────────────────────┘
                │
                ▼
        ┌─────────────────────┐
        │  4. 상담원 알림      │
        │  broadcastRoomList() │
        └─────────────────────┘
```

---

## 🎯 주요 기능

### 1. **고객 알림 (WebSocket)**
```javascript
// 고객 브라우저에 표시되는 메시지
System: "장시간 대화가 없어 상담이 자동 종료되었습니다."

// 3초 후 자동 실행:
- WebSocket 연결 해제
- 채팅 화면 종료
- 상담 신청 화면으로 복귀
- Alert 알림 표시
```

### 2. **데이터베이스 기록**
```sql
-- chat_session 테이블
UPDATE chat_session 
SET status = 'CLOSED', 
    ended_at = NOW() 
WHERE room_id = ?;

-- chat_history 테이블
INSERT INTO chat_history (
    room_id, sender_id, sender_name, sender_role,
    message, message_type
) VALUES (
    ?, 'system', 'System', 'SYSTEM',
    '장시간 대화가 없어 상담이 자동 종료되었습니다.', 'LEAVE'
);
```

### 3. **Redis 정리**
```java
// Redis에서 채팅방 데이터 삭제
roomRepository.deleteRoom(roomId);

// 삭제되는 Redis 키:
// - chat:room:{roomId}
// - chat:room:{roomId}:members
// - chat:room:{roomId}:name
// - chat:room:{roomId}:status
// - chat:room:{roomId}:agent
// - chat:room:{roomId}:created
// - chat:room:{roomId}:activity
// - chat:rooms (Set에서 제거)
```

### 4. **상담원 알림**
```java
// 상담원 화면에 업데이트된 채팅방 목록 전송
roomUpdateBroadcaster.broadcastRoomList();
```

---

## 📝 수정된 코드 요약

### RoomCleanupService.java
```java
@Service
@RequiredArgsConstructor
public class RoomCleanupService {
    private final RoomRepository roomRepository;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final MessageBroker messageBroker;              // ✅ 추가
    private final ChatHistoryService chatHistoryService;     // ✅ 추가
    private final ChatSessionService chatSessionService;     // ✅ 추가
    
    @Scheduled(fixedRate = 60000)
    public void cleanupIdleRooms() {
        for (ChatRoom room : allRooms) {
            if (idleTime > IDLE_TIMEOUT) {
                notifyRoomTimeout(room);           // ✅ 1. 고객 알림
                saveRoomTimeoutToDatabase(room);   // ✅ 2. DB 기록
                roomRepository.deleteRoom(roomId); // 3. Redis 정리
            }
        }
        roomUpdateBroadcaster.broadcastRoomList(); // 4. 상담원 알림
    }
    
    // ✅ 새로운 메소드
    private void notifyRoomTimeout(ChatRoom room) { ... }
    private void saveRoomTimeoutToDatabase(ChatRoom room) { ... }
}
```

### websocket-client.html
```javascript
function showMessage(message) {
    if (message.type === 'LEAVE') {
        // ✅ 자동 종료 감지
        if (message.sender === 'System' && 
            message.message.includes("자동 종료")) {
            
            setTimeout(() => {
                // WebSocket 종료
                stompClient.disconnect();
                
                // UI 초기화
                currentRoomId = null;
                document.getElementById("chat-page").style.display = "none";
                document.getElementById("connect-form").style.display = "block";
                
                // 알림
                alert("장시간 대화가 없어 상담이 종료되었습니다.");
            }, 3000); // ✅ 3초 후 자동 실행
        }
    }
}
```

---

## ⚙️ 설정 가이드

### 타임아웃 시간 변경
```java
// RoomCleanupService.java

// 현재: 1분 (테스트용)
private static final long IDLE_TIMEOUT = 1 * 60 * 1000;

// 프로덕션 권장: 10분
private static final long IDLE_TIMEOUT = 10 * 60 * 1000;

// 커스텀 예시: 30분
private static final long IDLE_TIMEOUT = 30 * 60 * 1000;
```

### 체크 주기 변경
```java
// 매 1분마다 (현재)
@Scheduled(fixedRate = 60000)

// 매 5분마다
@Scheduled(fixedRate = 300000)
```

### 자동 종료 대기 시간 변경
```javascript
// websocket-client.html

// 3초 (현재)
setTimeout(() => { ... }, 3000);

// 5초로 변경
setTimeout(() => { ... }, 5000);
```

---

## 🧪 테스트 방법

### 1. 빠른 테스트
```java
// 타임아웃을 1분으로 설정
private static final long IDLE_TIMEOUT = 1 * 60 * 1000;
```

### 2. 테스트 절차
```
1. 고객 로그인 (cust01 / 1234)
2. 상담 시작
3. 1분간 메시지 전송 안함 ⏱️
4. [자동] 시스템 메시지 수신 확인 ✅
   "장시간 대화가 없어 상담이 자동 종료되었습니다."
5. [자동] 3초 후 연결 해제 ✅
6. [자동] Alert 표시 ✅
7. [자동] 초기 화면 복귀 ✅
```

### 3. 데이터 확인
```sql
-- 세션 종료 기록
SELECT * FROM chat_session 
WHERE status = 'CLOSED' 
ORDER BY ended_at DESC LIMIT 5;

-- 타임아웃 메시지
SELECT * FROM chat_history 
WHERE message LIKE '%자동 종료%' 
ORDER BY created_at DESC LIMIT 5;
```

---

## 🔍 로그 확인

### application.yml 설정
```yaml
logging:
  level:
    aicc.chat.service.RoomCleanupService: debug
```

### 확인할 로그
```
INFO  - Cleaning up idle room: room-abc123 (Idle for 60123 ms)
INFO  - Timeout notification sent to room: room-abc123
INFO  - Timeout record saved to database for room: room-abc123
```

---

## 💾 데이터베이스 저장 내용

### chat_session 테이블
| 컬럼 | 값 |
|------|-----|
| status | `CLOSED` |
| ended_at | 종료 시간 (NOW()) |
| updated_at | 업데이트 시간 (NOW()) |

### chat_history 테이블
| 컬럼 | 값 |
|------|-----|
| room_id | 채팅방 ID |
| sender_id | `system` |
| sender_name | `System` |
| sender_role | `SYSTEM` |
| message | `장시간 대화가 없어 상담이 자동 종료되었습니다.` |
| message_type | `LEAVE` |

---

## 🎨 UI 표시 예시

### 고객 화면
```
┌─────────────────────────────────────────┐
│  상담 중                        [종료]   │
├─────────────────────────────────────────┤
│                                         │
│  홍길철: 안녕하세요?                      │
│                                         │
│  [System]                               │
│  장시간 대화가 없어                       │
│  상담이 자동 종료되었습니다.              │
│                                         │
└─────────────────────────────────────────┘
         ↓ (3초 후)
┌─────────────────────────────────────────┐
│  ⚠️ 장시간 대화가 없어                    │
│     상담이 종료되었습니다.                │
│                             [확인]       │
└─────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────┐
│  상담 신청                                │
├─────────────────────────────────────────┤
│  사용자 정보: 홍길철 (cust01)            │
│  상담 문의: [            ]               │
│             [상담 시작하기]              │
└─────────────────────────────────────────┘
```

---

## 📊 모니터링 쿼리

### 일별 타임아웃 통계
```sql
SELECT 
    DATE(ended_at) as date,
    COUNT(*) as timeout_count,
    AVG(EXTRACT(EPOCH FROM (ended_at - last_activity_at))) / 60 as avg_idle_minutes
FROM chat_session
WHERE status = 'CLOSED'
  AND ended_at IS NOT NULL
GROUP BY DATE(ended_at)
ORDER BY date DESC;
```

### Idle 시간 분포
```sql
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
GROUP BY idle_range;
```

---

## ✅ 체크리스트

- [x] RoomCleanupService에 의존성 추가 (MessageBroker, ChatHistoryService, ChatSessionService)
- [x] notifyRoomTimeout() 메소드 구현 - 고객 알림
- [x] saveRoomTimeoutToDatabase() 메소드 구현 - DB 기록
- [x] websocket-client.html의 showMessage() 수정 - 자동 종료 처리
- [x] 컴파일 테스트 통과 ✅
- [x] 상세 가이드 문서 작성 ✅

---

## 📚 관련 문서

- **상세 가이드**: `IDLE_ROOM_CLEANUP_NOTIFICATION_GUIDE.md`
- **Redis 아키텍처**: `ref/cursor_redis_chat_system_architecture_a.md`
- **MyBatis 가이드**: `SQL_LOGGING_GUIDE.md`

---

## 🚀 다음 단계

1. **테스트 실행**
   ```bash
   cd E:\aicc-dev\aicc\aicc-chat
   .\gradlew bootRun
   ```

2. **타임아웃 시간 조정**
   - 테스트: 1분
   - 프로덕션: 10분

3. **모니터링 설정**
   - 로그 레벨 조정
   - DB 쿼리 실행

---

## 💡 추가 개선 아이디어

1. **타임아웃 전 경고**
   - 8분에 "2분 후 자동 종료" 경고 메시지

2. **동적 타임아웃**
   - 고객별 또는 상담 유형별 타임아웃 설정

3. **재연결 옵션**
   - 자동 종료 후 "상담 재개" 버튼 제공

4. **통계 대시보드**
   - 타임아웃 발생 빈도 그래프
   - 평균 Idle 시간 분석
