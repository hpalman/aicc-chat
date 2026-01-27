# 상담원 멀티 탭 채팅 가이드

## 📋 개요

상담원이 여러 고객과 동시에 채팅할 수 있도록 **탭(Tab) UI**를 구현했습니다. 각 탭은 독립적인 고객 상담을 나타내며, 탭 닫기 버튼으로 상담을 종료할 수 있습니다.

---

## 🎯 주요 기능

### 1. 멀티 탭 채팅
- ✅ 여러 고객과 **동시에** 채팅 가능
- ✅ 탭 클릭으로 고객 간 **빠른 전환**
- ✅ 각 탭에 고객 이름 표시
- ✅ 현재 활성 탭 하이라이트

### 2. 탭 닫기 = 상담 종료
- ✅ 탭 닫기 버튼(×) 클릭 시 **상담 종료**
- ✅ 상담 종료 확인 대화상자
- ✅ 고객에게 BOT 모드로 복귀 알림

### 3. 상담원 개입 기능
- ✅ 다른 상담원이 상담 중인 방에 **강제 개입** 가능
- ✅ 개입 시 고객에게 알림 메시지 전송
- ✅ 이전 상담원과 교체

---

## 🎨 UI 변경 사항

### Before (단일 채팅창)

```
┌────────────────────────────────────────────┐
│  [방 목록]    [채팅 헤더: 홍길동 (상담중)]  │
│               ┌────────────────────────┐   │
│               │  채팅 메시지 영역       │   │
│               │                        │   │
│               └────────────────────────┘   │
│               [메시지 입력창]              │
└────────────────────────────────────────────┘
```

**문제점:**
- 한 번에 한 명의 고객만 상담 가능
- 다른 고객에게 응답하려면 방 목록에서 다시 선택
- 채팅 내용이 초기화됨

---

### After (멀티 탭)

```
┌────────────────────────────────────────────┐
│  [방 목록]    ┌─ 홍길동 × ┬─ 김철수 × ─┐  │
│               │  (활성)  │  (비활성)  │  │
│               ├──────────────────────────┤  │
│               │  채팅 헤더: 홍길동      │  │
│               ├──────────────────────────┤  │
│               │  채팅 메시지 영역       │  │
│               │                        │  │
│               └──────────────────────────┘  │
│               [메시지 입력창]              │
└────────────────────────────────────────────┘
```

**장점:**
- ✅ 여러 고객과 동시 상담 가능
- ✅ 탭 클릭으로 빠른 전환
- ✅ 각 탭의 채팅 내용 유지
- ✅ 탭 닫기(×)로 상담 종료

---

## 🔧 구현 상세

### 1. 프론트엔드 (chat-agent.html)

#### 탭 영역 추가

```html
<!-- 탭 영역 -->
<div class="chat-tabs" id="chat-tabs">
    <!-- Tabs will be dynamically added here -->
</div>

<!-- 활성 채팅 영역 -->
<div class="chat-header" id="chat-header">
    <span class="fw-bold fs-5">상담을 선택해주세요</span>
</div>
<div class="chat-box" id="chat-box">
    <!-- Messages -->
</div>
```

---

#### 탭 CSS 스타일

```css
.chat-tabs {
    border-bottom: 1px solid #ddd;
    background: #fff;
    padding: 10px 10px 0 10px;
    display: flex;
    overflow-x: auto;
}

.chat-tab {
    position: relative;
    padding: 10px 40px 10px 15px;
    border: 1px solid #ddd;
    border-bottom: none;
    background: #f8f9fa;
    cursor: pointer;
    margin-right: 5px;
    border-radius: 5px 5px 0 0;
    white-space: nowrap;
    transition: 0.2s;
}

.chat-tab:hover {
    background: #e9ecef;
}

.chat-tab.active {
    background: #fff;
    font-weight: bold;
    border-bottom: 2px solid #fff;
    margin-bottom: -1px;
}

.chat-tab .close-tab {
    position: absolute;
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
    color: #999;
    font-size: 18px;
    cursor: pointer;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.chat-tab .close-tab:hover {
    color: #dc3545;
}
```

---

#### JavaScript 주요 변수

```javascript
let openTabs = {}; // roomId -> room object (열려있는 탭 관리)
let tabOrder = []; // roomId 배열 (탭 순서 관리)
```

---

#### 탭 관리 함수

##### 1. 탭 UI 업데이트

