# WebSocket 연결 해제 시 상담원 오프라인 처리 가이드

> **작성일**: 2026-01-23  
> **주제**: WebSocketEventListener에서 상담원 세션 연결 해제 감지 및 온라인 상태 자동 제거  
> **관련 파일**: `WebSocketEventListener.java`, `RoomRepository.java`, `RedisRoomRepository.java`

---

## 📋 목차

1. [개요](#-개요)
2. [수정 사항](#-수정-사항)
3. [동작 원리](#-동작-원리)
4. [RoomRepository 상세 설명](#-roomrepository-상세-설명)
5. [테스트 시나리오](#-테스트-시나리오)
6. [주의사항](#️-주의사항)

---

## 📝 개요

### 문제 상황

기존에는 상담원이 브라우저를 닫거나 네트워크가 끊겨도:
- **하트비트만으로 처리**: 10분 TTL 만료를 기다려야 함
- **즉시 반영 안 됨**: 다른 고객이 해당 상담원을 "온라인"으로 보는 지연 발생

### 해결 방안

**WebSocket 연결 해제 이벤트 활용**:
- 상담원이 연결을 끊으면 즉시 Redis에서 온라인 상태 제거
- 하트비트(10분 TTL)와 이중 안전장치 구성
- 실시간 가용성 반영

---

## 🔧 수정 사항

### 1. WebSocketEventListener.java

#### 변경 전
```java
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {
    private final RoomRepository roomRepository;
    
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        roomRepository.removeMemberFromAll(sessionId);
    }
}
```

**문제점**:
- 방 멤버만 제거
- 상담원 온라인 상태는 그대로 유지 (TTL 만료 대기)

---

#### 변경 후
```java
package aicc.chat.websocket;

import aicc.chat.domain.UserRole;
import aicc.chat.service.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;
    
    private static final String ONLINE_AGENTS_KEY = "chat:online:agents";

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.info("ㅁㅁㅁ onDisconnect: 세션 연결 해제 - sessionId={}, closeStatus={}", 
                 event.getSessionId(), event.getCloseStatus());
        
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        
        // 1️⃣ 모든 방에서 세션 ID로 멤버 제거
        roomRepository.removeMemberFromAll(sessionId);
        
        // 2️⃣ 상담원인 경우 Redis 온라인 상태 제거
        if (sha.getSessionAttributes() != null) {
            Object userIdObj = sha.getSessionAttributes().get("userId");
            Object userRoleObj = sha.getSessionAttributes().get("userRole");
            
            if (userIdObj != null && userRoleObj != null) {
                String userId = userIdObj.toString();
                String userRoleStr = userRoleObj.toString();
                
                // 상담원(AGENT)인 경우에만 Redis 온라인 상태 제거
                if ("AGENT".equals(userRoleStr) || UserRole.AGENT.toString().equals(userRoleStr)) {
                    String agentKey = ONLINE_AGENTS_KEY + ":" + userId;
                    Boolean deleted = redisTemplate.delete(agentKey);
                    
                    if (Boolean.TRUE.equals(deleted)) {
                        log.info("✅ 상담원 오프라인 처리 완료 - userId={}, sessionId={}", 
                                 userId, sessionId);
                    } else {
                        log.warn("⚠️ 상담원 온라인 키 삭제 실패 (이미 만료됨?) - userId={}, sessionId={}", 
                                 userId, sessionId);
                    }
                }
            }
        }
        
        log.debug("세션 연결 해제 처리 완료 - sessionId={}", sessionId);
    }
}
```

**개선 사항**:
1. **즉시 오프라인 처리**: Redis 키 삭제로 실시간 반영
2. **역할 기반 처리**: 상담원(AGENT)만 온라인 상태 제거
3. **로깅 강화**: 성공/실패 상황 명확히 기록

---

## 🎯 동작 원리

### 전체 흐름도

```
[상담원 브라우저]
    ↓
    | 네트워크 끊김 또는 브라우저 닫기
    ↓
[WebSocket 서버]
    ↓
    | SessionDisconnectEvent 발생
    ↓
[WebSocketEventListener.onDisconnect()]
    ↓
    ├─ 1️⃣ 모든 방에서 멤버 제거 (roomRepository.removeMemberFromAll)
    │    ↓
    │    | Redis: chat:room:{roomId} 에서 sessionId 제거
    │
    └─ 2️⃣ 세션 속성에서 userRole 확인
         ↓
         | userRole == "AGENT"?
         ↓
         ├─ YES → Redis 온라인 키 삭제
         │    ↓
         │    | DELETE chat:online:agents:{userId}
         │    ↓
         │    | 로그: "✅ 상담원 오프라인 처리 완료"
         │
         └─ NO → 일반 고객, 처리 안 함
              ↓
              | 로그: "세션 연결 해제 처리 완료"
```

---

### 세션 속성 (Session Attributes)

WebSocket 연결 시 `StompHandler`에서 세션 속성을 설정합니다:

```java
// StompHandler.java에서 설정 (예시)
sessionAttributes.put("userId", userInfo.getUserId());
sessionAttributes.put("userRole", userInfo.getRole().toString());
sessionAttributes.put("userName", userInfo.getUserName());
```

**SessionDisconnectEvent에서 활용**:
```java
Object userRoleObj = sha.getSessionAttributes().get("userRole");
// "AGENT", "CUSTOMER", "BOT", "SYSTEM"
```

---

### 이중 안전장치 (하트비트 + 연결 해제)

| 시나리오 | 하트비트 TTL | WebSocket 연결 해제 | 결과 |
|---------|-------------|---------------------|------|
| 정상 로그아웃 | 10분 후 만료 | 즉시 삭제 ✅ | **즉시 오프라인** |
| 브라우저 강제 종료 | 10분 후 만료 | 즉시 삭제 ✅ | **즉시 오프라인** |
| 네트워크 끊김 | 10분 후 만료 | 즉시 삭제 ✅ | **즉시 오프라인** |
| 서버 재시작 | Redis 유지 | 연결 없음 | 10분 후 TTL 만료 |
| 클라이언트 버그 (하트비트 실패) | 10분 후 만료 | 정상 연결 유지 | 10분 후 오프라인 |

**결론**: 두 메커니즘이 상호 보완하여 안전성 보장

---

## 📦 RoomRepository 상세 설명

### 개요

`RoomRepository`는 **채팅방 생명주기를 관리하는 핵심 인터페이스**입니다.

**주요 책임**:
1. 채팅방 CRUD (생성, 조회, 삭제)
2. 방 멤버 관리 (추가, 제거)
3. 방 상태 관리 (라우팅 모드, 상담원 배정)
4. 활동 추적 (마지막 활동 시간)

---

### 인터페이스 정의

```java
package aicc.chat.service;

import aicc.chat.domain.ChatRoom;
import java.util.List;

public interface RoomRepository {
    // ═══════════════════════════════════════════════════════
    // 1. 채팅방 생성/조회/삭제
    // ═══════════════════════════════════════════════════════
    
    /**
     * 방 생성 (roomId 자동 생성 - UUID)
     * @param name 방 이름
     * @return 생성된 ChatRoom 객체
     */
    ChatRoom createRoom(String name);
    
    /**
     * 방 생성 (roomId 지정)
     * @param roomId 지정할 방 ID
     * @param name 방 이름
     * @return 생성된 ChatRoom 객체
     */
    ChatRoom createRoom(String roomId, String name);
    
    /**
     * roomId로 방 조회
     * @param roomId 방 ID
     * @return ChatRoom 객체 (없으면 null 또는 빈 객체)
     */
    ChatRoom findRoomById(String roomId);
    
    /**
     * 전체 방 목록 조회
     * @return 모든 ChatRoom 리스트
     */
    List<ChatRoom> findAllRooms();
    
    /**
     * 방 삭제 (모든 관련 데이터 제거)
     * @param roomId 삭제할 방 ID
     */
    void deleteRoom(String roomId);

    // ═══════════════════════════════════════════════════════
    // 2. 방 멤버 관리
    // ═══════════════════════════════════════════════════════
    
    /**
     * 방에 멤버 추가
     * @param roomId 방 ID
     * @param memberId 멤버 ID (userId 또는 sessionId)
     */
    void addMember(String roomId, String memberId);
    
    /**
     * 방에서 멤버 제거
     * @param roomId 방 ID
     * @param memberId 멤버 ID
     */
    void removeMember(String roomId, String memberId);
    
    /**
     * 모든 방에서 특정 멤버 제거
     * (WebSocket 연결 해제 시 사용)
     * @param memberId 제거할 멤버 ID
     */
    void removeMemberFromAll(String memberId);

    // ═══════════════════════════════════════════════════════
    // 3. 방 상태 관리
    // ═══════════════════════════════════════════════════════
    
    /**
     * 방 라우팅 모드 설정
     * @param roomId 방 ID
     * @param mode 라우팅 모드 ("BOT", "WAITING", "AGENT", "CLOSED")
     */
    void setRoutingMode(String roomId, String mode);
    
    /**
     * 방 라우팅 모드 조회
     * @param roomId 방 ID
     * @return 라우팅 모드 문자열
     */
    String getRoutingMode(String roomId);
    
    /**
     * 방에 상담원 배정 (설정)
     * @param roomId 방 ID
     * @param agentName 상담원 이름 (null이면 배정 해제)
     */
    void setAssignedAgent(String roomId, String agentName);
    
    /**
     * 방에 배정된 상담원 조회
     * @param roomId 방 ID
     * @return 상담원 이름 (없으면 null)
     */
    String getAssignedAgent(String roomId);
    
    /**
     * 원자적으로 상담원 배정 시도 (최초 배정만 성공)
     * Redis의 SETNX를 사용하여 동시성 제어
     * @param roomId 방 ID
     * @param agentName 상담원 이름
     * @return 배정 성공 시 true, 이미 배정된 경우 false
     */
    boolean assignAgent(String roomId, String agentName);

    // ═══════════════════════════════════════════════════════
    // 4. 활동 추적
    // ═══════════════════════════════════════════════════════
    
    /**
     * 방의 마지막 활동 시간 갱신
     * (메시지 전송, 상담원 배정 등에서 호출)
     * @param roomId 방 ID
     */
    void updateLastActivity(String roomId);
}
```

---

### Redis 구현 (RedisRoomRepository)

#### Redis 키 구조

```
채팅방 관련 키:
chat:rooms                              → Set: 모든 roomId 인덱스
chat:room:{roomId}                      → Set: 방 멤버 목록 (userId 또는 sessionId)
chat:room:{roomId}:name                 → String: 방 이름
chat:room:{roomId}:mode                 → String: 라우팅 모드 ("BOT", "AGENT", etc.)
chat:room:{roomId}:assignedAgent        → String: 배정된 상담원 이름
chat:room:{roomId}:createdAt            → String: 생성 시간 (밀리초)
chat:room:{roomId}:lastActivity         → String: 마지막 활동 시간 (밀리초)

온라인 상담원 키:
chat:online:agents:{userId}             → String: 상담원 이름 (TTL: 10분)
```

---

#### 주요 메서드 동작 원리

##### 1. `createRoom(String roomId, String name)`

```java
public ChatRoom createRoom(String roomId, String name) {
    long now = System.currentTimeMillis();
    
    // 1️⃣ roomId를 전체 인덱스에 등록
    redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
    
    // 2️⃣ 방 메타데이터 저장
    if (name != null) {
        redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":name", name);
    }
    redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":createdAt", String.valueOf(now));
    
    // 3️⃣ 마지막 활동 시간 초기화
    updateLastActivity(roomId);
    
    // 4️⃣ ChatRoom 객체 반환
    return ChatRoom.builder()
            .roomId(roomId)
            .roomName(name)
            .members(new HashSet<>())
            .status("BOT")
            .createdAt(now)
            .lastActivityAt(now)
            .build();
}
```

**Redis 명령어 예시**:
```bash
SADD chat:rooms "room-001"
SET chat:room:room-001:name "고객-홍길동"
SET chat:room:room-001:createdAt "1737619200000"
SET chat:room:room-001:lastActivity "1737619200000"
```

---

##### 2. `findRoomById(String roomId)`

```java
public ChatRoom findRoomById(String roomId) {
    // 1️⃣ 분산된 Redis 키들을 조회
    Set<String> members = redisTemplate.opsForSet().members(ROOM_KEY_PREFIX + roomId);
    String name = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":name");
    String status = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":mode");
    String assignedAgent = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
    String createdAtStr = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":createdAt");
    String lastActivityStr = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":lastActivity");
    
    // 2️⃣ 파싱
    long createdAt = createdAtStr != null ? Long.parseLong(createdAtStr) : 0;
    long lastActivityAt = lastActivityStr != null ? Long.parseLong(lastActivityStr) : 0;
    
    // 3️⃣ ChatRoom 객체로 합성
    return ChatRoom.builder()
            .roomId(roomId)
            .roomName(name == null ? roomId : name)
            .members(members == null ? Collections.emptySet() : members)
            .status(status == null ? "BOT" : status)
            .assignedAgent(assignedAgent)
            .createdAt(createdAt)
            .lastActivityAt(lastActivityAt)
            .build();
}
```

**특징**:
- Redis는 NoSQL이므로 여러 키를 조회하여 **객체를 합성**
- 기본값 처리: 값이 없으면 기본값 설정 (status="BOT" 등)

---

##### 3. `addMember(String roomId, String memberId)`

```java
public void addMember(String roomId, String memberId) {
    // Set에 멤버 추가 (자동 중복 제거)
    redisTemplate.opsForSet().add(ROOM_KEY_PREFIX + roomId, memberId);
    
    // 방이 인덱스에 없으면 추가
    redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
}
```

**Redis 명령어 예시**:
```bash
SADD chat:room:room-001 "agent01"
SADD chat:room:room-001 "customer-hong"
SADD chat:rooms "room-001"
```

**사용 시점**:
- 고객이 방에 입장할 때
- 상담원이 방에 배정될 때
- WebSocket에서 토픽 구독 시 (`onSubscribe`)

---

##### 4. `removeMemberFromAll(String memberId)`

```java
public void removeMemberFromAll(String memberId) {
    // 1️⃣ 전체 방 인덱스 조회
    Set<String> roomIds = redisTemplate.opsForSet().members(ROOM_INDEX_KEY);
    
    // 2️⃣ 각 방에서 멤버 제거
    if (roomIds != null) {
        for (String roomId : roomIds) {
            redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId, memberId);
        }
    }
}
```

**Redis 명령어 예시**:
```bash
# 1. 전체 방 조회
SMEMBERS chat:rooms
# ["room-001", "room-002", "room-003"]

# 2. 각 방에서 제거
SREM chat:room:room-001 "sessionId-abc123"
SREM chat:room:room-002 "sessionId-abc123"
SREM chat:room:room-003 "sessionId-abc123"
```

**사용 시점**:
- WebSocket 연결 해제 시 (`onDisconnect`)
- 고객 또는 상담원이 모든 방에서 나갈 때

---

##### 5. `assignAgent(String roomId, String agentName)` - 원자적 배정

```java
public boolean assignAgent(String roomId, String agentName) {
    if (roomId == null || agentName == null) return false;
    
    // 1️⃣ SETNX (setIfAbsent): 키가 없을 때만 설정
    Boolean success = redisTemplate.opsForValue()
        .setIfAbsent(ROOM_KEY_PREFIX + roomId + ":assignedAgent", agentName);
    
    if (Boolean.TRUE.equals(success)) {
        // 2️⃣ 배정 성공 시 모드도 AGENT로 변경
        setRoutingMode(roomId, "AGENT");
        updateLastActivity(roomId);
        return true;
    }
    
    // 3️⃣ 이미 배정된 경우 false 반환
    return false;
}
```

**Redis 명령어 예시**:
```bash
# 첫 번째 상담원 (성공)
SETNX chat:room:room-001:assignedAgent "agent01"
# (integer) 1  ← 성공

# 두 번째 상담원 (실패)
SETNX chat:room:room-001:assignedAgent "agent02"
# (integer) 0  ← 이미 존재하므로 실패
```

**특징**:
- **동시성 제어**: 여러 상담원이 동시에 배정 시도해도 안전
- **원자적 연산**: Redis SETNX는 원자적(Atomic)으로 실행됨
- **첫 번째만 성공**: 먼저 요청한 상담원만 배정됨

---

##### 6. `deleteRoom(String roomId)`

```java
public void deleteRoom(String roomId) {
    // 1️⃣ 인덱스에서 제거
    redisTemplate.opsForSet().remove(ROOM_INDEX_KEY, roomId);
    
    // 2️⃣ 모든 관련 키 삭제
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId);                    // 멤버 Set
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":name");          // 방 이름
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":mode");          // 라우팅 모드
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent"); // 배정 상담원
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":lastActivity");  // 활동 시간
    redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":createdAt");     // 생성 시간
}
```

**Redis 명령어 예시**:
```bash
SREM chat:rooms "room-001"
DEL chat:room:room-001
DEL chat:room:room-001:name
DEL chat:room:room-001:mode
DEL chat:room:room-001:assignedAgent
DEL chat:room:room-001:lastActivity
DEL chat:room:room-001:createdAt
```

---

### RoomRepository의 역할 요약

| 역할 | 메서드 | 설명 |
|------|--------|------|
| **방 생명주기 관리** | `createRoom()`, `deleteRoom()` | 방 생성/삭제 및 인덱스 관리 |
| **방 조회** | `findRoomById()`, `findAllRooms()` | 분산 키를 조합하여 객체 복원 |
| **멤버 관리** | `addMember()`, `removeMember()`, `removeMemberFromAll()` | 방 참여자 추가/제거 |
| **상태 관리** | `setRoutingMode()`, `getRoutingMode()` | 방의 라우팅 상태 (BOT/AGENT/WAITING/CLOSED) |
| **상담원 배정** | `assignAgent()`, `setAssignedAgent()`, `getAssignedAgent()` | 상담원 배정 및 동시성 제어 |
| **활동 추적** | `updateLastActivity()` | 유휴 방 감지를 위한 시간 기록 |

---

## 🧪 테스트 시나리오

### 시나리오 1: 정상 로그아웃 ✅

```
📍 초기 상태
- agent01 로그인 중
- Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)
- WebSocket 세션: sessionId-abc123

📍 동작
1. 상담원이 로그아웃 버튼 클릭
2. `logout()` 호출 → `stopHeartbeat()` → WebSocket 연결 종료
3. `SessionDisconnectEvent` 발생

📍 WebSocketEventListener 처리
1. sessionId로 모든 방에서 멤버 제거
2. 세션 속성 확인: userRole = "AGENT"
3. Redis 키 삭제: chat:online:agents:agent01
4. 로그: "✅ 상담원 오프라인 처리 완료 - userId=agent01"

📍 결과
- Redis: 키 삭제됨 (즉시)
- 다른 고객 화면: 30초 이내에 버튼 비활성화 (폴링 주기)

✅ 성공: 즉시 오프라인 처리
```

---

### 시나리오 2: 브라우저 강제 종료 ✅

```
📍 초기 상태
- agent01 로그인 중
- Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)

📍 동작
1. 상담원이 브라우저 강제 종료 (Alt+F4 또는 작업 관리자)
2. 하트비트 중단
3. WebSocket 연결 자동 종료 (서버 감지)
4. `SessionDisconnectEvent` 발생

📍 WebSocketEventListener 처리
1. sessionId로 모든 방에서 멤버 제거
2. 세션 속성 확인: userRole = "AGENT"
3. Redis 키 삭제: chat:online:agents:agent01
4. 로그: "✅ 상담원 오프라인 처리 완료 - userId=agent01"

📍 결과
- Redis: 키 삭제됨 (즉시, 보통 몇 초 이내)
- 고객 화면: 30초 이내에 버튼 비활성화

✅ 성공: 브라우저 종료해도 즉시 반영
```

---

### 시나리오 3: 네트워크 끊김 ✅

```
📍 초기 상태
- agent01 로그인 중
- Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)

📍 동작
1. 상담원의 네트워크 케이블 분리 또는 Wi-Fi 끊김
2. WebSocket 연결 끊김 감지 (TCP timeout, 보통 30초~1분)
3. `SessionDisconnectEvent` 발생

📍 WebSocketEventListener 처리
1. sessionId로 모든 방에서 멤버 제거
2. 세션 속성 확인: userRole = "AGENT"
3. Redis 키 삭제: chat:online:agents:agent01
4. 로그: "✅ 상담원 오프라인 처리 완료 - userId=agent01"

📍 결과
- Redis: 키 삭제됨 (네트워크 끊김 감지 후)
- 고객 화면: 최대 2분 이내 버튼 비활성화 (TCP timeout + 폴링)

✅ 성공: 네트워크 장애도 자동 처리
```

---

### 시나리오 4: 고객 연결 해제 (무시) ✅

```
📍 초기 상태
- customer-hong 로그인 중
- WebSocket 세션: sessionId-xyz789

📍 동작
1. 고객이 브라우저 닫기
2. WebSocket 연결 종료
3. `SessionDisconnectEvent` 발생

📍 WebSocketEventListener 처리
1. sessionId로 모든 방에서 멤버 제거
2. 세션 속성 확인: userRole = "CUSTOMER"
3. 상담원 아님 → Redis 처리 건너뛰기
4. 로그: "세션 연결 해제 처리 완료 - sessionId=xyz789"

📍 결과
- Redis: chat:online:agents 키 영향 없음 (고객은 추적 안 함)
- 방 멤버만 제거됨

✅ 성공: 고객은 온라인 추적 대상 아님
```

---

### 시나리오 5: TTL 만료 (백업 메커니즘) ✅

```
📍 초기 상태
- agent01 로그인 중
- Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)

📍 동작 (비정상 케이스)
1. WebSocket 연결은 유지되지만 서버 버그로 `onDisconnect` 미호출
2. 또는 Redis 키 삭제 실패
3. 하트비트도 실패 (클라이언트 버그)

📍 10분 경과
1. Redis TTL 만료
2. chat:online:agents:agent01 자동 삭제

📍 결과
- Redis: 키 자동 삭제됨 (TTL 메커니즘)
- 고객 화면: 다음 폴링 시 버튼 비활성화

✅ 성공: TTL이 백업 안전장치 역할
```

---

## ⚠️ 주의사항

### 1. 세션 속성 설정 필수

**문제**: 세션 속성에 `userRole`이 없으면 상담원 감지 불가

**해결**: `StompHandler`에서 반드시 설정

```java
// StompHandler.java
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
    
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
        // ... 토큰 검증 ...
        
        // ✅ 세션 속성 설정 필수
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put("userId", userInfo.getUserId());
            sessionAttributes.put("userRole", userInfo.getRole().toString()); // 중요!
            sessionAttributes.put("userName", userInfo.getUserName());
        }
    }
    
    return message;
}
```

---

### 2. Redis 키 삭제 실패 처리

**경우 1**: 키가 이미 만료됨 (TTL)
```java
Boolean deleted = redisTemplate.delete(agentKey);
if (Boolean.TRUE.equals(deleted)) {
    log.info("✅ 삭제 성공");
} else {
    log.warn("⚠️ 키 없음 (이미 만료?)");  // 정상 케이스
}
```

**경우 2**: Redis 연결 실패
```java
try {
    Boolean deleted = redisTemplate.delete(agentKey);
    // ...
} catch (Exception e) {
    log.error("❌ Redis 오류 - 온라인 상태 제거 실패: {}", e.getMessage());
    // TTL이 백업 안전장치이므로 예외는 로그만 기록
}
```

---

### 3. 동시 로그인 처리

**시나리오**: 같은 상담원이 두 브라우저에서 로그인

```
브라우저 A: agent01 로그인 → Redis 등록
브라우저 B: agent01 로그인 → Redis 덮어쓰기 (TTL 갱신)
브라우저 A: 연결 해제 → Redis 키 삭제
브라우저 B: 여전히 연결 중이지만 온라인 상태 삭제됨! ❌
```

**해결 방안**:
1. **로그인 시 기존 세션 강제 종료** (권장)
2. **Redis Set으로 세션 ID 목록 관리**

```java
// 해결 방안 1: 로그인 시 기존 세션 종료
@PostMapping("/login")
public ResponseEntity<UserInfo> login(...) {
    // ... 인증 ...
    
    // 기존 세션 종료 (SimpMessagingTemplate 사용)
    messagingTemplate.convertAndSendToUser(
        userId, 
        "/queue/logout", 
        "새로운 세션에서 로그인했습니다."
    );
    
    // 새 세션 등록
    // ...
}
```

---

### 4. 로깅 레벨 설정

```yaml
# application.yml
logging:
  level:
    aicc.chat.websocket.WebSocketEventListener: INFO
    aicc.chat.service.impl.RedisRoomRepository: DEBUG
```

**프로덕션**: INFO 레벨 권장
**개발/디버깅**: DEBUG 레벨로 상세 추적

---

## 📊 비교표: 하트비트 vs 연결 해제

| 항목 | 하트비트 (TTL) | WebSocket 연결 해제 |
|------|----------------|---------------------|
| **감지 속도** | 최대 10분 | 즉시 (수 초) |
| **정확도** | 중간 (클라이언트 의존) | 높음 (서버 감지) |
| **네트워크 장애** | 감지 지연 | 즉시 감지 |
| **브라우저 종료** | 감지 지연 | 즉시 감지 |
| **서버 재시작** | 유지 (복구 가능) | 모두 끊김 |
| **클라이언트 버그** | 감지 가능 | 감지 못함 |
| **오버헤드** | 낮음 (5분 간격) | 거의 없음 (이벤트) |
| **안정성** | 높음 (백업) | 중간 (보완 필요) |

**결론**: 두 메커니즘을 함께 사용하여 상호 보완

---

## 🎉 결론

### 개선 사항 요약

**Before ❌**:
- 하트비트 TTL에만 의존 (최대 10분 지연)
- 브라우저 종료나 네트워크 끊김 시 즉시 감지 불가

**After ✅**:
- WebSocket 연결 해제 즉시 감지 (수 초 이내)
- 하트비트 TTL은 백업 안전장치
- 실시간 가용성 반영

### RoomRepository 역할 정리

**핵심 개념**: 채팅방의 모든 상태를 Redis로 관리하는 Repository 패턴

**주요 역할**:
1. 방 생명주기 관리 (생성, 조회, 삭제)
2. 멤버 관리 (추가, 제거)
3. 상태 관리 (라우팅 모드, 상담원 배정)
4. 활동 추적 (유휴 방 감지)
5. 동시성 제어 (원자적 상담원 배정)

**Redis 구조**:
- 인덱스 Set: `chat:rooms`
- 방별 데이터: `chat:room:{roomId}:*`
- 분산 키를 조합하여 ChatRoom 객체 복원

### 최종 안전 메커니즘

```
[1차 방어선] WebSocket 연결 해제 감지
    ↓ (실패 시)
[2차 방어선] 하트비트 TTL 만료 (10분)
    ↓
    | 이중 안전장치로 높은 안정성 보장
```

---

**작성**: AI Assistant  
**문서 버전**: 1.0  
**최종 수정**: 2026-01-23
