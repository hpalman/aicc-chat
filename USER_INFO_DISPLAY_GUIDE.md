# 사용자 정보 표시 기능 가이드

## 📋 개요

고객 화면과 상담원 화면에 **사용자 ID와 이름**을 표시하도록 수정했습니다.

---

## 🎯 변경 사항

### 1. 고객 화면 (chat-customer.html)

#### Before
```
┌─────────────────────────────────────────────┐
│  배송문의                                   │
│  방 ID: room-abc123                        │
└─────────────────────────────────────────────┘
```

#### After ✅
```
┌─────────────────────────────────────────────┐
│  배송문의 (홍길동 / cust01)                │
│  방 ID: room-abc123                        │
└─────────────────────────────────────────────┘
```

**표시 위치:** 방 이름 오른쪽
**표시 형식:** `(이름 / ID)`

---

### 2. 상담원 화면 (chat-agent.html)

#### Before
```
┌─────────────────────────────────────────────┐
│  상담 목록                       [로그아웃] │
│  접속 상담원: 김상담                        │
└─────────────────────────────────────────────┘
```

#### After ✅
```
┌─────────────────────────────────────────────┐
│  상담 목록                       [로그아웃] │
│  접속 상담원: 김상담 (agent01)              │
└─────────────────────────────────────────────┘
```

**표시 위치:** 접속 상담원 라벨 옆
**표시 형식:** `이름 (ID)`

---

## 🔧 구현 상세

### 고객 화면 (chat-customer.html)

#### 1. HTML 수정

```html
<!-- Before -->
<div>
    <span id="room-title" class="fw-bold"></span>
    <div id="room-id" class="room-info"></div>
</div>

<!-- After ✅ -->
<div>
    <span id="room-title" class="fw-bold"></span>
    <span id="user-info" class="text-muted ms-2" style="font-size: 0.9em;"></span>
    <div id="room-id" class="room-info"></div>
</div>
```

**추가된 요소:**
- `<span id="user-info">` - 사용자 정보 표시 영역
- `class="text-muted ms-2"` - 회색 텍스트, 왼쪽 여백 추가
- `style="font-size: 0.9em;"` - 작은 글씨 크기

---

#### 2. JavaScript 변수 추가

```javascript
let stompClient = null;
let currentRoomId = null;
let nickname = null;
let authToken = null;
let currentUserId = null; // ✅ 사용자 ID 저장
```

---

#### 3. 로그인 성공 시 사용자 ID 저장

```javascript
function onLoginSuccess(user) {
    nickname = user.userName;
    currentUserId = user.userId; // ✅ 사용자 ID 저장
    authToken = user.token;
    
    // ... 기존 로직 ...
}
```

---

#### 4. 상담 시작 시 사용자 정보 표시

```javascript
function createRoom() {
    // ... WebSocket 연결 로직 ...
    
    stompClient.connect({}, function (frame) {
        // ... 구독 및 입장 메시지 로직 ...
        
        // 화면 표시
        document.getElementById("room-title").innerText = document.getElementById("roomName").value || "상담 중";
        document.getElementById("room-id").innerText = `방 ID: ${currentRoomId}`;
        document.getElementById("user-info").innerText = `(${nickname} / ${currentUserId})`; // ✅ 사용자 정보 표시
    });
}
```

---

### 상담원 화면 (chat-agent.html)

#### 1. JavaScript 변수 추가

```javascript
let stompClient = null;
let currentRoomId = null;
let nickname = null;
let authToken = null;
let currentUserId = null; // ✅ 상담원 ID 저장
let roomMessages = {}; // ...
// ... 기존 변수들 ...
```

---

#### 2. 인증 확인 시 상담원 ID 저장 및 표시

```javascript
function checkAuth(token) {
    fetch('/api/agent/me', {
        headers: { 'Authorization': 'Bearer ' + token }
    })
    .then(res => {
        if (res.ok) return res.json();
        throw new Error();
    })
    .then(user => {
        authToken = token;
        nickname = user.userName;
        currentUserId = user.userId; // ✅ 상담원 ID 저장
        document.getElementById("displayAgentName").innerText = `${nickname} (${currentUserId})`; // ✅ ID 포함 표시
        document.getElementById("login-form").style.display = "none";
        document.getElementById("admin-main").style.display = "flex";
        connectWebSocket();
    })
    .catch(() => {
        sessionStorage.removeItem("AGENT_TOKEN");
        document.getElementById("login-form").style.display = "block";
        document.getElementById("admin-main").style.display = "none";
    });
}
```