```javascript
function updateTabsUI() {
    const tabsContainer = document.getElementById("chat-tabs");
    tabsContainer.innerHTML = "";
    
    if (tabOrder.length === 0) {
        tabsContainer.innerHTML = '<div class="text-muted" style="padding: 10px;">열린 상담이 없습니다.</div>';
        return;
    }
    
    tabOrder.forEach(roomId => {
        const room = openTabs[roomId];
        if (!room) return;
        
        const tabDiv = document.createElement("div");
        tabDiv.className = `chat-tab ${currentRoomId === roomId ? 'active' : ''}`;
        tabDiv.innerHTML = `
            <span>${room.roomName || '알 수 없음'}</span>
            <span class="close-tab" onclick="event.stopPropagation(); closeTab('${roomId}')" title="상담 종료">×</span>
        `;
        tabDiv.onclick = () => switchTab(roomId);
        tabsContainer.appendChild(tabDiv);
    });
}
```

---

##### 2. 탭 열기 (새로운 상담 시작)

```javascript
function openTab(room) {
    if (!openTabs[room.roomId]) {
        openTabs[room.roomId] = room;
        tabOrder.push(room.roomId);
        
        // 구독 시작
        subscribeToRoom(room.roomId);
    }
    
    switchTab(room.roomId);
    updateTabsUI();
}
```

---

##### 3. 탭 전환

```javascript
function switchTab(roomId) {
    if (!openTabs[roomId]) return;
    
    currentRoomId = roomId;
    const room = openTabs[roomId];
    
    unreadRooms[roomId] = false;
    updateHeader(room);
    updateInputState(room);
    
    const chatBox = document.getElementById("chat-box");
    chatBox.innerHTML = "";
    
    if (roomMessages[roomId]) {
        roomMessages[roomId].forEach(msg => showMessage(msg));
    }
    
    updateTabsUI();
    updateRoomListUI(roomsData);
}
```

---

##### 4. 탭 닫기 (상담 종료)

```javascript
function closeTab(roomId) {
    if (!openTabs[roomId]) return;
    
    const room = openTabs[roomId];
    const isMyRoom = room.status === 'AGENT' && room.assignedAgent === nickname;
    
    // 내 상담방이면 종료 확인 및 API 호출
    if (isMyRoom && room.status !== 'CLOSED') {
        closeConsultation(roomId, room.status);
        return;
    }
    
    // 탭만 닫기
    delete openTabs[roomId];
    tabOrder = tabOrder.filter(id => id !== roomId);
    
    // 구독 해제
    if (subscriptions[roomId]) {
        subscriptions[roomId].unsubscribe();
        delete subscriptions[roomId];
    }
    
    // 현재 탭이었다면 다른 탭으로 전환
    if (currentRoomId === roomId) {
        if (tabOrder.length > 0) {
            switchTab(tabOrder[tabOrder.length - 1]);
        } else {
            currentRoomId = null;
            document.getElementById("chat-box").innerHTML = "";
            document.getElementById("chat-header").innerHTML = '<span class="fw-bold fs-5">상담을 선택해주세요</span>';
            document.getElementById("message").disabled = true;
            document.getElementById("sendBtn").disabled = true;
        }
    }
    
    updateTabsUI();
    updateRoomListUI(roomsData);
}
```

---

##### 5. 상담 종료 API 호출

```javascript
function closeConsultation(roomId, status) {
    const msg = status === 'CLOSED' ? "방을 목록에서 완전히 삭제하시겠습니까?" : "상담을 종료하시겠습니까?";
    if (!confirm(msg)) return;

    fetch(`/api/agent/rooms/${roomId}`, {
        method: 'DELETE',
        headers: { 'Authorization': 'Bearer ' + authToken }
    })
    .then(res => {
        if (res.ok) {
            alert("상담이 종료되었습니다.");
            // 탭 닫기
            closeTab(roomId);
            loadRooms();
        } else {
            alert("상담 종료에 실패했습니다.");
        }
    })
    .catch(err => console.error(err));
}
```

---

### 2. 백엔드 (AgentChatController.java)

#### 강제 개입 기능 추가

