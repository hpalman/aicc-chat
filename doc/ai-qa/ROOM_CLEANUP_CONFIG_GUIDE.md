# RoomCleanupService On/Off 설정 가이드

## 📋 개요

RoomCleanupService의 자동 정리 기능을 `application.yml` 설정 파일을 통해 on/off하고, 타임아웃 시간 및 실행 주기를 제어할 수 있도록 개선했습니다.

---

## 🎯 변경 사항

### Before (하드코딩) ❌

```java
@Service
public class RoomCleanupService {
    private static final long IDLE_TIMEOUT = 10 * 60 * 1000; // 하드코딩 ❌
    
    @Scheduled(fixedRate = 60000) // 하드코딩 ❌
    public void cleanupIdleRooms() {
        // 항상 실행됨 (on/off 불가) ❌
    }
}
```

**문제점:**
- ❌ 정리 기능을 끌 수 없음
- ❌ 타임아웃 시간 변경 시 코드 수정 필요
- ❌ 실행 주기 변경 시 코드 수정 및 재컴파일 필요

---

### After (설정 파일 기반) ✅

```yaml
# application.yml
app:
  chat:
    cleanup:
      enabled: true           # ✅ on/off 제어
      idle-timeout: 600000    # ✅ 설정 파일에서 변경 가능
      check-interval: 60000   # ✅ 설정 파일에서 변경 가능
```

```java
@Service
@ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true")
public class RoomCleanupService {
    @Value("${app.chat.cleanup.idle-timeout:600000}")
    private long idleTimeout; // ✅ 설정 파일에서 주입
    
    @Scheduled(fixedRateString = "${app.chat.cleanup.check-interval:60000}")
    public void cleanupIdleRooms() {
        // enabled=false면 Bean이 생성되지 않아 실행 안 됨 ✅
    }
}
```

**장점:**
- ✅ 설정 파일에서 on/off 제어 가능
- ✅ 타임아웃 및 주기 변경 시 코드 수정 불필요
- ✅ 재컴파일 없이 설정만 변경하면 됨
- ✅ 개발/운영 환경별로 다른 설정 적용 가능

---

## 🔧 구현 내용

### 1. application.yml 설정 추가

**파일:** `src/main/resources/application.yml`

```yaml
app:
  chat:
    cleanup:
      enabled: true           # 자동 정리 기능 활성화 (true/false)
      idle-timeout: 600000    # 유휴 타임아웃 시간 (밀리초, 10분)
      check-interval: 60000   # 정리 작업 실행 주기 (밀리초, 1분)
```

#### 설정 항목 설명

| 설정 키 | 설명 | 기본값 | 단위 |
|---------|------|--------|------|
| `app.chat.cleanup.enabled` | 자동 정리 기능 on/off | `true` | boolean |
| `app.chat.cleanup.idle-timeout` | 유휴 타임아웃 시간 | `600000` | 밀리초 (ms) |
| `app.chat.cleanup.check-interval` | 정리 작업 실행 주기 | `60000` | 밀리초 (ms) |

#### 시간 계산 예시

```yaml
# 타임아웃 시간 예시
idle-timeout: 300000    # 5분 = 5 * 60 * 1000
idle-timeout: 600000    # 10분 = 10 * 60 * 1000
idle-timeout: 1800000   # 30분 = 30 * 60 * 1000
idle-timeout: 3600000   # 1시간 = 60 * 60 * 1000

# 실행 주기 예시
check-interval: 30000   # 30초
check-interval: 60000   # 1분
check-interval: 300000  # 5분
```

---

### 2. RoomCleanupService 수정

**파일:** `RoomCleanupService.java`

#### 주요 변경사항

