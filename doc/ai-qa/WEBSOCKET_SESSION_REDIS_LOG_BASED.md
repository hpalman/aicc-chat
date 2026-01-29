# WebSocket 세션 Redis 저장 - 실제 로그 기반 개선 가이드

> **작성일**: 2026-01-23  
> **목적**: 실제 WebSocket 연결 시 발생하는 이벤트 로그를 기반으로 정확한 세션 정보 추출  
> **개선**: `simpSessionId`와 `simpSessionAttributes`에서 정확한 값 추출

---

## 📋 실제 WebSocket 이벤트 로그 분석

### onConnected 이벤트 로그 예시

```
{
  simpMessageType=CONNECT_ACK, 
  simpConnectMessage=GenericMessage [
    payload=byte[0], 
    headers={
      simpMessageType=CONNECT, 
      stompCommand=CONNECT, 
      nativeHeaders={
        accept-version=[1.1,1.0], 
        heart-beat=[10000,10000]
      }, 
      simpSessionAttributes={
        userName=홍길철, 
        userId=cust01, 
        roomId=room-307540f4, 
        companyId=apt001, 
        userEmail=cust01@example.com, 
        userRole=CUSTOMER
      }, 
      simpHeartbeat=[J@5de6faa, 
      simpSessionId=4azgoisg
    }
  ], 
  simpSessionId=4azgoisg
}
```

### 로그 분석 결과

| 항목 | 위치 | 값 예시 | 설명 |
|------|------|---------|------|
| **세션 ID** | `simpSessionId` | `4azgoisg` | WebSocket 고유 세션 ID |
| **사용자 ID** | `simpSessionAttributes.userId` | `cust01` | 고객/상담원 ID |
| **사용자 이름** | `simpSessionAttributes.userName` | `홍길철` | 실제 이름 |
| **사용자 역할** | `simpSessionAttributes.userRole` | `CUSTOMER` | 고객/상담원 구분 |
| **회사 ID** | `simpSessionAttributes.companyId` | `apt001` | 회사/아파트 코드 |
| **채팅방 ID** | `simpSessionAttributes.roomId` | `room-307540f4` | 채팅방 ID |
| **이메일** | `simpSessionAttributes.userEmail` | `cust01@example.com` | 사용자 이메일 |

---

## 🔧 개선된 코드

### WebSocketEventListener.java - onConnected()

```java
@EventListener
public void onConnected(SessionConnectedEvent event) {
    log.info("========================================");
    log.info("▶ WebSocket 연결 완료 이벤트");
    log.info("========================================");
    
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    
    // 1. simpSessionId 추출 ✅
    String simpSessionId = accessor.getSessionId();
    log.info("📌 simpSessionId: {}", simpSessionId);
    
    // 2. simpSessionAttributes에서 사용자 정보 추출 ✅
    String userId = null;
    String userName = null;
    String userRole = null;
    String userEmail = null;
    String companyId = null;
    String roomId = null;
    
    if (accessor.getSessionAttributes() != null) {
        log.info("📦 simpSessionAttributes 추출 중...");
        
        Object userIdObj = accessor.getSessionAttributes().get("userId");
        Object userNameObj = accessor.getSessionAttributes().get("userName");
        Object userRoleObj = accessor.getSessionAttributes().get("userRole");
        Object userEmailObj = accessor.getSessionAttributes().get("userEmail");
        Object companyIdObj = accessor.getSessionAttributes().get("companyId");
        Object roomIdObj = accessor.getSessionAttributes().get("roomId");
        
        if (userIdObj != null) {
            userId = userIdObj.toString();
            log.info("  ✓ userId: {}", userId);
        }
        if (userNameObj != null) {
            userName = userNameObj.toString();
            log.info("  ✓ userName: {}", userName);
        }
        if (userRoleObj != null) {
            userRole = userRoleObj.toString();
            log.info("  ✓ userRole: {}", userRole);
        }
        if (userEmailObj != null) {
            userEmail = userEmailObj.toString();
            log.info("  ✓ userEmail: {}", userEmail);
        }
        if (companyIdObj != null) {
            companyId = companyIdObj.toString();
            log.info("  ✓ companyId: {}", companyId);
        }
        if (roomIdObj != null) {
            roomId = roomIdObj.toString();
            log.info("  ✓ roomId: {}", roomId);
        }
    } else {
        log.warn("⚠️ simpSessionAttributes가 null입니다!");
    }
    
    // 3. Redis에 세션 정보 저장 ✅
    if (simpSessionId != null && userId != null) {
        log.info("💾 Redis에 세션 정보 저장 시작...");
        log.info("  - sessionId (simpSessionId): {}", simpSessionId);
        log.info("  - userId: {}", userId);
        log.info("  - userRole: {}", userRole);
        
        webSocketSessionService.registerSession(simpSessionId, userId, userRole);
        
        log.info("✅ Redis에 세션 등록 완료!");
        log.info("  - Redis Key: ws:session:{}", simpSessionId);
        log.info("  - Redis Value: {}", userId);
    } else {
        log.error("❌ Redis 세션 등록 실패 - sessionId 또는 userId가 null입니다.");
        log.error("  - simpSessionId: {}", simpSessionId);
        log.error("  - userId: {}", userId);
    }
    
    log.info("========================================");
    log.info("◀ WebSocket 연결 완료 처리 종료");
    log.info("========================================");
}
```