```java
@PostMapping("/rooms/{roomId}/assign")
public ResponseEntity<?> assignAgent(
        @PathVariable String roomId,
        @RequestHeader(value = "Authorization", required = false) String token,
        @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
    
    // ... 인증 및 방 상태 확인 ...
    
    String currentAgent = roomRepository.getAssignedAgent(roomId);
    if (currentAgent != null && !currentAgent.equals(userInfo.getUserName())) {
        if (userInfo.getUserName().equals(currentAgent)) {
            return ResponseEntity.ok().build(); // 이미 본인에게 배정된 경우 성공 처리
        }
        
        if (force) {
            log.info("Force assigning agent {} to room {} (current: {})", 
                     userInfo.getUserName(), roomId, currentAgent);
            
            // 강제 배정: 기존 배정 상담원 교체
            roomRepository.setAssignedAgent(roomId, userInfo.getUserName());
            roomRepository.setRoutingMode(roomId, "AGENT");
            roomRepository.updateLastActivity(roomId);

            LocalDateTime now = LocalDateTime.now();
            ChatMessage notice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(userInfo.getUserName() + " 상담원이 상담에 개입했습니다.")
                    .type(aicc.chat.domain.MessageType.INTERVENE)
                    .timestamp(now)
                    .build();

            try {
                messageBroker.publish(notice);
                roomUpdateBroadcaster.broadcastRoomList();

                chatSessionService.updateSessionStatus(roomId, "AGENT");
                chatSessionService.assignAgent(roomId, userInfo.getUserName());

                ChatHistory chatHistory = ChatHistory.builder()
                        .roomId(roomId)
                        .senderId("SYSTEM")
                        .senderName("System")
                        .senderRole("SYSTEM")
                        .message(notice.getMessage())
                        .messageType("INTERVENE")
                        .createdAt(now)
                        .build();
                chatHistoryService.saveChatHistory(chatHistory);
            } catch (Exception e) {
                log.error("Failed to post-force-assign actions", e);
            }

            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.status(409).body("이미 다른 상담원(" + currentAgent + ")이 배정되었습니다.");
    }
    
    // ... 정상 배정 로직 ...
}
```

---

#### 상담 종료 시 상담원 멤버 제거

```java
@DeleteMapping("/rooms/{roomId}")
public ResponseEntity<?> deleteRoom(@PathVariable String roomId, ...) {
    // ...
    
    String currentMode = roomRepository.getRoutingMode(roomId);
    
    if ("CLOSED".equals(currentMode)) {
        log.info("Permanently deleting closed room: {}", roomId);
        roomRepository.deleteRoom(roomId);
    } else {
        log.info("Agent ending consultation, switching room {} back to BOT mode", roomId);
        
        // 방 상태를 BOT으로 변경
        roomRepository.setRoutingMode(roomId, "BOT");
        
        // 상담원 배정 해제
        roomRepository.setAssignedAgent(roomId, null);
        
        // 상담원 멤버 정보 제거 (Redis 멤버 목록 정리) ✅
        roomRepository.removeMember(roomId, userInfo.getUserId());
        
        // PostgreSQL에 상태 업데이트
        chatSessionService.updateSessionStatus(roomId, "BOT");
        
        // ... 메시지 발송 및 이력 저장 ...
    }
    
    // ...
}
```

---

## 📊 사용자 시나리오

### 시나리오 1: 멀티 탭으로 동시 상담

```
1. 상담원 로그인
   - http://localhost:28070/chat-agent.html
   - 아이디: agent01, 비밀번호: 1234

2. 첫 번째 고객 상담 시작
   - 방 목록에서 "홍길동" 클릭
   - "상담을 수락하시겠습니까?" → 확인
   - 탭 생성: [홍길동 ×]
   - 홍길동과 채팅 시작 ✅

3. 두 번째 고객 상담 시작
   - 방 목록에서 "김철수" 클릭
   - "상담을 수락하시겠습니까?" → 확인
   - 탭 생성: [홍길동 ×] [김철수 ×]
   - 김철수와 채팅 시작 ✅

4. 세 번째 고객 상담 시작
   - 방 목록에서 "이영희" 클릭
   - 탭 생성: [홍길동 ×] [김철수 ×] [이영희 ×]
   - 이영희와 채팅 시작 ✅

5. 탭 전환으로 고객 응답
   - [홍길동 ×] 탭 클릭 → 홍길동 채팅창 표시
   - 메시지 입력 및 전송
   - [김철수 ×] 탭 클릭 → 김철수 채팅창 표시
   - 메시지 입력 및 전송
   - [이영희 ×] 탭 클릭 → 이영희 채팅창 표시
   - 메시지 입력 및 전송 ✅

6. 상담 종료 (탭 닫기)
   - [홍길동 ×]의 × 버튼 클릭
   - "상담을 종료하시겠습니까?" → 확인
   - 홍길동 탭 닫힘
   - 홍길동 고객 화면: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
   - 방 상태: AGENT → BOT ✅
```

---

### 시나리오 2: 상담원 강제 개입

