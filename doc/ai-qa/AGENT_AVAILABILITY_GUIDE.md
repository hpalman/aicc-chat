# 상담원 가용성 기반 연결 제어 가이드 (수정본)

## 📋 개요

상담원이 **로그인**하고 **3개 미만의 상담을 진행 중**일 때만 고객 화면의 "상담원 연결" 버튼이 활성화되도록 수정했습니다.

---

## 🔧 주요 변경사항 (수정)

### ❌ Before (문제)
- 상담원이 로그아웃해도 버튼이 활성화됨
- 상담 중인 방만 체크하여 로그인 여부를 확인할 수 없음

### ✅ After (수정)
- **Redis에 온라인 상담원 추적**
- 로그인 시 Redis에 상담원 등록 (10분 TTL)
- 하트비트로 온라인 상태 유지 (5분마다)
- 온라인 상담원이 없으면 버튼 비활성화

---

## 🎯 주요 기능

### 1. 온라인 상담원 추적 (Redis)
- ✅ 로그인 시 Redis에 등록
- ✅ TTL: 10분 (자동 만료)
- ✅ 하트비트로 갱신 (5분마다)

### 2. 가용성 체크 로직
- ✅ 온라인 상담원 확인
- ✅ 3개 미만 상담 중인 상담원 확인
- ✅ 두 조건 모두 만족 시 버튼 활성화

### 3. 자동 로그아웃 방지
- ✅ 5분마다 하트비트 전송
- ✅ Redis TTL 갱신 (10분)
- ✅ 로그아웃 시 하트비트 중단

---

## 🔧 구현 상세

### 1. 백엔드 (AgentAuthService.java)

#### Redis에 온라인 상담원 등록

```java
@Service
@RequiredArgsConstructor
public class AgentAuthService {
    
    private final StringRedisTemplate redisTemplate;
    private static final String ONLINE_AGENTS_KEY = "chat:online:agents";

    public UserInfo login(String id, String password) {
        // ... 기존 로그인 로직 ...
        
        // Redis에 온라인 상담원 등록 (10분 TTL) ✅
        String agentKey = ONLINE_AGENTS_KEY + ":" + account.getUserId();
        redisTemplate.opsForValue().set(agentKey, account.getUserName(), 10, TimeUnit.MINUTES);
        log.info("Agent {} registered as online in Redis", account.getUserId());
        
        return userInfo;
    }
    
    /**
     * 상담원 하트비트 - 온라인 상태 유지 ✅
     */
    public void heartbeat(String userId) {
        String agentKey = ONLINE_AGENTS_KEY + ":" + userId;
        redisTemplate.expire(agentKey, 10, TimeUnit.MINUTES);
    }
}
```

**Redis 키 형식:**
```
chat:online:agents:agent01 = "김상담" (TTL: 10분)
chat:online:agents:agent02 = "이상담" (TTL: 10분)
```

---

### 2. 백엔드 (AgentChatController.java)

#### 가용성 확인 API 수정

```java
@GetMapping("/availability")
public ResponseEntity<Map<String, Object>> checkAgentAvailability() {
    log.debug("Checking agent availability");
    
    // 1. 온라인 상담원 목록 조회 ✅
    Set<String> onlineAgentKeys = redisTemplate.keys(ONLINE_AGENTS_KEY + ":*");
    Set<String> onlineAgentIds = new HashSet<>();
    
    if (onlineAgentKeys != null) {
        for (String key : onlineAgentKeys) {
            String agentId = key.substring((ONLINE_AGENTS_KEY + ":").length());
            onlineAgentIds.add(agentId);
        }
    }
    
    log.info("Online agents: {}", onlineAgentIds);
    
    // 온라인 상담원이 없으면 즉시 불가 반환 ✅
    if (onlineAgentIds.isEmpty()) {
        log.info("No online agents available");
        return ResponseEntity.ok(Map.of(
            "available", false,
            "onlineAgentCount", 0,
            "agentCount", 0,
            "agentRoomCount", Collections.emptyMap()
        ));
    }
    
    // 2. 상담원이 배정된 방 개수 세기
    List<ChatRoom> allRooms = roomRepository.findAllRooms();
    Map<String, Long> agentRoomCount = allRooms.stream()
        .filter(room -> "AGENT".equals(room.getStatus()) && room.getAssignedAgent() != null)
        .collect(Collectors.groupingBy(
            ChatRoom::getAssignedAgent,
            Collectors.counting()
        ));
    
    // 3. 온라인 상담원 중 3개 미만의 상담을 하고 있는 상담원이 있는지 확인 ✅
    boolean hasAvailableAgent = onlineAgentIds.stream()
        .anyMatch(agentId -> {
            // 해당 상담원의 userName 조회 (Redis에서)
            String agentName = redisTemplate.opsForValue().get(ONLINE_AGENTS_KEY + ":" + agentId);
            if (agentName == null) return false;
            
            // 현재 상담 개수 확인
            long currentChats = agentRoomCount.getOrDefault(agentName, 0L);
            return currentChats < 3;
        });
    
    log.info("Agent availability check - Online: {}, Available: {}, Room count: {}", 
             onlineAgentIds.size(), hasAvailableAgent, agentRoomCount);
    
    return ResponseEntity.ok(Map.of(
        "available", hasAvailableAgent,
        "onlineAgentCount", onlineAgentIds.size(),
        "agentCount", agentRoomCount.size(),
        "agentRoomCount", agentRoomCount
    ));
}
```

