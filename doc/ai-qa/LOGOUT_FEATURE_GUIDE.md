# 고객 화면 로그아웃 기능 가이드

## 📋 개요

chat-customer.html의 '상담 시작하기' 버튼 오른쪽에 로그아웃 버튼을 추가하고, 클릭 시 모든 세션 정보를 정리하고 로그인 화면으로 돌아가는 기능을 구현했습니다.

---

## 🎨 UI 변경 사항

### 변경 전
```html
<!-- 상담 시작하기 버튼만 있음 (전체 너비) -->
<button class="btn btn-primary w-100" onclick="createRoom()">
    상담 시작하기
</button>
```

### 변경 후
```html
<!-- 상담 시작하기 + 로그아웃 버튼 (나란히 배치) -->
<div class="d-flex gap-2">
    <button class="btn btn-primary flex-grow-1" onclick="createRoom()">
        상담 시작하기
    </button>
    <button class="btn btn-outline-secondary" onclick="logout()">
        로그아웃
    </button>
</div>
```

### UI 레이아웃
```
┌──────────────────────────────────────────┐
│  고객 상담 채팅                           │
├──────────────────────────────────────────┤
│  사용자 정보                              │
│  ┌────────────────────────────────────┐  │
│  │ 홍길철 (cust01)                    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  상담 문의 (방 이름)                      │
│  ┌────────────────────────────────────┐  │
│  │ 배송문의                            │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌───────────────────┐  ┌────────────┐  │
│  │  상담 시작하기     │  │ 로그아웃   │  │
│  └───────────────────┘  └────────────┘  │
└──────────────────────────────────────────┘
```

---

## 🔧 로그아웃 함수 상세 설명

### 전체 코드
```javascript
/**
 * 로그아웃 기능
 * - WebSocket 연결이 있으면 종료
 * - 세션 스토리지 및 로컬 스토리지 정리
 * - 로그인 화면으로 복귀
 */
function logout() {
    // 확인 메시지
    if (!confirm("로그아웃 하시겠습니까?")) {
        return;
    }

    console.log("로그아웃 시작...");

    // 1. WebSocket 연결이 있으면 종료
    if (stompClient !== null && currentRoomId) {
        try {
            stompClient.send("/app/customer/chat", {}, JSON.stringify({
                roomId: currentRoomId,
                sender: nickname,
                type: 'LEAVE',
                message: nickname + "님이 나갔습니다."
            }));
            stompClient.disconnect();
            console.log("WebSocket 연결 종료됨");
        } catch (err) {
            console.error("WebSocket 종료 중 오류:", err);
        }
        stompClient = null;
    }

    // 2. 세션 스토리지 정리
    sessionStorage.removeItem("AUTH_TOKEN");
    console.log("세션 스토리지 정리 완료");

    // 3. 로컬 스토리지 정리
    localStorage.removeItem('authToken');
    localStorage.removeItem('user.userName');
    localStorage.removeItem('user.userId');
    console.log("로컬 스토리지 정리 완료");

    // 4. 전역 변수 초기화
    nickname = null;
    authToken = null;
    currentRoomId = null;

    // 5. UI 초기화 및 로그인 화면 표시
    document.getElementById("chat-box").innerHTML = "";
    document.getElementById("chat-page").style.display = "none";
    document.getElementById("connect-form").style.display = "none";
    document.getElementById("login-form").style.display = "block";
    
    // 6. 입력 필드 초기화
    document.getElementById("nickname").value = "";
    document.getElementById("roomName").value = "";
    
    console.log("로그아웃 완료");
    alert("로그아웃 되었습니다.");
}
```

---

## 📝 로그아웃 처리 단계

### 1단계: 사용자 확인
```javascript
if (!confirm("로그아웃 하시겠습니까?")) {
    return;
}
```
- 사용자에게 로그아웃 확인 메시지 표시
- 취소하면 로그아웃 중단