---

## 📊 사용자 정보 표시 위치

### 고객 화면 (chat-customer.html)

```
┌─────────────────────────────────────────────────────────┐
│  고객 상담 채팅                                         │
├─────────────────────────────────────────────────────────┤
│  배송문의 (홍길동 / cust01)     [상담원 연결] [상담종료]│
│  방 ID: room-abc123                                     │
├─────────────────────────────────────────────────────────┤
│  [채팅 메시지 영역]                                     │
│                                                         │
│  고객: 배송 문의합니다                                  │
│  Bot: 무엇을 도와드릴까요?                              │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [메시지 입력] [전송]                                   │
└─────────────────────────────────────────────────────────┘
```

**표시 정보:**
- **방 이름:** "배송문의"
- **사용자 정보:** "(홍길동 / cust01)" ← 추가됨
- **방 ID:** "room-abc123"

---

### 상담원 화면 (chat-agent.html)

```
┌─────────────────────────────────────────────────────────┐
│  상담 목록                              [로그아웃]      │
│  접속 상담원: 김상담 (agent01)   ← 추가됨              │
├─────────────────────────────────────────────────────────┤
│  - 홍길동 [상담중(나)]                                  │
│  - 김철수 [연결대기]                                    │
│  - 이영희 [챗봇상담중]                                  │
└─────────────────────────────────────────────────────────┘
```

**표시 정보:**
- **접속 상담원:** "김상담 (agent01)" ← ID 추가됨

---

## 🧪 테스트 시나리오

### 시나리오 1: 고객 화면 사용자 정보 확인

```
1. 고객 로그인
   - http://localhost:28070/chat-customer.html
   - 아이디: cust01
   - 비밀번호: 1234
   - 로그인 성공 ✅

2. 상담 시작
   - 사용자 정보 표시 확인: "홍길동 (cust01)"
   - 상담 문의: "배송문의"
   - "상담 시작" 버튼 클릭

3. 채팅 화면 확인 ✅
   - 방 이름: "배송문의"
   - 사용자 정보: "(홍길동 / cust01)" ← 확인
   - 방 ID: "room-xxx"
   
4. 표시 형식 확인
   ┌─────────────────────────────────────────┐
   │  배송문의 (홍길동 / cust01)             │
   │  방 ID: room-abc123                    │
   └─────────────────────────────────────────┘
   ✅ 사용자 이름과 ID가 방 이름 오른쪽에 표시됨
```

---

### 시나리오 2: 상담원 화면 사용자 정보 확인

```
1. 상담원 로그인
   - http://localhost:28070/chat-agent.html
   - 아이디: agent01
   - 비밀번호: 1234
   - 로그인 성공 ✅

2. 상담원 정보 확인 ✅
   - 좌측 상단 표시:
     "접속 상담원: 김상담 (agent01)" ← 확인
   
3. 다른 상담원 로그인 (새 브라우저)
   - 아이디: agent02
   - 비밀번호: 1234
   - 표시: "접속 상담원: 이상담 (agent02)" ← 확인

4. 표시 형식 확인
   ┌─────────────────────────────────────────┐
   │  상담 목록              [로그아웃]      │
   │  접속 상담원: 김상담 (agent01)          │
   └─────────────────────────────────────────┘
   ✅ 상담원 이름과 ID가 표시됨
```

---

## 💡 사용자 정보 데이터 흐름

### 고객 화면

```
1. 로그인 API 호출
   POST /api/customer/apt001/login?id=cust01&password=1234
   ↓
2. 서버 응답
   {
     "userId": "cust01",
     "userName": "홍길동",
     "role": "CUSTOMER",
     "token": "eyJ...",
     "companyId": "apt001"
   }
   ↓
3. JavaScript 변수 저장
   nickname = "홍길동"
   currentUserId = "cust01"
   ↓
4. 화면 표시
   user-info.innerText = "(홍길동 / cust01)"
```

