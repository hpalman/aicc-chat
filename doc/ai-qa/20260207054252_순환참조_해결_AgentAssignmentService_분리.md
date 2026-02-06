# 순환 참조 해결 - AgentAssignmentService 분리

> **작성일**: 2026-02-07 05:42:52  
> **목적**: `MiChatRoutingStrategy`와 `ChatAgentService` 간 순환 참조 문제 해결  
> **방법**: 자동 배정 로직을 별도의 `AgentAssignmentService`로 분리

---

## 📋 목차

1. [문제 상황](#문제-상황)
2. [해결 방법](#해결-방법)
3. [변경 사항](#변경-사항)
4. [아키텍처 다이어그램](#아키텍처-다이어그램)
5. [테스트 방법](#테스트-방법)

---

## 문제 상황

### 순환 참조 발생

**이전 구조**:
```
MiChatRoutingStrategy
    ↓ (의존)
ChatAgentService
    ↓ (의존)
ChatRoutingStrategy (인터페이스)
    ↑ (구현)
MiChatRoutingStrategy
```

**문제점**:
- `MiChatRoutingStrategy`가 `ChatAgentService`를 주입받음
- `ChatAgentService`가 `ChatRoutingStrategy`를 주입받음
- `MiChatRoutingStrategy`는 `ChatRoutingStrategy`의 구현체
- **순환 참조 발생!** ❌

**에러 메시지**:
```
The dependencies of some of the beans in the application context form a cycle:

   miChatRoutingStrategy
      ↓
   chatAgentService
      ↓
   chatRoutingStrategy
```

---

## 해결 방법

### 별도 서비스 분리

자동 배정 로직을 **`AgentAssignmentService`**로 분리하여 순환 참조 해결

**새로운 구조**:
```
MiChatRoutingStrategy
    ↓ (의존)
AgentAssignmentService (새로 생성)
    ↓ (의존)
RoomRepository, MessageBroker, RoomUpdateBroadcaster

ChatAgentService
    ↓ (의존)
ChatRoutingStrategy (순환 참조 없음)
```

**장점**:
- ✅ 순환 참조 해결
- ✅ 단일 책임 원칙 준수 (자동 배정 로직 분리)
- ✅ 코드 재사용성 향상
- ✅ 테스트 용이성 증가

---

## 변경 사항

### 1. AgentAssignmentService.java (신규 생성)

**파일 경로**: `src/main/java/aicc/chat/service/AgentAssignmentService.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssignmentService {

    private final StringRedisTemplate redisTemplate;
    private final RoomRepository roomRepository;
    private final MessageBroker messageBroker;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;

    /**
     * 대기 중인 상담원 중 첫 번째 상담원 조회
     * @return 상담원 ID, 없으면 null
     */
    public String findWaitingAgent() {
        Set<String> onlineAgentKeys = redisTemplate.keys(Constants.USER_AGENT_KEY + ":*");
        
        if (onlineAgentKeys != null) {
            for (String key : onlineAgentKeys) {
                String agentId = key.substring((Constants.USER_AGENT_KEY + ":").length());
                
                // Hash에서 agentStatus 조회
                Object agentStatusObj = redisTemplate.opsForHash().get(key, "agentStatus");
                String agentStatus = agentStatusObj != null ? agentStatusObj.toString() : null;
                
                // agentStatus가 WAITING인 경우 반환
                if ("WAITING".equals(agentStatus)) {
                    log.info("▶ Found WAITING agent: {}", agentId);
                    return agentId;
                }
            }
        }
        
        log.info("▶ No WAITING agent found");
        return null;
    }

    /**
     * 대기 중인 상담원을 자동으로 배정
     * @param roomId 채팅방 ID
     * @return 배정 성공 여부
     */
    public boolean autoAssignWaitingAgent(String roomId) {
        log.info("▼ autoAssignWaitingAgent S. roomId:{}", roomId);
        
        // 1. 대기 중인 상담원 조회
        String agentId = findWaitingAgent();
        if (agentId == null) {
            log.info("▶ No WAITING agent available for auto-assignment");
            return false;
        }
        
        // 2. 상담원 userName 조회
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId;
        Object userNameObj = redisTemplate.opsForHash().get(agentKey, "userName");
        String agentName = userNameObj != null ? userNameObj.toString() : agentId;
        
        log.info("▶ Auto-assigning WAITING agent: {} ({}) to room: {}", agentName, agentId, roomId);
        
        // 3. 상담원 배정 시도
        boolean success = roomRepository.assignAgent(roomId, agentId);
        if (success) {
            // 4. 상담원 상태를 WORKING으로 변경
            setAgentStatus(agentId, "WORKING");
            
            // 5. 연결 알림 메시지 발송
            ChatMessage notice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(agentName + " 상담원과 연결되었습니다.")
                    .type(MessageType.TALK)
                    .timestamp(LocalDateTime.now())
                    .build();
            messageBroker.publish(notice);
            
            // 6. 방 목록 브로드캐스트
            roomUpdateBroadcaster.broadcastRoomList();
            
            log.info("▲ autoAssignWaitingAgent E. Successfully assigned agent {} to room {}", agentName, roomId);
        } else {
            log.warn("▲ autoAssignWaitingAgent E. Failed to assign agent {} to room {}", agentName, roomId);
        }
        
        return success;
    }

    /**
     * 상담원 상태 변경
     * @param agentId 상담원 ID
     * @param status 상태 (WAITING, WORKING)
     */
    private void setAgentStatus(String agentId, String status) {
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId;
        
        Boolean exists = redisTemplate.hasKey(agentKey);
        if (!exists) {
            log.warn("▶ Agent key not found: {}", agentKey);
            return;
        }
        
        Object currentStatusObj = redisTemplate.opsForHash().get(agentKey, "agentStatus");
        String currentStatus = currentStatusObj != null ? currentStatusObj.toString() : null;
        
        if (!status.equals(currentStatus)) {
            redisTemplate.opsForHash().put(agentKey, "agentStatus", status);
            log.info("▶ Agent {} status changed: {} -> {}", agentId, currentStatus, status);
            
            // 상담원 상태 변경 알림 브로드캐스트
            ChatMessage statusMessage = ChatMessage.builder()
                .roomId("SYSTEM_BROADCAST")
                .sender("System")
                .senderRole(UserRole.SYSTEM)
                .message("AGENT_STATUS")
                .type(MessageType.SYSTEM)
                .timestamp(LocalDateTime.now())
                .build();
            messageBroker.publish(statusMessage);
        }
    }
}
```

**책임**:
- 대기 중인 상담원 조회
- 자동 배정 로직 처리
- 상담원 상태 관리 (WAITING ↔ WORKING)
- 연결 알림 메시지 발송

---

### 2. MiChatRoutingStrategy.java 수정

**변경 전**:
```java
@RequiredArgsConstructor
public class MiChatRoutingStrategy implements ChatRoutingStrategy {
    private final ChatAgentService chatAgentService;  // ❌ 순환 참조
    
    // ...
    
    if (MessageType.HANDOFF.equals(message.getType())) {
        boolean autoAssigned = chatAgentService.autoAssignWaitingAgent(roomId);
        // ...
    }
}
```

**변경 후**:
```java
@RequiredArgsConstructor
public class MiChatRoutingStrategy implements ChatRoutingStrategy {
    private final AgentAssignmentService agentAssignmentService;  // ✅ 순환 참조 해결
    
    // ...
    
    if (MessageType.HANDOFF.equals(message.getType())) {
        boolean autoAssigned = agentAssignmentService.autoAssignWaitingAgent(roomId);
        // ...
    }
}
```

---

### 3. ChatRoutingConfig.java 수정

**변경 전**:
```java
@Bean
public ChatRoutingStrategy dynamicRoutingStrategy(
        MessageBroker messageBroker,
        ChatBot chatBot,
        RoomRepository roomRepository,
        RoomUpdateBroadcaster roomUpdateBroadcaster,
        ChatHistoryService chatHistoryService,
        ChatSessionService chatSessionService) {
    
    MiChatRoutingStrategy miChat = new MiChatRoutingStrategy(
            messageBroker, chatBot, roomRepository, roomUpdateBroadcaster,
            chatHistoryService, chatSessionService);  // ❌ AgentAssignmentService 없음
    // ...
}
```

**변경 후**:
```java
@Bean
public ChatRoutingStrategy dynamicRoutingStrategy(
        MessageBroker messageBroker,
        ChatBot chatBot,
        RoomRepository roomRepository,
        RoomUpdateBroadcaster roomUpdateBroadcaster,
        ChatHistoryService chatHistoryService,
        ChatSessionService chatSessionService,
        AgentAssignmentService agentAssignmentService) {  // ✅ 추가
    
    MiChatRoutingStrategy miChat = new MiChatRoutingStrategy(
            messageBroker, chatBot, roomRepository, roomUpdateBroadcaster,
            chatHistoryService, chatSessionService, agentAssignmentService);  // ✅ 주입
    // ...
}
```

---

### 4. ChatAgentService.java 수정

**변경 사항**:
- `findWaitingAgent()` 메서드 제거 → `AgentAssignmentService`로 이동
- `autoAssignWaitingAgent()` 메서드 제거 → `AgentAssignmentService`로 이동
- `ChatRoutingStrategy` 의존성 유지 (순환 참조 없음)

---

## 아키텍처 다이어그램

### Before (순환 참조 발생)

```
┌─────────────────────────────────────────────────────────────┐
│                     순환 참조 발생 구조                        │
└─────────────────────────────────────────────────────────────┘

[MiChatRoutingStrategy]
    │
    │ 의존 (주입)
    ↓
[ChatAgentService]
    │
    │ 의존 (주입)
    ↓
[ChatRoutingStrategy] (인터페이스)
    ↑
    │ 구현
    │
[MiChatRoutingStrategy]  ← 순환 참조! ❌
```

---

### After (순환 참조 해결)

```
┌─────────────────────────────────────────────────────────────┐
│                  순환 참조 해결 구조                           │
└─────────────────────────────────────────────────────────────┘

[MiChatRoutingStrategy]
    │
    │ 의존 (주입)
    ↓
[AgentAssignmentService] ← 새로 생성
    │
    ├─ StringRedisTemplate
    ├─ RoomRepository
    ├─ MessageBroker
    └─ RoomUpdateBroadcaster

[ChatAgentService]
    │
    │ 의존 (주입)
    ↓
[ChatRoutingStrategy] (인터페이스)
    ↑
    │ 구현
    │
[MiChatRoutingStrategy]  ← 순환 참조 없음! ✅
```

---

## 테스트 방법

### 1. 애플리케이션 시작 확인

```bash
# 애플리케이션 시작
./gradlew bootRun

# 또는
java -jar build/libs/aicc-chat-0.0.1-SNAPSHOT.jar
```

**확인 사항**:
- ✅ 순환 참조 에러 없이 정상 시작
- ✅ Bean 생성 로그 확인:
  ```
  ▼ dynamicRoutingStrategy
  ▼ miChatRoutingStrategy
  ```

---

### 2. 자동 배정 기능 테스트

**시나리오**:
```
1. 상담원 로그인 후 "대기" 버튼 클릭
   ↓
2. 고객이 "상담사 연결" 버튼 클릭
   ↓
3. 서버 로그 확인:
   - "▶ HANDOFF request received for room: room-xxx"
   - "▶ Found WAITING agent: agent01"
   - "▶ Auto-assigning WAITING agent: 김상담 (agent01) to room: room-xxx"
   - "▶ Auto-assignment successful, switching to AGENT mode"
   ↓
4. 고객 화면 확인:
   - "김상담 상담원과 연결되었습니다." 메시지 표시
   ↓
5. 상담원과 고객이 채팅 가능 ✅
```

---

### 3. 순환 참조 확인

**확인 방법**:
```bash
# 애플리케이션 시작 시 에러 로그 확인
grep -i "cycle" logs/application.log

# 또는 Spring Boot Actuator 사용
curl http://localhost:28070/actuator/beans | jq '.contexts.application.beans | keys'
```

**정상 동작 시**:
- ✅ "cycle" 관련 에러 없음
- ✅ `agentAssignmentService` Bean 생성 확인
- ✅ `miChatRoutingStrategy` Bean 생성 확인

---

## 변경된 파일 목록

### 신규 생성 (1개)
1. ✅ `src/main/java/aicc/chat/service/AgentAssignmentService.java`
   - 자동 배정 로직 분리

### 수정 (3개)
2. ✅ `src/main/java/aicc/chat/service/impl/MiChatRoutingStrategy.java`
   - `ChatAgentService` → `AgentAssignmentService` 의존성 변경

3. ✅ `src/main/java/aicc/chat/config/ChatRoutingConfig.java`
   - `AgentAssignmentService` 주입 추가

4. ✅ `src/main/java/aicc/chat/service/ChatAgentService.java`
   - 중복 메서드 제거 (이미 제거됨)

---

## 주의사항

### 1. 의존성 주입 순서

Spring Boot는 Bean 생성 시 다음 순서로 의존성을 해결합니다:
```
1. AgentAssignmentService 생성
   - StringRedisTemplate (이미 생성됨)
   - RoomRepository (이미 생성됨)
   - MessageBroker (이미 생성됨)
   - RoomUpdateBroadcaster (이미 생성됨)

2. MiChatRoutingStrategy 생성
   - AgentAssignmentService (위에서 생성됨)
   - 기타 의존성들

3. ChatAgentService 생성
   - ChatRoutingStrategy (MiChatRoutingStrategy 구현체)
   - 순환 참조 없음 ✅
```

---

### 2. 단일 책임 원칙 (SRP)

**AgentAssignmentService**:
- 책임: 상담원 자동 배정
- 역할: 대기 중인 상담원 조회 및 배정

**ChatAgentService**:
- 책임: 상담원 관련 비즈니스 로직
- 역할: 로그인, 로그아웃, 상태 관리, 수동 배정 등

**MiChatRoutingStrategy**:
- 책임: 메시지 라우팅
- 역할: 메시지 타입별 처리 (HANDOFF, TALK 등)

---

### 3. 테스트 용이성

**Before**:
- `MiChatRoutingStrategy` 테스트 시 `ChatAgentService` Mock 필요
- `ChatAgentService` 테스트 시 `ChatRoutingStrategy` Mock 필요
- 순환 참조로 인한 복잡한 Mock 설정

**After**:
- `MiChatRoutingStrategy` 테스트 시 `AgentAssignmentService` Mock만 필요
- `AgentAssignmentService` 독립적으로 테스트 가능
- 단순하고 명확한 테스트 구조 ✅

---

## 🎉 완료!

순환 참조 문제를 **`AgentAssignmentService`**를 분리하여 해결했습니다!

**주요 개선사항**:
- ✅ 순환 참조 해결
- ✅ 단일 책임 원칙 준수
- ✅ 코드 재사용성 향상
- ✅ 테스트 용이성 증가
- ✅ 명확한 의존성 구조

**테스트 체크리스트**:
```
□ 애플리케이션 정상 시작 (순환 참조 에러 없음)
□ 대기 중인 상담원 자동 배정 성공
□ 상담원과 고객 채팅 가능
□ Bean 생성 로그 확인
```