```
1. 상담원 A가 고객과 상담 중
   - agent01이 "홍길동"과 상담 중
   - 방 상태: AGENT (assignedAgent: agent01)

2. 상담원 B가 같은 고객 선택
   - agent02가 로그인
   - 방 목록에서 "홍길동" 클릭
   - 확인 대화상자: "agent01 상담원이 이미 상담 중입니다.\n지금 이 상담에 개입하시겠습니까?"

3. 개입 선택
   - "확인" 클릭
   - API 호출: POST /api/agent/rooms/{roomId}/assign?force=true

4. 개입 성공
   - 방 상태: AGENT (assignedAgent: agent02) ✅
   - 고객 화면: "agent02 상담원이 상담에 개입했습니다." (INTERVENE 타입)
   - agent02 탭 생성: [홍길동 ×]
   - agent01 화면: 방 목록에서 "홍길동" 상태 변경 (상담중(agent02))

5. agent02가 상담 진행
   - agent02가 홍길동과 채팅 계속
   - agent01은 더 이상 메시지 입력 불가 ✅
```

---

### 시나리오 3: 탭 닫기로 상담 종료

```
1. 여러 고객과 동시 상담 중
   - 탭: [홍길동 ×] [김철수 ×] [이영희 ×]
   - 현재 활성 탭: 김철수

2. 김철수 상담 종료
   - [김철수 ×]의 × 버튼 클릭
   - 확인 대화상자: "상담을 종료하시겠습니까?" → 확인
   - API 호출: DELETE /api/agent/rooms/{roomId}

3. 서버 처리
   - 방 상태: AGENT → BOT
   - 상담원 배정 해제: assignedAgent = null
   - 상담원 멤버 제거: removeMember(roomId, agentId)
   - 고객에게 메시지: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
   - PostgreSQL 업데이트: status = "BOT"

4. 상담원 화면
   - [김철수 ×] 탭 닫힘
   - 남은 탭: [홍길동 ×] [이영희 ×]
   - 자동으로 [이영희 ×] 탭으로 전환 (마지막 탭)
   - Alert: "상담이 종료되었습니다."

5. 고객 화면 (김철수)
   - 시스템 메시지: "상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다."
   - "상담원 연결" 버튼 활성화 ✅
   - 챗봇과 대화 재개 가능 ✅
```

---

## 💡 주요 변경 사항 요약

### 프론트엔드 (chat-agent.html)

| 변경 사항 | Before | After |
|-----------|--------|-------|
| 채팅 UI | 단일 채팅창 | 멀티 탭 |
| 방 선택 | 기존 채팅 내용 초기화 | 탭에 내용 유지 |
| 상담 종료 | 헤더의 "상담종료" 버튼 | 탭의 × 버튼 |
| 동시 상담 | 불가능 | 여러 고객 동시 가능 |
| 상담원 개입 | 불가능 | 강제 개입 가능 |

---

### 백엔드 (AgentChatController.java)

| 변경 사항 | 설명 |
|-----------|------|
| **강제 개입 파라미터** | `?force=true` 추가 |
| **개입 메시지 타입** | `MessageType.INTERVENE` 추가 |
| **상담원 멤버 제거** | `removeMember(roomId, agentId)` 호출 |
| **개입 알림** | "{상담원명} 상담원이 상담에 개입했습니다." |

---

## 🎨 UI 스크린샷 설명

### 1. 탭 없음 (초기 상태)

```
┌───────────────────────────────────────────────┐
│  열린 상담이 없습니다.                        │
├───────────────────────────────────────────────┤
│  상담을 선택해주세요                          │
│                                               │
│                                               │
└───────────────────────────────────────────────┘
```

---

### 2. 탭 1개 (홍길동)

```
┌───────────────────────────────────────────────┐
│  ┌─ 홍길동 × ────────┐                       │
│  │   (활성)          │                       │
├──┴────────────────────────────────────────────┤
│  채팅 헤더: 홍길동 [상담중(나)]              │
├───────────────────────────────────────────────┤
│  홍길동: 안녕하세요                           │
│  상담원: 무엇을 도와드릴까요?                 │
│                                               │
└───────────────────────────────────────────────┘
```

---

### 3. 탭 3개 (홍길동, 김철수, 이영희)

```
┌───────────────────────────────────────────────┐
│  ┌─ 홍길동 × ─┬─ 김철수 × ─┬─ 이영희 × ─┐  │
│  │  (비활성)  │  (활성)    │  (비활성)  │  │
├──┴────────────┴────────────┴────────────────┤
│  채팅 헤더: 김철수 [상담중(나)]              │
├───────────────────────────────────────────────┤
│  김철수: 배송 문의 드립니다                   │
│  상담원: 주문번호 알려주세요                  │
│                                               │
└───────────────────────────────────────────────┘
```