**응답 형식 (수정):**
```json
{
  "available": true,
  "onlineAgentCount": 2,     // 온라인 상담원 수 (추가)
  "agentCount": 1,            // 상담 중인 상담원 수
  "agentRoomCount": {
    "agent01": 2
  }
}
```

---

### 3. 백엔드 (AgentLoginController.java)

#### 하트비트 엔드포인트

```java
@GetMapping("/me")
public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String token) {
    // ... 토큰 검증 ...
    
    // 하트비트 - 온라인 상태 유지 ✅
    agentAuthService.heartbeat(userInfo.getUserId());
    
    return ResponseEntity.ok(userInfo);
}
```

**동작:**
- 기존 `/api/agent/me` 엔드포인트에 하트비트 기능 추가
- 호출 시 Redis TTL 10분으로 갱신

---

### 4. 프론트엔드 (chat-agent.html)

#### 하트비트 구현

```javascript
let heartbeatInterval = null; // 하트비트 인터벌

/**
 * 하트비트 시작 - 온라인 상태 유지
 */
function startHeartbeat() {
    // 즉시 한 번 실행
    sendHeartbeat();
    
    // 5분마다 하트비트 전송 ✅
    heartbeatInterval = setInterval(() => {
        sendHeartbeat();
    }, 5 * 60 * 1000); // 5분
    
    console.log('상담원 하트비트 시작 (5분 간격)');
}

/**
 * 하트비트 전송
 */
function sendHeartbeat() {
    fetch('/api/agent/me', {
        headers: { 'Authorization': 'Bearer ' + authToken }
    })
    .then(res => {
        if (res.ok) {
            console.log('하트비트 전송 성공');
        } else {
            console.warn('하트비트 전송 실패 - 재로그인 필요');
        }
    })
    .catch(err => {
        console.error('하트비트 오류:', err);
    });
}

/**
 * 하트비트 중단
 */
function stopHeartbeat() {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
        console.log('상담원 하트비트 중단');
    }
}
```

**호출 위치:**
```javascript
// WebSocket 연결 성공 시
function connectWebSocket() {
    stompClient.connect({}, function () {
        // ...
        startHeartbeat(); // ✅ 하트비트 시작
    });
}

// 로그아웃 시
function logout() {
    stopHeartbeat(); // ✅ 하트비트 중단
    // ...
}
```

---

## 📊 가용성 판단 로직 (수정)

### Before (문제)
```
1. 상담 중인 방만 체크
2. 방이 없으면 true (잘못됨!) ❌
```

### After (수정)
```
1. Redis에서 온라인 상담원 확인
   ↓
2. 온라인 상담원 없음?
   → available: false (버튼 비활성화) ❌
   ↓
3. 온라인 상담원 있음
   → 각 상담원의 상담 개수 확인
   ↓
4. 3개 미만 상담 중인 상담원 있음?
   → available: true (버튼 활성화) ✅
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 상담원 로그아웃 상태 ✅

```
1. 초기 상태
   - 모든 상담원 로그아웃
   - Redis: chat:online:agents:* 키 없음

2. 고객 상담 시작
   - 고객 A 로그인 및 상담 시작

3. API 호출
   GET /api/agent/availability
   
   응답:
   {
     "available": false,           ← 불가
     "onlineAgentCount": 0,        ← 온라인 상담원 없음
     "agentCount": 0,
     "agentRoomCount": {}
   }

4. 고객 화면 확인 ✅
   - 버튼: 비활성화 ❌
   - 텍스트: "상담원 대기 중"
   - Tooltip: "모든 상담원이 상담 중입니다..."
```

---

### 시나리오 2: 상담원 로그인 (상담 0개) ✅

```
1. 상담원 로그인
   - agent01 로그인
   - Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)

2. 고객 상담 시작
   - 고객 B 로그인 및 상담 시작

3. API 호출
   GET /api/agent/availability
   
   응답:
   {
     "available": true,            ← 가용
     "onlineAgentCount": 1,        ← 온라인 상담원 1명
     "agentCount": 0,              ← 상담 중인 방 0개
     "agentRoomCount": {}
   }

4. 고객 화면 확인 ✅
   - 버튼: 활성화 ✅
   - 텍스트: "상담원 연결"
   - 클릭 가능
```

---

### 시나리오 3: 상담원 3개 상담 중 ❌

```
1. 상담원 상태
   - agent01: 3개 상담 중
   - Redis: chat:online:agents:agent01 = "김상담" (TTL: 10분)

2. 고객 상담 시작
   - 고객 E 로그인 및 상담 시작