### 2단계: WebSocket 연결 종료
```javascript
if (stompClient !== null && currentRoomId) {
    try {
        // LEAVE 메시지 전송
        stompClient.send("/app/customer/chat", {}, JSON.stringify({
            roomId: currentRoomId,
            sender: nickname,
            type: 'LEAVE',
            message: nickname + "님이 나갔습니다."
        }));
        // WebSocket 연결 해제
        stompClient.disconnect();
    } catch (err) {
        console.error("WebSocket 종료 중 오류:", err);
    }
    stompClient = null;
}
```
- 채팅 중이었다면 LEAVE 메시지를 서버에 전송
- WebSocket 연결을 정상적으로 종료
- 에러가 발생해도 로그아웃은 계속 진행

### 3단계: 세션 스토리지 정리
```javascript
sessionStorage.removeItem("AUTH_TOKEN");
```
- 인증 토큰 삭제
- 다음 방문 시 자동 로그인 방지

### 4단계: 로컬 스토리지 정리
```javascript
localStorage.removeItem('authToken');
localStorage.removeItem('user.userName');
localStorage.removeItem('user.userId');
```
- 저장된 사용자 정보 삭제
- 완전한 로그아웃 보장

### 5단계: 전역 변수 초기화
```javascript
nickname = null;
authToken = null;
currentRoomId = null;
```
- 메모리에 남아있는 사용자 정보 삭제

### 6단계: UI 초기화
```javascript
document.getElementById("chat-box").innerHTML = "";
document.getElementById("chat-page").style.display = "none";
document.getElementById("connect-form").style.display = "none";
document.getElementById("login-form").style.display = "block";

document.getElementById("nickname").value = "";
document.getElementById("roomName").value = "";
```
- 모든 화면을 숨기고 로그인 화면만 표시
- 입력 필드 초기화

### 7단계: 완료 알림
```javascript
alert("로그아웃 되었습니다.");
```
- 사용자에게 로그아웃 완료 알림

---

## 🧪 테스트 시나리오

### 시나리오 1: 상담 전 로그아웃
```
1. 로그인 (cust01 / 1234) ✅
2. 상담 신청 화면에서 "로그아웃" 버튼 클릭 ✅
3. 확인 메시지: "로그아웃 하시겠습니까?" ✅
4. [확인] 클릭 ✅
5. "로그아웃 되었습니다." 알림 ✅
6. 로그인 화면으로 이동 ✅
7. 세션 스토리지 확인 (AUTH_TOKEN 없음) ✅
8. 로컬 스토리지 확인 (authToken, user.* 없음) ✅
```

### 시나리오 2: 상담 중 로그아웃
```
1. 로그인 후 상담 시작 ✅
2. 봇과 대화 진행 ✅
3. 브라우저 뒤로가기로 상담 신청 화면 복귀 ✅
4. "로그아웃" 버튼 클릭 ✅
5. 확인 메시지: "로그아웃 하시겠습니까?" ✅
6. [확인] 클릭 ✅
7. WebSocket LEAVE 메시지 전송 확인 (콘솔) ✅
8. 채팅방에서 "홍길철님이 나갔습니다." 메시지 표시 ✅
9. "로그아웃 되었습니다." 알림 ✅
10. 로그인 화면으로 이동 ✅
```

### 시나리오 3: 로그아웃 취소
```
1. 로그인 후 상담 신청 화면 ✅
2. "로그아웃" 버튼 클릭 ✅
3. 확인 메시지: "로그아웃 하시겠습니까?" ✅
4. [취소] 클릭 ✅
5. 로그아웃 취소됨 (화면 변화 없음) ✅
6. 계속 상담 신청 가능 ✅
```

---

## 🔍 브라우저 개발자 도구에서 확인

### 콘솔 로그
```javascript
// 로그아웃 시작
로그아웃 시작...

// WebSocket 연결 종료 (채팅 중이었을 경우)
WebSocket 연결 종료됨

// 스토리지 정리
세션 스토리지 정리 완료
로컬 스토리지 정리 완료

// 로그아웃 완료
로그아웃 완료
```

