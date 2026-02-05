package aicc.chat.controller;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
//import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
//import aicc.chat.domain.UserRole;
//import aicc.chat.domain.persistence.ChatHistory;
//import aicc.chat.domain.persistence.ChatSession;
import aicc.chat.service.ChatCustomerService;
import aicc.chat.service.CustomerAuthService;
//import aicc.chat.service.RoomUpdateBroadcaster;
import aicc.chat.service.TokenService;
import aicc.chat.util.UtilString;
//import aicc.chat.service.inteface.ChatHistoryService;
//import aicc.chat.service.inteface.ChatRoutingStrategy;
//import aicc.chat.service.inteface.ChatSessionService;
//import aicc.chat.service.inteface.MessageBroker;
//import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
@Slf4j
public class ChatCustomerController {

    //private final RoomRepository        roomRepository;
    //private final ChatRoutingStrategy   routingStrategy;
    private final TokenService          tokenService;
    //private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    //private final ChatSessionService    chatSessionService;
    //private final ChatHistoryService    chatHistoryService;
    //private final MessageBroker         messageBroker;
    private final CustomerAuthService   customerAuthService;

    private final ChatCustomerService   chatCustomerService;
    @PostMapping("/{companyId}/login")
    // 회사별 고객 로그인 처리
    public ResponseEntity<UserInfo> login(
            @PathVariable String companyId,
            @RequestBody Map<String,String> body
    ) {
        String id = body.get("id");
        String pw = body.get("pw");

        log.info("▶ 회사별 고객 로그인 처리:login 시작");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = customerAuthService.login(id, pw, companyId);
        if (userInfo == null) {
            ret = ResponseEntity.status(401).build();
        } else {
            if ( userInfo.getStatus() != 0 ) {
                ret = ResponseEntity.status(409).build();
            } else {
                ret = ResponseEntity.ok(userInfo);
            }
        }
        log.info("◀ 회사별 고객 로그인 처리:login 완료 ");
        return ret;
    }

    @PostMapping("/logout")
    // 고객 로그아웃 처리
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
        log.info("▶ 고객 로그아웃 처리:logout 시작");

        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).build();
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null) {
            log.warn("userInfo == null");
            return ResponseEntity.status(401).build();
        }

        // Redis에서 온라인 고객 제거
        customerAuthService.logout(userInfo.getUserId());

        log.info("◀ 고객 로그아웃 처리:logout 완료");
        return ResponseEntity.ok().build();
    }

    /**
     * 고객의 챗봇 상담방을 생성하고 세션/목록을 갱신
     * @param token
     * @param requestBody
     * @return
     */
    @PostMapping("/chat-start")
    public ResponseEntity<ChatRoom> chatStart(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ChatRoom requestBody) {
        log.info("▼ chatStart S. token:{}, chatRoom:{}", UtilString.leftRight(token,15,5), requestBody);
        ResponseEntity<ChatRoom> ret =
                chatCustomerService._chatStart(token, requestBody);
        log.info("▲ chatStart E. ret:{}", ret);
        return ret;
    }

    /**
     * 고객의 상담 종료 처리
     * @param token
     * @return
     */
    @PostMapping("/chat-end")
    public ResponseEntity<?> chatEnd(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody Map<String,Object> body) {

        Object roomId = body.get("roomId");
        log.info("▼ chatEnd S. token:{}, roomId:{}", UtilString.leftRight(token,15,5), roomId);

        ResponseEntity<?> ret;
        try {
            ret = chatCustomerService._chatEnd(token, roomId);
        } catch (Exception e) {
            log.error("❌ chatEnd error.", e);
            ret = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        log.warn("▲ chatEnd E.");
        return ret;
    }

    // 최근 채팅 정보
    @GetMapping("/chat-lastinfo")
    public ResponseEntity<?> chatLastInfo(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ chatLastInfo S. token:{}", UtilString.leftRight(token,15,5));

        ResponseEntity<?> ret;
        try {
            ret = chatCustomerService.chatLastInfo(token);
        } catch (Exception e) {
            log.error("❌ chatLastInfo error.", e);
            ret = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        log.warn("▲ chatLastInfo E. {}", ret);
        return ret;
    }

/*
    ["SEND\ndestination:/app/customer/chat\ncontent-length:107\n\n{\"roomId\":\"room-dba1f913\",\"sender\":\"홍길철\",\"type\":\"LEAVE\",\"message\":\"홍길철님이 나갔습니다.\"}\u0000"]
    StompHeaderAccessor [headers={simpMessageType=MESSAGE, stompCommand=SEND, nativeHeaders={destination=[/app/customer/chat], content-length=[107]}, simpSessionAttributes={userName=홍길철, userId=cust01, roomId=room-6c736bd7, companyId=apt001, org.springframework.messaging.simp.SimpAttributes.COMPLETED=true, userEmail=cust01@example.com, userRole=CUSTOMER}, simpHeartbeat=[J@11a9323d, lookupDestination=/customer/chat, simpSessionId=mlgk5gek, simpDestination=/app/customer/chat}]
*/
    /**
     * 고객 메시지를 받아 이력 저장 후 라우팅
     * @param message
     * @param headerAccessor
     */
    @MessageMapping("/customer/chat")
    public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        log.info("▼ onCustomerMessage S. 고객 메시지 처리. S");
        chatCustomerService.customerMessage(message, headerAccessor);
        log.info("▼ onCustomerMessage E. 고객 메시지 처리. E");
    }

}