---

### 상담원 화면

```
1. 로그인 API 호출
   POST /api/agent/login?id=agent01&password=1234
   ↓
2. 서버 응답
   {
     "userId": "agent01",
     "userName": "김상담",
     "role": "AGENT",
     "token": "eyJ..."
   }
   ↓
3. JavaScript 변수 저장
   nickname = "김상담"
   currentUserId = "agent01"
   ↓
4. 화면 표시
   displayAgentName.innerText = "김상담 (agent01)"
```

---

## 📝 변경된 파일 목록

### 프론트엔드 (2개)
- ✅ `frontend/chat-customer.html`
  - HTML: `<span id="user-info">` 추가
  - JavaScript: `currentUserId` 변수 추가
  - JavaScript: 사용자 정보 표시 로직 추가

- ✅ `frontend/chat-agent.html`
  - JavaScript: `currentUserId` 변수 추가
  - JavaScript: 상담원 정보 표시 로직 수정

---

## 🎨 화면 스크린샷 설명

### 고객 화면

#### 로그인 후 (상담 시작 전)
```
┌─────────────────────────────────────────┐
│  고객 상담 채팅                         │
├─────────────────────────────────────────┤
│  로그인                                 │
│  아이디: cust01                         │
│  비밀번호: ****                         │
│  [로그인]                               │
└─────────────────────────────────────────┘
```

#### 상담 중
```
┌─────────────────────────────────────────┐
│  배송문의 (홍길동 / cust01)  ← 추가     │
│  방 ID: room-abc123                    │
├─────────────────────────────────────────┤
│  Bot: 안녕하세요. 무엇을 도와드릴까요?  │
│  고객: 배송 문의 드립니다               │
└─────────────────────────────────────────┘
```

---

### 상담원 화면

#### 로그인 후
```
┌─────────────────────────────────────────┐
│  상담 목록              [로그아웃]      │
│  접속 상담원: 김상담 (agent01) ← 추가   │
├─────────────────────────────────────────┤
│  - 홍길동 [연결대기]                    │
│  - 김철수 [상담중(나)]                  │
└─────────────────────────────────────────┘
```

---

## ⚠️ 주의사항

### 1. 사용자 ID 노출

사용자 ID가 화면에 표시되므로:
- 보안상 민감한 정보는 ID에 포함하지 마세요
- 필요시 마스킹 처리를 고려하세요

---

### 2. 표시 형식 일관성

- **고객 화면:** `(이름 / ID)` 형식
- **상담원 화면:** `이름 (ID)` 형식
- 두 화면의 형식이 다름 (의도적)

---

### 3. 긴 이름/ID 처리

이름이나 ID가 너무 길면 UI가 깨질 수 있습니다:
- CSS에서 `text-overflow: ellipsis` 추가 고려
- 또는 최대 길이 제한

---

## 🔍 CSS 스타일

### 고객 화면 사용자 정보 스타일

```css
#user-info {
    color: #6c757d;        /* text-muted */
    margin-left: 0.5rem;   /* ms-2 */
    font-size: 0.9em;      /* 작은 크기 */
}
```

**적용 결과:**
- 회색 텍스트 (덜 눈에 띄게)
- 방 이름과 약간의 간격
- 작은 글씨 크기

---

## 🎉 완료!

고객 화면과 상담원 화면에 **사용자 ID와 이름**이 표시됩니다!

### 고객 화면 ✅
```
배송문의 (홍길동 / cust01)
방 ID: room-abc123
```

### 상담원 화면 ✅
```
접속 상담원: 김상담 (agent01)
```

**테스트:**
- 고객: http://localhost:28070/chat-customer.html
- 상담원: http://localhost:28070/chat-agent.html

**변경 사항:**
- ✅ 고객 화면에 고객 ID와 이름 표시
- ✅ 상담원 화면에 상담원 ID와 이름 표시
- ✅ 깔끔한 UI로 정보 표시
- ✅ 일관성 있는 형식 유지