```java
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.chat.cleanup.enabled", 
    havingValue = "true", 
    matchIfMissing = true  // 설정 없으면 기본값 true
)
public class RoomCleanupService {

    // 의존성 주입
    private final RoomRepository roomRepository;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final MessageBroker messageBroker;
    private final ChatHistoryService chatHistoryService;
    private final ChatSessionService chatSessionService;
    
    // ✅ application.yml에서 값 주입
    @Value("${app.chat.cleanup.idle-timeout:600000}")
    private long idleTimeout; // 기본값: 10분
    
    @Value("${app.chat.cleanup.check-interval:60000}")
    private long checkInterval; // 기본값: 1분

    /**
     * ✅ fixedRateString으로 변경 (설정 파일에서 주입 가능)
     */
    @Scheduled(fixedRateString = "${app.chat.cleanup.check-interval:60000}")
    public void cleanupIdleRooms() {
        log.debug("Starting idle room cleanup task... (timeout: {}ms, interval: {}ms)", 
                idleTimeout, checkInterval);
        
        List<ChatRoom> allRooms = roomRepository.findAllRooms();
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (ChatRoom room : allRooms) {
            long idleTime = now - room.getLastActivityAt();
            
            // ✅ 설정 파일에서 주입받은 idleTimeout 사용
            if (idleTime > idleTimeout) {
                log.info("Cleaning up idle room: {} (Idle for {} ms, timeout: {} ms)", 
                        room.getRoomId(), idleTime, idleTimeout);
                
                notifyRoomTimeout(room);
                saveRoomTimeoutToDatabase(room);
                roomRepository.deleteRoom(room.getRoomId());
                
                changed = true;
            }
        }

        if (changed) {
            roomUpdateBroadcaster.broadcastRoomList();
        }
    }
    
    // ... 나머지 메서드
}
```

#### 핵심 어노테이션

1. **`@ConditionalOnProperty`**
   ```java
   @ConditionalOnProperty(
       name = "app.chat.cleanup.enabled", 
       havingValue = "true", 
       matchIfMissing = true
   )
   ```
   - `enabled=false`면 RoomCleanupService Bean이 생성되지 않음
   - 설정이 없으면 `matchIfMissing=true`로 기본 활성화

2. **`@Value`**
   ```java
   @Value("${app.chat.cleanup.idle-timeout:600000}")
   private long idleTimeout;
   ```
   - application.yml의 값을 필드에 주입
   - `:600000`은 기본값 (설정이 없을 때 사용)

3. **`@Scheduled(fixedRateString)`**
   ```java
   @Scheduled(fixedRateString = "${app.chat.cleanup.check-interval:60000}")
   ```
   - `fixedRate`는 상수만 가능
   - `fixedRateString`은 SpEL 표현식 가능 (설정 주입 가능)

---

## 📊 사용 시나리오

### 시나리오 1: 정리 기능 비활성화 (개발 환경)

```yaml
# application-dev.yml
app:
  chat:
    cleanup:
      enabled: false  # ✅ 개발 환경에서는 자동 정리 비활성화
```

**결과:**
- RoomCleanupService Bean이 생성되지 않음
- 정리 작업이 실행되지 않음
- 개발 중 채팅방이 자동으로 삭제되지 않음

---

### 시나리오 2: 타임아웃 시간 단축 (테스트 환경)

```yaml
# application-test.yml
app:
  chat:
    cleanup:
      enabled: true
      idle-timeout: 60000     # ✅ 1분으로 단축 (테스트용)
      check-interval: 10000   # ✅ 10초마다 체크 (테스트용)
```

**결과:**
- 1분간 활동이 없으면 자동 정리
- 10초마다 정리 작업 실행
- 빠른 테스트 가능

---

### 시나리오 3: 타임아웃 시간 연장 (운영 환경)

```yaml
# application-prod.yml
app:
  chat:
    cleanup:
      enabled: true
      idle-timeout: 1800000   # ✅ 30분으로 연장
      check-interval: 300000  # ✅ 5분마다 체크
```

**결과:**
- 30분간 활동이 없어야 정리
- 5분마다 정리 작업 실행
- 서버 부하 감소

---

## 🧪 테스트 방법

### 1. 정리 기능 활성화 테스트

```yaml
# application.yml
app:
  chat:
    cleanup:
      enabled: true
      idle-timeout: 60000    # 1분
      check-interval: 10000  # 10초
```

```bash
# 1. 애플리케이션 시작
.\gradlew bootRun

# 2. 로그 확인
# 10초마다 다음 로그가 출력됨:
# "Starting idle room cleanup task... (timeout: 60000ms, interval: 10000ms)"

# 3. 고객 로그인 및 상담 시작
http://localhost:28070/chat-customer.html

# 4. 1분간 메시지를 보내지 않음

# 5. 로그 확인
# "Cleaning up idle room: room-xxx (Idle for 61234 ms, timeout: 60000 ms)"
# "Timeout notification sent to room: room-xxx"
# "Timeout record saved to database for room: room-xxx"

# 6. 고객 화면 확인
# "[2026-01-26 15:30:45] 장시간 대화가 없어 상담이 자동 종료되었습니다."
# 3초 후 자동으로 상담 시작 화면으로 이동
```

---

### 2. 정리 기능 비활성화 테스트

```yaml
# application.yml
app:
  chat:
    cleanup:
      enabled: false  # ✅ 비활성화
```