---

### WebSocketEventListener.java - onDisconnect()

```java
@EventListener
public void onDisconnect(SessionDisconnectEvent event) {
    log.info("========================================");
    log.info("▶ WebSocket 연결 해제 이벤트");
    log.info("========================================");
    
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

    // 1. simpSessionId 추출 ✅
    String simpSessionId = accessor.getSessionId();
    String closeStatus = event.getCloseStatus() != null ? 
        event.getCloseStatus().toString() : "UNKNOWN";
    
    log.info("📌 simpSessionId: {}", simpSessionId);
    log.info("📌 closeStatus: {}", closeStatus);

    // 2. simpSessionAttributes에서 사용자 정보 추출 ✅
    String userId = null;
    String userName = null;
    String userRole = null;
    
    if (accessor.getSessionAttributes() != null) {
        Object userIdObj = accessor.getSessionAttributes().get("userId");
        Object userNameObj = accessor.getSessionAttributes().get("userName");
        Object userRoleObj = accessor.getSessionAttributes().get("userRole");
        
        if (userIdObj != null) {
            userId = userIdObj.toString();
            log.info("  ✓ userId: {}", userId);
        }
        if (userNameObj != null) {
            userName = userNameObj.toString();
            log.info("  ✓ userName: {}", userName);
        }
        if (userRoleObj != null) {
            userRole = userRoleObj.toString();
            log.info("  ✓ userRole: {}", userRole);
        }
    }
    
    // 3. Redis에서 세션 정보 제거 ✅
    if (simpSessionId != null) {
        log.info("💾 Redis에서 세션 정보 제거 시작...");
        log.info("  - sessionId (simpSessionId): {}", simpSessionId);
        log.info("  - userId: {}", userId);
        
        webSocketSessionService.unregisterSession(simpSessionId);
        
        log.info("✅ Redis에서 세션 제거 완료!");
        log.info("  - 삭제된 Redis Key: ws:session:{}", simpSessionId);
    } else {
        log.error("❌ Redis 세션 제거 실패 - simpSessionId가 null입니다.");
    }
    
    // 4. 채팅방 멤버 제거
    roomRepository.removeMemberFromAll(simpSessionId);
    
    log.info("========================================");
    log.info("◀ WebSocket 연결 해제 처리 종료");
    log.info("========================================");
}
```

---

## 📊 실제 실행 로그 예시

### 고객 접속 시 로그

```
========================================
▶ WebSocket 연결 완료 이벤트
========================================
📌 simpSessionId: 4azgoisg
📦 simpSessionAttributes 추출 중...
  ✓ userId: cust01
  ✓ userName: 홍길철
  ✓ userRole: CUSTOMER
  ✓ userEmail: cust01@example.com
  ✓ companyId: apt001
  ✓ roomId: room-307540f4
💾 Redis에 세션 정보 저장 시작...
  - sessionId (simpSessionId): 4azgoisg
  - userId: cust01
  - userRole: CUSTOMER
WebSocket 세션 등록 - sessionId: 4azgoisg, userId: cust01, role: CUSTOMER
✅ Redis에 세션 등록 완료!
  - Redis Key: ws:session:4azgoisg
  - Redis Value: cust01
========================================
◀ WebSocket 연결 완료 처리 종료
========================================
```