### 세션 스토리지 확인
```
F12 → Application → Session Storage → http://localhost:28070

로그아웃 전:
- AUTH_TOKEN: "eyJ1c2VySWQiOiJjdXN0MDEi..."

로그아웃 후:
- AUTH_TOKEN: (없음)
```

### 로컬 스토리지 확인
```
F12 → Application → Local Storage → http://localhost:28070

로그아웃 전:
- authToken: "eyJ1c2VySWQiOiJjdXN0MDEi..."
- user.userName: "홍길철"
- user.userId: "cust01"

로그아웃 후:
- (모두 삭제됨)
```

### 네트워크 요청 확인 (채팅 중 로그아웃)
```
F12 → Network → WS (WebSocket)

SEND 메시지:
{
  "roomId": "room-abc123",
  "sender": "홍길철",
  "type": "LEAVE",
  "message": "홍길철님이 나갔습니다."
}
```

---

## 📊 로그아웃 vs 상담 종료 비교

| 구분 | 상담 종료 (disconnect) | 로그아웃 (logout) |
|------|----------------------|------------------|
| **WebSocket 종료** | ✅ 종료 | ✅ 종료 (채팅 중이면) |
| **세션 토큰 삭제** | ❌ 유지 | ✅ 삭제 |
| **로컬 스토리지 삭제** | ❌ 유지 | ✅ 삭제 |
| **전역 변수 초기화** | 부분적 | ✅ 전체 |
| **이동 화면** | 상담 신청 화면 | 로그인 화면 |
| **자동 로그인** | ✅ 가능 | ❌ 불가능 |

---

## 🎯 주요 특징

### 1. 안전한 로그아웃
- 확인 메시지로 실수 방지
- try-catch로 에러 처리
- 단계별 정리 과정

### 2. 완전한 세션 정리
- 세션 스토리지 삭제
- 로컬 스토리지 삭제
- 전역 변수 초기화
- UI 초기화

### 3. 사용자 친화적 UI
- Bootstrap의 `d-flex`와 `gap-2` 클래스 사용
- 상담 시작 버튼은 `flex-grow-1`로 더 큰 영역 차지
- 로그아웃 버튼은 `outline-secondary` 스타일로 시각적 구분

### 4. 채팅 중에도 안전하게 로그아웃
- 채팅방에 LEAVE 메시지 전송
- WebSocket 정상 종료
- 에러 발생해도 로그아웃은 완료

---

## 💡 커스터마이징 옵션

### 1. 확인 메시지 변경
```javascript
// 현재
if (!confirm("로그아웃 하시겠습니까?")) {
    return;
}

// 상세 메시지
if (!confirm("로그아웃 하시겠습니까?\n\n진행 중인 상담이 종료됩니다.")) {
    return;
}
```

### 2. 로그아웃 버튼 스타일 변경
```html
<!-- 현재 (회색 아웃라인) -->
<button class="btn btn-outline-secondary" onclick="logout()">
    로그아웃
</button>

<!-- 빨간색 강조 -->
<button class="btn btn-outline-danger" onclick="logout()">
    로그아웃
</button>

<!-- 작은 버튼 -->
<button class="btn btn-sm btn-outline-secondary" onclick="logout()">
    로그아웃
</button>
```

### 3. 로그아웃 후 리다이렉트
```javascript
// 현재: 로그인 화면으로 이동
document.getElementById("login-form").style.display = "block";

// 또는 페이지 새로고침
window.location.reload();

// 또는 특정 URL로 이동
window.location.href = "/login";
```

### 4. 로그아웃 완료 알림 제거
```javascript
// 알림 없이 조용히 로그아웃
// alert("로그아웃 되었습니다."); // 이 줄 삭제 또는 주석 처리
```

---

## 🔒 보안 고려사항

