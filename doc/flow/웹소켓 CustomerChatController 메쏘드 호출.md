### 웹소켓 CustomerChatController 메쏘드 호출

클라이언트 UI에서 stompClient.send("/app/customer/chat", ...)로 STOMP SEND를 보내면, Spring의 STOMP 라우팅이 /app 프리픽스를 제거하고 @MessageMapping("/customer/chat")에 매핑된 메서드로 디스패치합니다. 그래서 /app/customer/chat → CustomerChatController.onCustomerMessage(...)가 호출됩니다.


클라이언트 전송 위치: frontend/chat-customer.html의 stompClient.send("/app/customer/chat", ...) 호출

chat-customer.html

```javascript
stompClient.send("/app/customer/chat", {}, JSON.stringify({
    roomId: currentRoomId,
    sender: nickname,
    type: 'LEAVE',
    message: nickname + "님이 나갔습니다."
}));
```

* 서버 매핑 근거: WebSocketConfig에서 setApplicationDestinationPrefixes("/app") 설정
WebSocketConfig.java
```java
public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
    log.info("▶ configureMessageBroker (/topic, /app) S");
    registry.enableSimpleBroker("/topic"); // 서버에서 클라이언트로 broadcast 하는 prefix
    // ["SUBSCRIBE\nid:sub-0\ndestination:/topic/room/room-161cedaa\n\n\u0000"]


    registry.setApplicationDestinationPrefixes("/app"); // 서버로 보내는 메세지 prefix
    // ["SEND\ndestination:/app/customer/chat\ncontent-length:116\n\n{\"roomId\":\"room-161cedaa\",\"sender\":\"고객-5bf4\",\"type\":\"JOIN\",\"message\":\"고객-5bf4님이 입장하셨습니다.\"}\u0000"]
    log.info("◀ configureMessageBroker E");
}
```

* 실제 핸들러: CustomerChatController.onCustomerMessage가 @MessageMapping("/customer/chat")로 등록
CustomerChatController.java
```java
@MessageMapping("/customer/chat")
// 고객 메시지를 받아 이력 저장 후 라우팅
public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
    log.info("▶ 고객 메시지를 받아 이력 저장 후 라우팅:onCustomerMessage 시작");
    Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
    String userId = null;
```

즉, UI에서 /app/customer/chat로 보내는 STOMP 메시지가 @MessageMapping("/customer/chat")에 매핑되어 onCustomerMessage가 호출됩니다.
