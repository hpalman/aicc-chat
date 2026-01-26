# websocket-client.html 에러 처리 개선

## 변경 사항

### 1. 로그인 API (`/api/customer/apt001/login`) 에러 처리 강화

#### 개선 전
```javascript
fetch(`/api/customer/apt001/login?id=${id}&password=${pw}`, { method: 'POST' })
    .then(res => res.json())
    .then(user => {
        if (user.status == 200) {
            sessionStorage.setItem("AUTH_TOKEN", user.token);
            onLoginSuccess(user);
        }
    })
    .catch(err => {
        alert("로그인에 실패했습니다.");
    });
```

#### 개선 후
```javascript
fetch(`/api/customer/apt001/login?id=${id}&password=${pw}`, { method: 'POST' })
    .then(res => {
        // HTTP 상태 코드 체크
        if (!res.ok) {
            if (res.status === 401) {
                throw new Error('아이디 또는 비밀번호가 올바르지 않습니다.');
            } else if (res.status === 500) {
                throw new Error('서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
            } else if (res.status === 404) {
                throw new Error('로그인 서비스를 찾을 수 없습니다.');
            } else {
                throw new Error(`로그인 실패 (상태 코드: ${res.status})`);
            }
        }
        return res.json();
    })
    .then(user => {
        // 응답 데이터 검증
        if (!user) {
            throw new Error('로그인 응답이 올바르지 않습니다.');
        }
        
        if (user.status === 200) {
            if (!user.token) {
                throw new Error('인증 토큰을 받지 못했습니다.');
            }
            sessionStorage.setItem("AUTH_TOKEN", user.token);
            onLoginSuccess(user);
        } else {
            const errorMsg = user.message || user.error || '로그인에 실패했습니다.';
            throw new Error(errorMsg);
        }
    })
    .catch(err => {
        console.error('로그인 오류:', err);
        alert(err.message || "로그인에 실패했습니다. 다시 시도해주세요.");
    });
```

#### 추가된 검증
- ✅ 입력값 검증 (아이디/비밀번호 입력 확인)
- ✅ HTTP 상태 코드별 에러 메시지
  - 401: 아이디 또는 비밀번호 오류
  - 404: 로그인 서비스 없음
  - 500: 서버 오류
- ✅ 응답 데이터 검증 (user 객체, token 존재 여부)
- ✅ 에러 로그 출력 (콘솔)
- ✅ 사용자 친화적인 에러 메시지

---

### 2. 초기 로딩 시 토큰 검증 개선

#### 개선 사항
- HTTP 상태 코드 체크 추가
- 응답 데이터 검증 추가
- 에러 로그 출력
- 잘못된 토큰 자동 제거

```javascript
window.onload = function() {
    const token = sessionStorage.getItem("AUTH_TOKEN");
    console.log('토큰 확인: [' + token + ']');
    
    if (token) {
        fetch('/api/me', {
            headers: { 'Authorization': 'Bearer ' + token }
        })
        .then(res => {
            if (!res.ok) {
                console.log('저장된 토큰이 유효하지 않습니다.');
                sessionStorage.removeItem("AUTH_TOKEN");
                throw new Error('Invalid token');
            }
            return res.json();
        })
        .then(user => {
            if (user && user.userName) {
                onLoginSuccess(user);
            } else {
                throw new Error('Invalid user data');
            }
        })
        .catch(err => {
            console.log('자동 로그인 실패:', err.message);
            sessionStorage.removeItem("AUTH_TOKEN");
            document.getElementById("login-form").style.display = "block";
        });
    } else {
        console.log('저장된 토큰이 없습니다. 로그인이 필요합니다.');
        document.getElementById("login-form").style.display = "block";
    }
}
```

---

### 3. 상담방 생성 API 에러 처리 개선

#### 개선 사항
- HTTP 상태 코드별 에러 처리
- 응답 데이터 검증 (room 객체, roomId 확인)
- 세션 만료 시 자동 로그아웃
- 상세한 에러 메시지

```javascript
fetch(`/api/customer/chatbot`, { 
    method: 'POST',
    headers: { 'Authorization': 'Bearer ' + authToken }
})
    .then(res => {
        if (!res.ok) {
            if (res.status === 401) {
                alert("세션이 만료되었습니다. 다시 로그인해주세요.");
                sessionStorage.removeItem("AUTH_TOKEN");
                window.location.reload();
                throw new Error('Unauthorized');
            } else if (res.status === 500) {
                throw new Error('서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
            } else if (res.status === 404) {
                throw new Error('상담 서비스를 찾을 수 없습니다.');
            } else {
                throw new Error(`상담 연결 실패 (상태 코드: ${res.status})`);
            }
        }
        return res.json();
    })
    .then(room => {
        if (!room || !room.roomId) {
            throw new Error('방 정보를 받지 못했습니다.');
        }
        console.log("상담방 생성 완료:", room);
        currentRoomId = room.roomId;
        updateHandoffButtons(room.status);
        connect();
    })
    .catch(err => {
        console.error('상담방 생성 오류:', err);
        if (err.message !== 'Unauthorized') {
            alert(err.message || "상담 연결에 실패했습니다. 다시 시도해주세요.");
        }
    });
```

---

### 4. WebSocket 연결 에러 처리 추가