```bash
# 1. 애플리케이션 시작
.\gradlew bootRun

# 2. 로그 확인
# RoomCleanupService 관련 로그가 전혀 출력되지 않음

# 3. 고객 로그인 및 상담 시작
http://localhost:28070/chat-customer.html

# 4. 오랫동안 메시지를 보내지 않음

# 5. 결과 확인
# 채팅방이 자동으로 정리되지 않음 ✅
# 고객은 언제든지 메시지를 보낼 수 있음
```

---

### 3. 설정 변경 테스트 (재시작 필요)

```bash
# 1. 현재 설정 확인
app.chat.cleanup.idle-timeout: 600000  # 10분

# 2. 설정 변경
# application.yml에서 idle-timeout: 120000 (2분)으로 변경

# 3. 애플리케이션 재시작
.\gradlew bootRun

# 4. 로그에서 변경된 값 확인
# "Starting idle room cleanup task... (timeout: 120000ms, ...)"

# 5. 2분간 대기 후 정리 확인
```

---

## 💡 환경별 설정 예시

### 개발 환경 (application-dev.yml)

```yaml
app:
  chat:
    cleanup:
      enabled: false  # 개발 중에는 비활성화
      # idle-timeout: 600000  # 설정 필요 없음
      # check-interval: 60000  # 설정 필요 없음
```

---

### 테스트 환경 (application-test.yml)

```yaml
app:
  chat:
    cleanup:
      enabled: true
      idle-timeout: 60000    # 1분 (빠른 테스트)
      check-interval: 10000  # 10초 (자주 체크)
```

---

### 운영 환경 (application-prod.yml)

```yaml
app:
  chat:
    cleanup:
      enabled: true
      idle-timeout: 1800000   # 30분 (넉넉한 시간)
      check-interval: 300000  # 5분 (적당한 주기)
```

---

## 🔍 로그 예시

### 정리 기능 활성화 시

```
2026-01-26 15:30:00.123 [scheduling-1] DEBUG RoomCleanupService - Starting idle room cleanup task... (timeout: 600000ms, interval: 60000ms)
2026-01-26 15:40:15.456 [scheduling-1] INFO  RoomCleanupService - Cleaning up idle room: room-abc123 (Idle for 615234 ms, timeout: 600000 ms)
2026-01-26 15:40:15.457 [scheduling-1] INFO  RoomCleanupService - Timeout notification sent to room: room-abc123
2026-01-26 15:40:15.478 [scheduling-1] INFO  RoomCleanupService - Timeout record saved to database for room: room-abc123
```

### 정리 기능 비활성화 시

```
# RoomCleanupService 관련 로그가 전혀 출력되지 않음
```

---

## 📝 상담원 종료 시 버튼 활성화

chat-customer.html은 이미 구현되어 있습니다:

```javascript
// 상담원 상담 종료 → BOT 모드 복귀 확인
if (message.sender === 'System' && 
    message.message.includes("상담원과의 상담이 종료되었습니다")) {
    updateHandoffButtons('BOT');  // ✅ "상담원 연결" 버튼 활성화
}
```

**동작:**
1. 상담원이 "상담 종료" 클릭
2. 서버에서 "상담원과의 상담이 종료되었습니다" 메시지 전송
3. 고객 화면에서 메시지 수신
4. `updateHandoffButtons('BOT')` 호출
5. "상담원 연결" 버튼 활성화 ✅

---

## 🎯 주요 변경사항 요약

### 백엔드 (2개)
- [x] `application.yml` - cleanup 설정 추가
- [x] `RoomCleanupService.java` - 설정 기반으로 수정

### 프론트엔드 (1개)
- [x] `chat-customer.html` - 이미 구현됨 (상담원 종료 시 버튼 활성화)

---

## ✅ 컴파일 성공

```bash
.\gradlew compileJava

BUILD SUCCESSFUL in 17s
```

---

## 🎉 완료

RoomCleanupService가 `application.yml` 설정으로 제어됩니다!

**주요 기능:**
- ✅ `app.chat.cleanup.enabled`로 on/off 제어
- ✅ `app.chat.cleanup.idle-timeout`으로 타임아웃 시간 설정
- ✅ `app.chat.cleanup.check-interval`으로 실행 주기 설정
- ✅ 환경별로 다른 설정 적용 가능
- ✅ 코드 수정 없이 설정만 변경하면 됨
- ✅ 상담원 종료 시 "상담원 연결" 버튼 자동 활성화