---

### 고객 접속 해제 시 로그

```
========================================
▶ WebSocket 연결 해제 이벤트
========================================
📌 simpSessionId: 4azgoisg
📌 closeStatus: CloseStatus[code=1000, reason=null]
  ✓ userId: cust01
  ✓ userName: 홍길철
  ✓ userRole: CUSTOMER
💾 Redis에서 세션 정보 제거 시작...
  - sessionId (simpSessionId): 4azgoisg
  - userId: cust01
WebSocket 세션 해제 - sessionId: 4azgoisg
✅ Redis에서 세션 제거 완료!
  - 삭제된 Redis Key: ws:session:4azgoisg
========================================
◀ WebSocket 연결 해제 처리 종료
========================================
```

---

## 🧪 Redis 저장 확인

### 1. 고객 접속 후 Redis 확인

```bash
# Redis CLI 접속
redis-cli

# 세션 ID로 사용자 ID 조회
GET ws:session:4azgoisg
# "cust01"

# 세션의 역할 조회
GET ws:session:4azgoisg:role
# "CUSTOMER"

# 사용자의 모든 세션 조회
SMEMBERS ws:user:cust01
# 1) "4azgoisg"

# 전체 활성 세션 조회
SMEMBERS ws:sessions:all
# 1) "4azgoisg"
```

---

### 2. API로 확인

```bash
# 세션 정보 조회
curl http://localhost:28070/api/session/4azgoisg

# 응답:
{
  "sessionId": "4azgoisg",
  "userId": "cust01",
  "userRole": "CUSTOMER"
}

# 사용자의 모든 세션 조회
curl http://localhost:28070/api/session/user/cust01

# 응답:
{
  "userId": "cust01",
  "isOnline": true,
  "sessionCount": 1,
  "sessions": ["4azgoisg"]
}

# 온라인 상태 확인
curl http://localhost:28070/api/session/user/cust01/online

# 응답:
{
  "userId": "cust01",
  "isOnline": true,
  "sessionCount": 1
}
```

---

## 🔍 데이터 흐름

### 연결 시 (onConnected)

```
1. 고객이 chat-customer.html 접속
   ↓
2. WebSocket 연결 요청
   ↓
3. 서버에서 SessionConnectedEvent 발생
   ↓
4. WebSocketEventListener.onConnected() 호출
   ↓
5. StompHeaderAccessor로 메시지 래핑
   ↓
6. accessor.getSessionId() → simpSessionId 추출
   ↓
7. accessor.getSessionAttributes() → userId, userRole 등 추출
   ↓
8. webSocketSessionService.registerSession(simpSessionId, userId, userRole)
   ↓
9. Redis 저장:
   - ws:session:4azgoisg = "cust01"
   - ws:session:4azgoisg:role = "CUSTOMER"
   - ws:user:cust01 += "4azgoisg"
   - ws:sessions:all += "4azgoisg"
```

---

### 연결 해제 시 (onDisconnect)

```
1. 고객이 브라우저 닫기 또는 연결 종료
   ↓
2. WebSocket 연결 종료
   ↓
3. 서버에서 SessionDisconnectEvent 발생
   ↓
4. WebSocketEventListener.onDisconnect() 호출
   ↓
5. StompHeaderAccessor로 메시지 래핑
   ↓
6. accessor.getSessionId() → simpSessionId 추출
   ↓
7. webSocketSessionService.unregisterSession(simpSessionId)
   ↓
8. Redis에서 제거:
   - ws:session:4azgoisg 삭제
   - ws:session:4azgoisg:role 삭제
   - ws:user:cust01에서 "4azgoisg" 제거
   - ws:sessions:all에서 "4azgoisg" 제거
```

---

## 📊 다중 세션 테스트

### 시나리오: 한 사용자가 PC + 모바일 접속

