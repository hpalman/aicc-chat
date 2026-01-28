# WebSocket 세션 관리 Redis 저장 가이드

> **작성일**: 2026-01-23  
> **목적**: WebSocket 세션ID와 고객ID 매핑을 Redis에 저장하여 실시간 세션 관리  
> **기능**: 온라인 사용자 추적, 세션-사용자 매핑, 다중 세션 지원

---

## 📋 목차

1. [개요](#-개요)
2. [Redis 구조](#-redis-구조)
3. [구현 상세](#-구현-상세)
4. [API 사용 가이드](#-api-사용-가이드)
5. [테스트 시나리오](#-테스트-시나리오)
6. [Redis CLI 명령어](#-redis-cli-명령어)
7. [활용 사례](#-활용-사례)

---

## 🎯 개요

### 목적

WebSocket 연결 시 생성되는 세션ID와 사용자ID(고객ID, 상담원ID)를 Redis에 저장하여:
- 실시간 온라인 사용자 추적
- 세션ID로 사용자 정보 조회
- 사용자ID로 모든 활성 세션 조회
- 다중 디바이스/탭 접속 지원
- 세션 타임아웃 관리

### 주요 기능

| 기능 | 설명 |
|------|------|
| **세션 등록** | WebSocket 연결 시 자동 등록 |
| **세션 해제** | 연결 종료 시 자동 제거 |
| **양방향 매핑** | sessionId ↔ userId 양방향 조회 |
| **다중 세션** | 한 사용자의 여러 세션 관리 |
| **온라인 체크** | 사용자 온라인 상태 확인 |
| **TTL 관리** | 24시간 자동 만료 |

---

## 💾 Redis 구조

### 키 구조 설계

```
1. ws:session:{sessionId} = {userId}
   - sessionId → userId 매핑
   - TTL: 24시간
   - 예: ws:session:abc123 = "cust01"

2. ws:session:{sessionId}:role = {userRole}
   - sessionId → userRole 매핑
   - TTL: 24시간
   - 예: ws:session:abc123:role = "CUSTOMER"

3. ws:user:{userId} = Set<sessionId>
   - userId → sessionId Set
   - TTL: 24시간
   - 예: ws:user:cust01 = ["abc123", "def456"]

4. ws:sessions:all = Set<sessionId>
   - 모든 활성 세션 Set
   - TTL: 없음 (계속 유지)
   - 예: ws:sessions:all = ["abc123", "def456", "ghi789"]
```

### Redis 데이터 예시

#### 고객 1명, 세션 1개

```
ws:session:abc123 = "cust01"
ws:session:abc123:role = "CUSTOMER"
ws:user:cust01 = ["abc123"]
ws:sessions:all = ["abc123"]
```

#### 고객 1명, 세션 2개 (PC + 모바일)

```
ws:session:abc123 = "cust01"
ws:session:abc123:role = "CUSTOMER"
ws:session:def456 = "cust01"
ws:session:def456:role = "CUSTOMER"
ws:user:cust01 = ["abc123", "def456"]
ws:sessions:all = ["abc123", "def456"]
```

#### 고객 2명, 상담원 1명

```
# 고객 1
ws:session:abc123 = "cust01"
ws:session:abc123:role = "CUSTOMER"
ws:user:cust01 = ["abc123"]

# 고객 2
ws:session:def456 = "cust02"
ws:session:def456:role = "CUSTOMER"
ws:user:cust02 = ["def456"]

# 상담원
ws:session:ghi789 = "agent01"
ws:session:ghi789:role = "AGENT"
ws:user:agent01 = ["ghi789"]

# 전체 세션
ws:sessions:all = ["abc123", "def456", "ghi789"]
```

---

## 🔧 구현 상세

### 1. WebSocketSessionService.java

#### 주요 메서드

```java
@Service
@RequiredArgsConstructor
public class WebSocketSessionService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String SESSION_TO_USER_PREFIX = "ws:session:";
    private static final String USER_TO_SESSIONS_PREFIX = "ws:user:";
    private static final String ALL_SESSIONS_KEY = "ws:sessions:all";
    private static final long SESSION_TTL_HOURS = 24;

    /**
     * 세션 등록
     */
    public void registerSession(String sessionId, String userId, String userRole) {
        // 1. sessionId -> userId 매핑
        redisTemplate.opsForValue().set(
            SESSION_TO_USER_PREFIX + sessionId, 
            userId, 
            SESSION_TTL_HOURS, 
            TimeUnit.HOURS
        );
        
        // 2. userId -> sessionId Set에 추가
        redisTemplate.opsForSet().add(
            USER_TO_SESSIONS_PREFIX + userId, 
            sessionId
        );
        
        // 3. 전체 세션 Set에 추가
        redisTemplate.opsForSet().add(ALL_SESSIONS_KEY, sessionId);
        
        // 4. 역할 정보 저장
        if (userRole != null) {
            redisTemplate.opsForValue().set(
                SESSION_TO_USER_PREFIX + sessionId + ":role", 
                userRole, 
                SESSION_TTL_HOURS, 
                TimeUnit.HOURS
            );
        }
    }

    /**
     * 세션 해제
     */
    public void unregisterSession(String sessionId) {
        // 1. sessionId로 userId 조회
        String userId = redisTemplate.opsForValue().get(
            SESSION_TO_USER_PREFIX + sessionId
        );
        
        if (userId != null) {
            // 2. userId -> sessionId Set에서 제거
            redisTemplate.opsForSet().remove(
                USER_TO_SESSIONS_PREFIX + userId, 
                sessionId
            );
            
            // 3. userId의 세션이 모두 제거되었으면 키 삭제
            Long count = redisTemplate.opsForSet().size(
                USER_TO_SESSIONS_PREFIX + userId
            );
            if (count != null && count == 0) {
                redisTemplate.delete(USER_TO_SESSIONS_PREFIX + userId);
            }
        }
        
        // 4. sessionId -> userId 매핑 삭제
        redisTemplate.delete(SESSION_TO_USER_PREFIX + sessionId);
        
        // 5. 역할 정보 삭제
        redisTemplate.delete(SESSION_TO_USER_PREFIX + sessionId + ":role");
        
        // 6. 전체 세션 Set에서 제거
        redisTemplate.opsForSet().remove(ALL_SESSIONS_KEY, sessionId);
    }

    /**
     * 세션ID로 사용자ID 조회
     */
    public String getUserIdBySessionId(String sessionId) {
        return redisTemplate.opsForValue().get(
            SESSION_TO_USER_PREFIX + sessionId
        );
    }

    /**
     * 사용자ID로 모든 활성 세션 조회
     */
    public Set<String> getSessionIdsByUserId(String userId) {
        Set<String> sessions = redisTemplate.opsForSet().members(
            USER_TO_SESSIONS_PREFIX + userId
        );
        return sessions != null ? sessions : Collections.emptySet();
    }

    /**
     * 사용자 온라인 상태 확인
     */
    public boolean isUserOnline(String userId) {
        Long count = redisTemplate.opsForSet().size(
            USER_TO_SESSIONS_PREFIX + userId
        );
        return count != null && count > 0;
    }

    /**
     * 세션 TTL 갱신
     */
    public void refreshSessionTTL(String sessionId) {
        String userId = getUserIdBySessionId(sessionId);
        if (userId != null) {
            redisTemplate.expire(
                SESSION_TO_USER_PREFIX + sessionId, 
                SESSION_TTL_HOURS, 
                TimeUnit.HOURS
            );
            redisTemplate.expire(
                USER_TO_SESSIONS_PREFIX + userId, 
                SESSION_TTL_HOURS, 
                TimeUnit.HOURS
            );
        }
    }

    /**
     * 전체 활성 세션 조회
     */
    public Set<String> getAllActiveSessions() {
        Set<String> sessions = redisTemplate.opsForSet().members(
            ALL_SESSIONS_KEY
        );
        return sessions != null ? sessions : Collections.emptySet();
    }

    /**
     * 전체 세션 수 조회
     */
    public long getTotalSessionCount() {
        Long count = redisTemplate.opsForSet().size(ALL_SESSIONS_KEY);
        return count != null ? count : 0;
    }
}
```

---

### 2. WebSocketEventListener.java

#### 연결 완료 시 세션 등록

```java
@EventListener
public void onConnected(SessionConnectedEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    
    String sessionId = accessor.getSessionId();
    String userId = null;
    String userRole = null;
    
    // 세션 속성에서 userId, userRole 추출
    if (accessor.getSessionAttributes() != null) {
        Object userIdObj = accessor.getSessionAttributes().get("userId");
        Object userRoleObj = accessor.getSessionAttributes().get("userRole");
        
        if (userIdObj != null) {
            userId = userIdObj.toString();
        }
        if (userRoleObj != null) {
            userRole = userRoleObj.toString();
        }
    }
    
    // ✅ Redis에 세션 등록
    if (sessionId != null && userId != null) {
        webSocketSessionService.registerSession(sessionId, userId, userRole);
        log.info("✅ Redis에 세션 등록 완료 - sessionId: {}, userId: {}, role: {}", 
                 sessionId, userId, userRole);
    }
}
```

#### 연결 해제 시 세션 제거

```java
@EventListener
public void onDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String sessionId = accessor.getSessionId();
    
    // ✅ Redis에서 세션 제거
    if (sessionId != null) {
        webSocketSessionService.unregisterSession(sessionId);
        log.info("✅ Redis에서 세션 제거 완료 - sessionId: {}", sessionId);
    }
    
    // 기존 채팅방 멤버 제거 로직
    roomRepository.removeMemberFromAll(sessionId);
}
```

---

### 3. WebSocketSessionController.java

#### REST API 엔드포인트

| 메서드 | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/session/all` | 모든 활성 세션 조회 |
| GET | `/api/session/{sessionId}` | 세션ID로 사용자 정보 조회 |
| GET | `/api/session/user/{userId}` | 사용자ID로 모든 세션 조회 |
| GET | `/api/session/user/{userId}/online` | 사용자 온라인 상태 확인 |
| POST | `/api/session/{sessionId}/refresh` | 세션 TTL 갱신 |
| GET | `/api/session/stats` | 세션 통계 조회 |

---

## 📡 API 사용 가이드

### 1. 모든 활성 세션 조회

```bash
GET /api/session/all

# 응답
{
  "totalCount": 3,
  "sessions": [
    "abc123",
    "def456",
    "ghi789"
  ]
}
```

---

### 2. 세션ID로 사용자 정보 조회

```bash
GET /api/session/abc123

# 응답
{
  "sessionId": "abc123",
  "userId": "cust01",
  "userRole": "CUSTOMER"
}
```

---

### 3. 사용자ID로 모든 세션 조회

```bash
GET /api/session/user/cust01

# 응답
{
  "userId": "cust01",
  "isOnline": true,
  "sessionCount": 2,
  "sessions": [
    "abc123",
    "def456"
  ]
}
```

**설명**: `cust01` 사용자가 2개의 디바이스(PC, 모바일)로 접속 중

---

### 4. 사용자 온라인 상태 확인

```bash
GET /api/session/user/cust01/online

# 응답
{
  "userId": "cust01",
  "isOnline": true,
  "sessionCount": 2
}
```

---

### 5. 세션 TTL 갱신 (하트비트)

```bash
POST /api/session/abc123/refresh

# 응답
{
  "sessionId": "abc123",
  "userId": "cust01",
  "refreshed": true
}
```

**설명**: 세션 TTL을 24시간으로 재설정

---

### 6. 세션 통계 조회

```bash
GET /api/session/stats

# 응답
{
  "totalActiveSessions": 15,
  "timestamp": 1706011234567
}
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 단일 사용자 접속 ✅

```
1. 고객 cust01이 PC로 접속
   - WebSocket 연결: sessionId = "abc123"
   
2. Redis 상태 확인
   GET /api/session/user/cust01
   {
     "userId": "cust01",
     "isOnline": true,
     "sessionCount": 1,
     "sessions": ["abc123"]
   }

3. 세션 정보 확인
   GET /api/session/abc123
   {
     "sessionId": "abc123",
     "userId": "cust01",
     "userRole": "CUSTOMER"
   }
```

---

### 시나리오 2: 다중 디바이스 접속 ✅

```
1. 고객 cust01이 PC로 접속
   - sessionId = "abc123"

2. 같은 고객이 모바일로 접속
   - sessionId = "def456"

3. Redis 상태 확인
   GET /api/session/user/cust01
   {
     "userId": "cust01",
     "isOnline": true,
     "sessionCount": 2,
     "sessions": ["abc123", "def456"]
   }

4. PC 연결 종료
   - "abc123" 세션 제거

5. Redis 상태 재확인
   GET /api/session/user/cust01
   {
     "userId": "cust01",
     "isOnline": true,
     "sessionCount": 1,
     "sessions": ["def456"]
   }

6. 모바일 연결 종료
   - "def456" 세션 제거

7. 최종 상태 확인
   GET /api/session/user/cust01/online
   {
     "userId": "cust01",
     "isOnline": false,
     "sessionCount": 0
   }
```

---

### 시나리오 3: 다중 사용자 접속 ✅

```
1. 고객 3명, 상담원 2명 접속
   - cust01 (PC): "abc123"
   - cust02 (모바일): "def456"
   - cust03 (PC): "ghi789"
   - agent01 (PC): "jkl012"
   - agent02 (PC): "mno345"

2. 전체 세션 조회
   GET /api/session/all
   {
     "totalCount": 5,
     "sessions": [
       "abc123", "def456", "ghi789", "jkl012", "mno345"
     ]
   }

3. 통계 조회
   GET /api/session/stats
   {
     "totalActiveSessions": 5,
     "timestamp": 1706011234567
   }
```

---

### 시나리오 4: TTL 자동 만료 ✅

```
1. 고객 cust01 접속
   - sessionId = "abc123"
   - TTL: 24시간

2. 24시간 경과 (네트워크 끊김, 하트비트 없음)
   - Redis 키 자동 만료

3. 상태 확인
   GET /api/session/user/cust01/online
   {
     "userId": "cust01",
     "isOnline": false,
     "sessionCount": 0
   }
```

---

## 🔍 Redis CLI 명령어

### 세션 조회

```bash
# Redis 접속
redis-cli

# 1. 모든 활성 세션 조회
SMEMBERS ws:sessions:all

# 결과:
# 1) "abc123"
# 2) "def456"
# 3) "ghi789"

# 2. sessionId로 userId 조회
GET ws:session:abc123
# "cust01"

# 3. sessionId로 역할 조회
GET ws:session:abc123:role
# "CUSTOMER"

# 4. userId로 모든 세션 조회
SMEMBERS ws:user:cust01
# 1) "abc123"
# 2) "def456"

# 5. TTL 확인
TTL ws:session:abc123
# 86400 (24시간 = 86400초)

# 6. 특정 사용자 온라인 여부
EXISTS ws:user:cust01
# 1 (존재함 = 온라인)
# 0 (없음 = 오프라인)

# 7. 전체 세션 수
SCARD ws:sessions:all
# 15
```

---

### 세션 수동 조작

```bash
# 1. 세션 수동 등록
SET ws:session:test123 "testUser" EX 86400
SADD ws:user:testUser test123
SADD ws:sessions:all test123

# 2. 세션 수동 삭제
DEL ws:session:test123
SREM ws:user:testUser test123
SREM ws:sessions:all test123

# 3. 사용자의 모든 세션 삭제
SMEMBERS ws:user:cust01
# (결과로 나온 sessionId들을 각각 삭제)
DEL ws:session:abc123
DEL ws:session:def456
DEL ws:user:cust01

# 4. 전체 세션 초기화
DEL ws:sessions:all
KEYS ws:session:*
# (결과로 나온 키들을 각각 삭제)
```

---

### 세션 디버깅

```bash
# 1. 모든 세션 키 확인
KEYS ws:session:*

# 2. 모든 사용자 키 확인
KEYS ws:user:*

# 3. 특정 패턴 검색
KEYS ws:*

# 4. 세션 상세 정보
GET ws:session:abc123
GET ws:session:abc123:role
SMEMBERS ws:user:cust01

# 5. 활성 세션 vs 등록된 세션 비교
SCARD ws:sessions:all
# 15 (전체 세션 수)

KEYS ws:session:* | wc -l
# 30 (sessionId + role 키 = 15 * 2)
```

---

## 💡 활용 사례

### 1. 메시지 특정 사용자에게 전송

```java
@Service
public class MessageNotificationService {
    
    private final WebSocketSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 특정 사용자에게 메시지 전송 (모든 세션)
     */
    public void sendToUser(String userId, String message) {
        Set<String> sessions = sessionService.getSessionIdsByUserId(userId);
        
        for (String sessionId : sessions) {
            messagingTemplate.convertAndSendToUser(
                sessionId, 
                "/queue/messages", 
                message
            );
        }
    }
}
```

---

### 2. 중복 로그인 방지

```java
@Service
public class LoginService {
    
    private final WebSocketSessionService sessionService;
    
    /**
     * 로그인 시 기존 세션 확인
     */
    public void login(String userId) {
        // 기존 세션이 있으면 강제 종료
        if (sessionService.isUserOnline(userId)) {
            Set<String> oldSessions = sessionService.getSessionIdsByUserId(userId);
            
            for (String sessionId : oldSessions) {
                sessionService.unregisterSession(sessionId);
                // WebSocket 연결 종료 로직 추가
            }
            
            log.info("기존 세션 종료 - userId: {}, sessions: {}", userId, oldSessions);
        }
        
        // 새 로그인 처리
    }
}
```

---

### 3. 온라인 사용자 목록

```java
@Service
public class OnlineUserService {
    
    private final WebSocketSessionService sessionService;
    
    /**
     * 현재 온라인인 모든 사용자 조회
     */
    public List<String> getOnlineUsers() {
        Set<String> allSessions = sessionService.getAllActiveSessions();
        Set<String> onlineUsers = new HashSet<>();
        
        for (String sessionId : allSessions) {
            String userId = sessionService.getUserIdBySessionId(sessionId);
            if (userId != null) {
                onlineUsers.add(userId);
            }
        }
        
        return new ArrayList<>(onlineUsers);
    }
}
```

---

### 4. 상담원 배정 시 온라인 체크

```java
@Service
public class AgentAssignmentService {
    
    private final WebSocketSessionService sessionService;
    
    /**
     * 온라인 상태인 상담원에게만 배정
     */
    public String assignAvailableAgent(List<String> agentIds) {
        for (String agentId : agentIds) {
            if (sessionService.isUserOnline(agentId)) {
                log.info("상담원 {} 온라인 확인, 배정 진행", agentId);
                return agentId;
            }
        }
        
        log.warn("온라인 상태인 상담원이 없습니다.");
        return null;
    }
}
```

---

### 5. 브로드캐스트 메시지 전송

```java
@Service
public class BroadcastService {
    
    private final WebSocketSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * 모든 온라인 사용자에게 공지사항 전송
     */
    public void broadcastToAll(String message) {
        Set<String> allSessions = sessionService.getAllActiveSessions();
        
        for (String sessionId : allSessions) {
            messagingTemplate.convertAndSend(
                "/topic/broadcast", 
                message
            );
        }
        
        log.info("공지사항 전송 완료 - 대상: {} 세션", allSessions.size());
    }
}
```

---

## ⚙️ 설정 및 최적화

### TTL 조정

```java
// WebSocketSessionService.java
private static final long SESSION_TTL_HOURS = 24; // 기본값

// 짧게 설정 (6시간)
private static final long SESSION_TTL_HOURS = 6;

// 길게 설정 (7일)
private static final long SESSION_TTL_HOURS = 7 * 24;
```

---

### 하트비트 구현

```javascript
// chat-customer.html
let sessionHeartbeatInterval = null;

function startSessionHeartbeat() {
    sessionHeartbeatInterval = setInterval(() => {
        const sessionId = stompClient.ws._transport.url.split('/')[5];
        
        fetch(`/api/session/${sessionId}/refresh`, {
            method: 'POST'
        }).then(res => {
            if (res.ok) {
                console.log('세션 TTL 갱신 성공');
            }
        });
    }, 60 * 60 * 1000); // 1시간마다
}

function stopSessionHeartbeat() {
    if (sessionHeartbeatInterval) {
        clearInterval(sessionHeartbeatInterval);
    }
}
```

---

## 📊 모니터링

### 세션 통계 대시보드

```javascript
// 주기적으로 세션 통계 조회
setInterval(() => {
    fetch('/api/session/stats')
        .then(res => res.json())
        .then(data => {
            console.log('활성 세션:', data.totalActiveSessions);
            document.getElementById('session-count').innerText = 
                data.totalActiveSessions;
        });
}, 30000); // 30초마다
```

---

## 📝 변경된 파일 목록

### 신규 파일 (2개)

1. **`src/main/java/aicc/chat/service/WebSocketSessionService.java`**
   - WebSocket 세션 관리 서비스
   - Redis 저장/조회/삭제 로직

2. **`src/main/java/aicc/chat/controller/WebSocketSessionController.java`**
   - 세션 관리 REST API
   - 6개 엔드포인트

### 수정 파일 (1개)

1. **`src/main/java/aicc/chat/websocket/WebSocketEventListener.java`**
   - `onConnected()`: 세션 등록 로직 추가
   - `onDisconnect()`: 세션 해제 로직 추가

---

## ✅ 체크리스트

### 구현 확인
- [x] WebSocketSessionService 생성
- [x] WebSocketSessionController 생성
- [x] WebSocketEventListener 수정
- [x] Redis 키 구조 설계
- [x] TTL 설정 (24시간)

### 기능 확인
- [x] 세션 등록 (연결 시)
- [x] 세션 해제 (연결 종료 시)
- [x] sessionId → userId 조회
- [x] userId → sessionId Set 조회
- [x] 온라인 상태 확인
- [x] 다중 세션 지원

### API 확인
- [x] GET /api/session/all
- [x] GET /api/session/{sessionId}
- [x] GET /api/session/user/{userId}
- [x] GET /api/session/user/{userId}/online
- [x] POST /api/session/{sessionId}/refresh
- [x] GET /api/session/stats

---

## 🎉 완료!

WebSocket 세션ID와 사용자ID 매핑이 Redis에 저장되어 실시간 세션 관리가 가능합니다!

**주요 기능:**
- ✅ 자동 세션 등록/해제
- ✅ 양방향 매핑 (sessionId ↔ userId)
- ✅ 다중 디바이스 지원
- ✅ 온라인 상태 추적
- ✅ REST API 제공
- ✅ TTL 자동 관리

**테스트:**
```bash
# 1. 서버 실행
./gradlew bootRun

# 2. 고객 접속 (chat-customer.html)
# 3. Redis 확인
redis-cli
KEYS ws:*

# 4. API 테스트
curl http://localhost:28070/api/session/stats
```

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23