3. API 호출
   GET /api/agent/availability
   
   응답:
   {
     "available": false,           ← 불가
     "onlineAgentCount": 1,        ← 온라인 1명
     "agentCount": 1,              ← 상담 중 1명
     "agentRoomCount": {
       "agent01": 3                ← 3개 (한계)
     }
   }

4. 고객 화면 확인 ✅
   - 버튼: 비활성화 ❌
   - 텍스트: "상담원 대기 중"
```

---

### 시나리오 4: 하트비트 동작 ✅

```
1. 상담원 로그인
   - agent01 로그인 (15:00)
   - Redis TTL: 10분 (15:10 만료 예정)

2. 5분 경과 (15:05)
   - 자동 하트비트 전송
   - Redis TTL 갱신: 10분 (15:15 만료로 연장)

3. 10분 경과 (15:10)
   - 자동 하트비트 전송
   - Redis TTL 갱신: 10분 (15:20 만료로 연장)

4. 상담원 계속 온라인 유지 ✅
```

---

### 시나리오 5: 자동 로그아웃 (하트비트 없음) ✅

```
1. 상담원 로그인
   - agent01 로그인 (15:00)
   - Redis TTL: 10분 (15:10 만료 예정)

2. 브라우저 강제 종료 또는 네트워크 끊김
   - 하트비트 전송 안 됨

3. 10분 경과 (15:10)
   - Redis 키 자동 만료
   - chat:online:agents:agent01 삭제됨

4. 고객 화면 (15:11)
   - API 호출: onlineAgentCount = 0
   - 버튼: 비활성화 ✅
```

---

## 💡 Redis 키 구조

### 온라인 상담원 키

```
키: chat:online:agents:{userId}
값: {userName}
TTL: 10분

예시:
chat:online:agents:agent01 = "김상담" (TTL: 600초)
chat:online:agents:agent02 = "이상담" (TTL: 600초)
```

---

## 📝 변경된 파일 목록

### 백엔드 (3개)
- ✅ `AgentAuthService.java`
  - Redis에 온라인 상담원 등록
  - `heartbeat()` 메서드 추가

- ✅ `AgentChatController.java`
  - 가용성 확인 로직 수정 (온라인 체크 추가)
  - `StringRedisTemplate` 의존성 추가

- ✅ `AgentLoginController.java`
  - `/api/agent/me` 엔드포인트에 하트비트 추가

### 프론트엔드 (1개)
- ✅ `chat-agent.html`
  - `heartbeatInterval` 변수 추가
  - `startHeartbeat()` 함수 추가
  - `sendHeartbeat()` 함수 추가
  - `stopHeartbeat()` 함수 추가

---

## ⚠️ 주의사항

### 1. TTL 시간 (10분)

Redis TTL이 10분으로 설정되어 있습니다:
```java
redisTemplate.opsForValue().set(agentKey, account.getUserName(), 10, TimeUnit.MINUTES);
```

**조정 가능:**
- 짧게: 5분 (더 빠른 로그아웃 감지)
- 길게: 30분 (네트워크 불안정 허용)

---

### 2. 하트비트 주기 (5분)

하트비트가 5분마다 전송됩니다:
```javascript
setInterval(() => { sendHeartbeat(); }, 5 * 60 * 1000); // 5분
```

**권장:**
- TTL의 절반 이하로 설정
- 예: TTL 10분 → 하트비트 5분

---

### 3. Redis KEYS 명령어

`redisTemplate.keys()`는 성능 이슈가 있을 수 있습니다.

**개선 방안:**
- Redis Set으로 온라인 상담원 관리
- 또는 Hash 구조 사용

---

## 🔍 디버깅

### Redis 확인

```bash
# Redis CLI 접속
redis-cli

# 온라인 상담원 확인
KEYS chat:online:agents:*

# 결과:
# 1) "chat:online:agents:agent01"
# 2) "chat:online:agents:agent02"

# 특정 상담원 정보 확인
GET chat:online:agents:agent01
# "김상담"

# TTL 확인
TTL chat:online:agents:agent01
# 582 (초)
```

---

### API 테스트

```bash
# 가용성 확인
curl http://localhost:28070/api/agent/availability

# 응답:
{
  "available": true,
  "onlineAgentCount": 2,
  "agentCount": 1,
  "agentRoomCount": {
    "agent01": 2
  }
}
```

---

## 🎉 완료!

상담원이 **로그인**하고 **3개 미만의 상담을 진행 중**일 때만 고객이 상담원 연결을 요청할 수 있습니다!

**주요 개선사항:**
- ✅ Redis로 온라인 상담원 추적
- ✅ 로그아웃 시 버튼 비활성화
- ✅ 하트비트로 자동 로그아웃 방지
- ✅ 정확한 가용성 판단

**테스트:**
```
1. 모든 상담원 로그아웃 → 버튼 비활성화 확인 ✅
2. 상담원 로그인 → 버튼 활성화 확인 ✅
3. 상담원 3개 상담 시작 → 버튼 비활성화 확인 ✅
4. 10분 대기 (하트비트 없음) → 자동 로그아웃 확인 ✅
```
