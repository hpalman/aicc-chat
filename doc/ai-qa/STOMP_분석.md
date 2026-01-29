## ws

### 고객
##### -> CONNECT
서버 접속 요청
##### <- CONNECTED
서버 접속 완료

##### -> SUBSCRIBE  destination:/topic/room/room-04834a25
방 구독 요청

##### <- MESSAGE destination:/topic/room/room-04834a25
메시지가 룸에 송신 되었음

##### -> SEND destination:/app/customer/chat {}
고객이 메시지 송신 요청

### 상담사
##### -> CONNECT
서버 접속 요청
##### <- CONNECTED
서버 접속 완료

##### -> SUBSCRIBE destination:/topic/rooms
전체 방 목록 (상담원용) 구독 요청

##### <- MESSAGE destination:/topic/rooms
전체 방 목록 메시지

##### -> SUBSCRIBE destination:/topic/room/room-04834a25
방 구독 요청

##### -> SEND destination:/app/agent/chat, {roomId:{}, ...
상담사가 메시지 전송 요청

##### <- MESSAGE destination:/topic/room/room-04834a25 {}
메시지가 룸에 송신 되었음