#### 개선 사항
- WebSocket 연결 실패 콜백 추가
- try-catch로 초기화 오류 처리
- 연결 실패 시 사용자 알림
- 연결 실패 시 상담 신청 화면으로 복귀

```javascript
function connect() {
    console.log('WebSocket 연결 시작 - authToken[' + authToken + '], currentRoomId[' + currentRoomId +']');
    
    try {
        const socket = new SockJS(`/ws-chat?token=${authToken}&roomId=${currentRoomId}`);
        stompClient = Stomp.over(socket);

        // 연결 성공 콜백
        stompClient.connect({}, function (frame) {
            console.log('WebSocket 연결 성공: ' + frame);
            // ... 정상 로직
        }, function(error) {
            // 연결 실패 콜백
            console.error('WebSocket 연결 실패:', error);
            alert('채팅 서버 연결에 실패했습니다.\n\n' + 
                  (error.headers?.message || '네트워크 상태를 확인해주세요.'));
            
            // 연결 폼으로 되돌리기
            document.getElementById("chat-page").style.display = "none";
            document.getElementById("connect-form").style.display = "block";
            currentRoomId = null;
        });
    } catch (err) {
        console.error('WebSocket 초기화 오류:', err);
        alert('채팅 연결 초기화에 실패했습니다.\n다시 시도해주세요.');
        
        // 연결 폼으로 되돌리기
        document.getElementById("chat-page").style.display = "none";
        document.getElementById("connect-form").style.display = "block";
        currentRoomId = null;
    }
}
```

---

## 에러 메시지 정리

### 로그인 관련
| 상황 | 에러 메시지 |
|------|------------|
| 아이디/비밀번호 미입력 | "아이디와 비밀번호를 입력해주세요." |
| 401 Unauthorized | "아이디 또는 비밀번호가 올바르지 않습니다." |
| 404 Not Found | "로그인 서비스를 찾을 수 없습니다." |
| 500 Server Error | "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요." |
| 토큰 없음 | "인증 토큰을 받지 못했습니다." |
| 응답 없음 | "로그인 응답이 올바르지 않습니다." |

### 상담방 생성 관련
| 상황 | 에러 메시지 |
|------|------------|
| 문의 내용 미입력 | "상담 문의 내용을 입력해주세요." |
| 401 Unauthorized | "세션이 만료되었습니다. 다시 로그인해주세요." (자동 로그아웃) |
| 404 Not Found | "상담 서비스를 찾을 수 없습니다." |
| 500 Server Error | "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요." |
| roomId 없음 | "방 정보를 받지 못했습니다." |

### WebSocket 연결 관련
| 상황 | 에러 메시지 |
|------|------------|
| 연결 실패 | "채팅 서버 연결에 실패했습니다.\n네트워크 상태를 확인해주세요." |
| 초기화 실패 | "채팅 연결 초기화에 실패했습니다.\n다시 시도해주세요." |

---

## 테스트 시나리오

### 1. 로그인 에러 테스트
```
1. 아이디/비밀번호 미입력 후 로그인 → "아이디와 비밀번호를 입력해주세요."
2. 잘못된 아이디/비밀번호 입력 → "아이디 또는 비밀번호가 올바르지 않습니다."
3. 서버 중지 상태에서 로그인 → "로그인 서비스를 찾을 수 없습니다." 또는 네트워크 오류
```

### 2. 상담방 생성 에러 테스트
```
1. 문의 내용 미입력 후 상담 시작 → "상담 문의 내용을 입력해주세요."
2. 만료된 토큰으로 상담 시작 → "세션이 만료되었습니다." + 자동 로그아웃
3. 서버 중지 상태에서 상담 시작 → "상담 서비스를 찾을 수 없습니다." 또는 서버 오류
```

### 3. WebSocket 연결 에러 테스트
```
1. 서버 WebSocket 엔드포인트 비활성화 → "채팅 서버 연결에 실패했습니다."
2. 잘못된 토큰으로 WebSocket 연결 → 연결 실패 메시지 + 상담 신청 화면 복귀
```

---

## 사용자 경험 개선 사항

1. ✅ **명확한 에러 메시지**: 각 상황에 맞는 구체적인 안내
2. ✅ **자동 복구**: 세션 만료 시 자동 로그아웃 및 재로그인 유도
3. ✅ **에러 로깅**: 개발자 콘솔에 상세 에러 로그 출력
4. ✅ **UI 복구**: 연결 실패 시 이전 화면으로 자동 복귀
5. ✅ **입력 검증**: 사전에 잘못된 입력 차단
6. ✅ **HTTP 상태 코드 활용**: 서버 응답 상태에 따른 적절한 처리

---

## 디버깅 가이드

### 콘솔 로그 확인
```
F12 → Console 탭에서 다음 로그 확인:
- "로그인 오류:" - 로그인 실패 원인
- "상담방 생성 오류:" - 상담방 생성 실패 원인
- "WebSocket 연결 실패:" - WebSocket 연결 오류
- "WebSocket 초기화 오류:" - WebSocket 초기화 오류
```

### 네트워크 요청 확인
```
F12 → Network 탭에서 다음 요청 확인:
- /api/customer/apt001/login (로그인)
- /api/customer/chatbot (상담방 생성)
- /ws-chat (WebSocket 연결)

각 요청의 Status Code 및 Response 확인
```