### 1. 토큰 완전 삭제
```javascript
// 세션 스토리지
sessionStorage.removeItem("AUTH_TOKEN");

// 로컬 스토리지
localStorage.removeItem('authToken');
localStorage.removeItem('user.userName');
localStorage.removeItem('user.userId');

// 전역 변수
nickname = null;
authToken = null;
currentRoomId = null;
```

### 2. 자동 로그인 방지
- 로그아웃 후에는 저장된 토큰이 없어 자동 로그인 불가능
- 다음 방문 시 반드시 수동 로그인 필요

### 3. 채팅 기록 보호
- 로그아웃 시 채팅 화면 내용 삭제
- 메모리에 남지 않도록 초기화

---

## 🚀 다음 단계 제안

### 1. 서버 측 로그아웃 API (선택)
```javascript
// 서버에 로그아웃 알림
function logout() {
    if (!confirm("로그아웃 하시겠습니까?")) {
        return;
    }
    
    // 서버에 로그아웃 요청
    fetch('/api/customer/logout', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + authToken }
    })
    .then(() => {
        // 기존 로그아웃 로직 실행
        // ...
    })
    .catch(err => {
        console.error('로그아웃 API 오류:', err);
        // 에러가 나도 클라이언트 측 로그아웃은 진행
    });
}
```

### 2. 세션 만료 시 자동 로그아웃
```javascript
// 401 에러 발생 시 자동 로그아웃
function handleUnauthorized() {
    alert("세션이 만료되었습니다. 다시 로그인해주세요.");
    logout(); // 확인 없이 바로 로그아웃
}
```

### 3. 로그아웃 전 미저장 데이터 확인
```javascript
function logout() {
    // 채팅 중이면 추가 확인
    if (currentRoomId && stompClient !== null) {
        if (!confirm("상담 중입니다. 정말 로그아웃 하시겠습니까?")) {
            return;
        }
    }
    
    // 기존 로그아웃 로직
    // ...
}
```

---

## 📚 관련 함수

- `login()` - 로그인 처리
- `onLoginSuccess()` - 로그인 성공 후 처리
- `logout()` - 로그아웃 처리 (신규)
- `disconnect()` - 상담 종료 (로그아웃과 다름)
- `createRoom()` - 상담 시작

---

## 📞 트러블슈팅

### 문제 1: 로그아웃 후에도 자동 로그인됨
**원인:** 토큰이 완전히 삭제되지 않음

**해결:**
```javascript
// 콘솔에서 확인
console.log(sessionStorage.getItem("AUTH_TOKEN"));
console.log(localStorage.getItem("authToken"));

// 수동으로 삭제
sessionStorage.clear();
localStorage.clear();
```

### 문제 2: 로그아웃 버튼이 보이지 않음
**원인:** CSS 클래스 오류 또는 화면 크기

**해결:**
```html
<!-- gap-2를 gap-1로 줄이거나 버튼 크기 조정 -->
<div class="d-flex gap-1">
    <button class="btn btn-primary flex-grow-1" onclick="createRoom()">
        상담 시작하기
    </button>
    <button class="btn btn-sm btn-outline-secondary" onclick="logout()">
        로그아웃
    </button>
</div>
```

### 문제 3: 로그아웃 시 에러 발생
**원인:** WebSocket 종료 중 오류

**해결:**
```javascript
// 이미 try-catch로 처리되어 있음
// 에러가 발생해도 로그아웃은 계속 진행됨
```

---

## ✅ 체크리스트

- [x] 로그아웃 버튼 UI 추가
- [x] logout() 함수 구현
- [x] WebSocket 연결 종료 처리
- [x] 세션 스토리지 정리
- [x] 로컬 스토리지 정리
- [x] 전역 변수 초기화
- [x] UI 초기화 및 로그인 화면 표시
- [x] 에러 처리 추가
- [x] 사용자 확인 메시지 추가
- [x] 완료 알림 추가

---

## 🎉 완료

chat-customer.html에 로그아웃 기능이 성공적으로 추가되었습니다!