---

## 🔍 기술 구현 세부 사항

### 1. 탭 순서 관리

```javascript
let tabOrder = []; // ["room-abc123", "room-def456", "room-ghi789"]

// 탭 추가
tabOrder.push(roomId);

// 탭 제거
tabOrder = tabOrder.filter(id => id !== roomId);

// 마지막 탭 선택
if (tabOrder.length > 0) {
    switchTab(tabOrder[tabOrder.length - 1]);
}
```

---

### 2. 열린 탭 관리

```javascript
let openTabs = {
    "room-abc123": { roomId: "room-abc123", roomName: "홍길동", status: "AGENT", ... },
    "room-def456": { roomId: "room-def456", roomName: "김철수", status: "AGENT", ... },
    "room-ghi789": { roomId: "room-ghi789", roomName: "이영희", status: "AGENT", ... }
};

// 탭 추가
openTabs[roomId] = room;

// 탭 제거
delete openTabs[roomId];

// 탭 존재 확인
if (openTabs[roomId]) { ... }
```

---

### 3. 구독 관리

```javascript
// 열린 탭만 구독 유지
rooms.forEach(room => {
    if (openTabs[room.roomId]) {
        // 열린 탭은 항상 구독 유지
        if (!subscriptions[room.roomId]) {
            subscribeToRoom(room.roomId);
        }
    } else {
        // 열린 탭이 아니면 구독 해제
        if (subscriptions[room.roomId]) {
            subscriptions[room.roomId].unsubscribe();
            delete subscriptions[room.roomId];
        }
    }
});
```

---

### 4. 메시지 이력 유지

```javascript
let roomMessages = {
    "room-abc123": [ /* 홍길동 메시지 배열 */ ],
    "room-def456": [ /* 김철수 메시지 배열 */ ],
    "room-ghi789": [ /* 이영희 메시지 배열 */ ]
};

// 탭 전환 시 메시지 복원
if (roomMessages[roomId]) {
    roomMessages[roomId].forEach(msg => showMessage(msg));
}
```

---

## ⚠️ 주의사항

### 1. 탭 닫기 = 상담 종료

탭의 × 버튼을 클릭하면 **실제로 상담이 종료**됩니다.
- 고객은 BOT 모드로 복귀
- 방 상태가 AGENT → BOT로 변경
- 상담원 배정 해제

**실수로 닫지 않도록 주의!**

---

### 2. 강제 개입 신중히 사용

다른 상담원이 상담 중인 방에 개입하면:
- 기존 상담원이 더 이상 메시지를 보낼 수 없음
- 고객에게 개입 알림이 표시됨
- 상담 연속성이 끊길 수 있음

**긴급한 경우에만 사용하세요!**

---

### 3. 탭 개수 제한 없음

현재 탭 개수에 제한이 없으므로:
- 너무 많은 탭을 열면 UI가 혼잡할 수 있음
- 상담원이 관리하기 어려울 수 있음
- 필요에 따라 제한 기능 추가 고려

---

## 🧪 테스트 체크리스트

```
✅ 여러 고객과 동시 상담 가능
✅ 탭 클릭으로 채팅 전환
✅ 각 탭의 메시지 이력 유지
✅ 탭 닫기로 상담 종료
✅ 고객에게 BOT 복귀 알림
✅ 상담원 강제 개입 기능
✅ 개입 시 고객에게 알림
✅ 방 목록에서 열린 탭 표시 (열림 배지)
✅ 백그라운드 메시지 알림 (NEW 배지)
✅ 마지막 탭 닫을 때 초기 상태로 복귀
```

---

## 🎉 완료!

상담원이 **멀티 탭**으로 여러 고객과 동시에 채팅할 수 있습니다!

**주요 기능:**
- ✅ 멀티 탭 UI로 여러 고객 동시 상담
- ✅ 탭 클릭으로 빠른 전환
- ✅ 탭 닫기(×) 버튼으로 상담 종료
- ✅ 상담 종료 시 고객 BOT 모드 복귀
- ✅ 강제 개입 기능으로 다른 상담원 방 접근

**사용 방법:**
1. 방 목록에서 고객 선택 → 탭 생성
2. 탭 클릭으로 고객 간 전환
3. 탭 × 버튼으로 상담 종료

**테스트:**
```
http://localhost:28070/chat-agent.html
아이디: agent01, 비밀번호: 1234
```