```bash
# 1. PC에서 접속
# simpSessionId: abc123
# userId: cust01

# Redis 상태:
GET ws:session:abc123
# "cust01"

SMEMBERS ws:user:cust01
# 1) "abc123"

# 2. 모바일에서 접속 (같은 사용자)
# simpSessionId: def456
# userId: cust01

# Redis 상태:
GET ws:session:def456
# "cust01"

SMEMBERS ws:user:cust01
# 1) "abc123"
# 2) "def456"

SMEMBERS ws:sessions:all
# 1) "abc123"
# 2) "def456"

# 3. API로 확인
curl http://localhost:28070/api/session/user/cust01

# 응답:
{
  "userId": "cust01",
  "isOnline": true,
  "sessionCount": 2,
  "sessions": ["abc123", "def456"]
}

# 4. PC 연결 종료
# "abc123" 세션 제거

SMEMBERS ws:user:cust01
# 1) "def456"  (모바일만 남음)

# 5. 모바일 연결 종료
# "def456" 세션 제거

SMEMBERS ws:user:cust01
# (empty list - 모두 제거됨)

curl http://localhost:28070/api/session/user/cust01/online
# { "isOnline": false, "sessionCount": 0 }
```

---

## ⚙️ 로그 레벨 설정

### application.yml

```yaml
logging:
  level:
    aicc.chat.websocket.WebSocketEventListener: INFO
    aicc.chat.service.WebSocketSessionService: INFO
```

**로그 레벨 옵션:**
- `DEBUG`: 상세한 디버깅 정보
- `INFO`: 일반 정보 (권장)
- `WARN`: 경고만
- `ERROR`: 에러만

---

## 🐛 문제 해결

### 문제 1: simpSessionId가 null

**증상:**
```
❌ Redis 세션 등록 실패 - sessionId 또는 userId가 null입니다.
  - simpSessionId: null
  - userId: cust01
```

**원인:** `accessor.getSessionId()`가 null 반환

**해결:**
```java
// 대체 방법
String simpSessionId = (String) accessor.getMessageHeaders().get("simpSessionId");
```

---

### 문제 2: userId가 null

**증상:**
```
❌ Redis 세션 등록 실패 - sessionId 또는 userId가 null입니다.
  - simpSessionId: 4azgoisg
  - userId: null
```

**원인:** `simpSessionAttributes`에 `userId`가 없음

**해결:**
- WebSocket 연결 시 `HandshakeInterceptor`에서 `userId` 설정 확인
- 로그인 시 세션에 `userId` 저장 확인

---

### 문제 3: simpSessionAttributes가 null

**증상:**
```
⚠️ simpSessionAttributes가 null입니다!
```

**원인:** WebSocket 핸드셰이크 시 속성이 설정되지 않음

**해결:**
```java
// WebSocketConfig.java의 HandshakeInterceptor 확인
@Override
public boolean beforeHandshake(ServerHttpRequest request, 
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler, 
                               Map<String, Object> attributes) {
    // attributes에 userId, userRole 등 설정
    attributes.put("userId", userId);
    attributes.put("userRole", userRole);
    return true;
}
```

---

## ✅ 체크리스트

### 구현 확인
- [x] `simpSessionId` 추출
- [x] `simpSessionAttributes`에서 `userId` 추출
- [x] `simpSessionAttributes`에서 `userRole` 추출
- [x] Redis 세션 등록 (`registerSession`)
- [x] Redis 세션 제거 (`unregisterSession`)
- [x] 로그 출력 개선

### 테스트 확인
- [x] 고객 접속 시 Redis 저장 확인
- [x] 고객 접속 해제 시 Redis 삭제 확인
- [x] 다중 세션 (PC + 모바일) 확인
- [x] API로 세션 조회 확인

---

## 🎉 완료!

`simpSessionId`와 `simpSessionAttributes`에서 정확하게 값을 추출하여 Redis에 저장합니다!

**핵심 개선사항:**
- ✅ `accessor.getSessionId()` → simpSessionId
- ✅ `accessor.getSessionAttributes().get("userId")` → userId
- ✅ `accessor.getSessionAttributes().get("userRole")` → userRole
- ✅ 상세한 로그 출력
- ✅ 에러 처리 강화

**테스트:**
```bash
# 1. 서버 실행
./gradlew bootRun

# 2. 고객 접속 (chat-customer.html)
# 3. 로그 확인
# 4. Redis 확인
redis-cli
GET ws:session:4azgoisg
SMEMBERS ws:user:cust01
```

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23
