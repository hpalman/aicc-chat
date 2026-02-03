package aicc.chat.controller;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.domain.persistence.ChatSession;
import aicc.chat.service.CustomerAuthService;
import aicc.chat.service.TokenService;
import aicc.chat.service.inteface.ChatHistoryService;
import aicc.chat.service.inteface.ChatRoutingStrategy;
import aicc.chat.service.inteface.ChatSessionService;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
@Slf4j
public class ChatCustomerController {

    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
    private final TokenService tokenService;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final ChatSessionService chatSessionService;
    private final ChatHistoryService chatHistoryService;
    private final MessageBroker messageBroker;
    private final CustomerAuthService customerAuthService;

    @PostMapping("/{companyId}/login")
    // 회사별 고객 로그인 처리
    public ResponseEntity<UserInfo> login(
            @PathVariable String companyId,
            @RequestParam String id,
            @RequestParam String password) {
        log.info("▶ 회사별 고객 로그인 처리:login 시작");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = customerAuthService.login(id, password, companyId);
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

    @PostMapping("/login")
    // 기본 회사(default)로 고객 로그인 처리
    public ResponseEntity<UserInfo> loginDefault(
            @RequestParam String id,
            @RequestParam String password) {
        ResponseEntity<UserInfo> ret;
        log.info("▶ 기본 회사(default)로 고객 로그인 처리:loginDefault 시작");
        ret = login("default", id, password);
        log.info("◀ 기본 회사(default)로 고객 로그인 처리:loginDefault 완료 ");
        return ret;
    }

    @PostMapping("/logout")
    // 고객 로그아웃 처리
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▶ 고객 로그아웃 처리:logout 시작");

        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("token == null || !token.startsWith(\"Bearer \"))");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null) {
            log.warn("userInfo == null");
            return ResponseEntity.status(401).build();
        }

        // Redis에서 온라인 고객 제거
        customerAuthService.logout(userInfo.getUserId());

        log.info("◀ 고객 로그아웃 처리:logout 완료");
        return ResponseEntity.ok().build();
    }


    @PostMapping("/chat-start")
    // 고객의 챗봇 상담방을 생성하고 세션/목록을 갱신
    public ResponseEntity<ChatRoom> createRoomWithBot(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ChatRoom requestBody) {
        ResponseEntity<ChatRoom> ret;

        log.info("▶ 고객의 챗봇 상담방을 생성하고 세션/목록을 갱신:createRoomWithBot 시작");

        do {
            if (token == null || !token.startsWith("Bearer ")) {
                log.warn("token null or not startsWith");
                ret = ResponseEntity.status(401).build();
                break;
            }
            String actualToken = token.substring(7);
            UserInfo custInfo = tokenService.validateToken(actualToken);
            if (custInfo == null) {
                log.warn("custInfo == null");
                ret = ResponseEntity.status(401).build();
                break;
            }
String userId = custInfo.getUserId();            
            if ( roomRepository.existCustomer(userId) ) {
            	return ResponseEntity.status(409).build();            	
            }

String roomName = requestBody.getRoomName();
            customerAuthService.setChatInitInfo(custInfo); // Constants.ONLINE_CUSTOMERS_KEY : "chat:user-customers:{custId}" 해시값 넣음

            String newRoomId = "room-" + UUID.randomUUID().toString().substring(0, 8);
            ChatRoom room = roomRepository.createRoom(newRoomId, roomName /* custInfo.getUserId() */); // 룸 생성(Redis에 키 및 값들 넣음)

            roomRepository.addMember(newRoomId, custInfo.getUserId()); // 고객을 멤버로 추가

            // Redis에 고객의 roomId 업데이트
            customerAuthService.updateRoomId(custInfo.getUserId(), newRoomId);

            // PostgreSQL에 세션 정보 저장
            try {
                ChatSession chatSession = ChatSession.builder()
                        .roomId(newRoomId)
                        .roomName(roomName /*=배송문의 custInfo.getUserId() */)
                        .customerId(custInfo.getUserId())
                        .customerName(custInfo.getUserName())
                        .status("BOT")
                        .companyId(custInfo.getCompanyId())
                        .startedAt(LocalDateTime.now())
                        .lastActivityAt(LocalDateTime.now())
                        .build();
                chatSessionService.createChatSession(chatSession); // DB
                log.info("Chat session saved to DB: roomId={}", newRoomId);
            } catch (Exception e) {
                log.error("Failed to save chat session to DB: roomId={}", newRoomId, e);
                // DB 저장 실패해도 채팅은 계속 진행
            }

            routingStrategy.onRoomCreated(room);
            roomUpdateBroadcaster.broadcastRoomList();
            ret = ResponseEntity.ok(room);
        } while (false);
        log.info("◀ 고객의 챗봇 상담방을 생성하고 세션/목록을 갱신:createRoomWithBot 완료 ");
        return ret;
    }

    @PostMapping("/chat-end")
    // 고객의 상담 종료 처리
    public ResponseEntity<?> chatEnd(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ endChat S. Customer request endChat");
        ResponseEntity<?> ret;

        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("▲ endChat E. token == null || !token.startsWith(\"Bearer \")");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null) {
            log.warn("▲ endChat E. userInfo == null");
            return ResponseEntity.status(401).build();
        }
String userId    = userInfo.getUserId();
String companyId = userInfo.getCompanyId();
String userName  = userInfo.getUserName();

        // 고객의 현재 roomId 조회
        String roomId = customerAuthService.getRoomId(userId);
        if (roomId == null) {
            log.info("▲ endChat E. roomId == null. 활성화된 상담방이 없습니다.");
            return ResponseEntity.status(404).body("활성화된 상담방이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        try {
            ChatRoom room = roomRepository.findRoomById(roomId);
            if (room == null) {
                log.info("▲ endChat E. Room not found: roomId={}", roomId);
                return ResponseEntity.status(404).body("상담방을 찾을 수 없습니다.");
            }

            // 채팅방 상태를 CLOSED로 변경
            roomRepository.setRoutingMode(roomId, "CLOSED");

            // 고객과 상담원에게 종료 메시지 전송
            ChatMessage endMessage = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message("고객이 상담을 종료했습니다.")
                    .type(MessageType.LEAVE)
                    .timestamp(now)
                    .companyId(companyId)
                    .build();
            messageBroker.publish(endMessage);

            // 상담원이 배정된 경우 상담원에게도 알림
            if (room.getAssignedAgent() != null) {
                ChatMessage agentNotice = ChatMessage.builder()
                        .roomId(roomId)
                        .sender("System")
                        .senderRole(UserRole.SYSTEM)
                        .message(userName + " 고객이 상담을 종료했습니다.")
                        .type(MessageType.CUSTOMER_LEFT)
                        .timestamp(now)
                        .companyId(companyId)
                        .build();
                messageBroker.publish(agentNotice);
            }

            // PostgreSQL에 세션 종료 기록
            chatSessionService.updateSessionStatus(roomId, "CLOSED"); // DB
            chatSessionService.endSession(roomId); // DB

            // 종료 메시지를 이력에 저장
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(roomId)
                    .senderId("system")
                    .senderName("System")
                    .senderRole("SYSTEM")
                    .message("고객이 상담을 종료했습니다.")
                    .messageType("LEAVE")
                    .companyId(companyId)
                    .createdAt(now)
                    .build();
            chatHistoryService.saveChatHistory(chatHistory); // DB

            // Redis에서 채팅방 관련 모든 키 삭제
            // 1. chat:rooms에서 roomId 제거
            // 2. chat:room-info:{roomId} Hash 삭제
            // 3. chat:room-member:{roomId} Set 삭제
            //roomRepository.deleteRoom(roomId);
            //log.info("✅ Redis room keys deleted: roomId={}", roomId);

            // Redis에서 고객의 roomId 제거 및 고객 정보 삭제
            // chat:user-customers:{userId} Hash 삭제
            //customerAuthService.updateRoomId(userId, null);
            customerAuthService.deleteCustomer(userId, roomId);

            customerAuthService.logout(userId);
            log.info("✅ Redis customer keys deleted: userId={}", userId);

            // 상담원에게 채팅방 목록 업데이트 브로드캐스트
            roomUpdateBroadcaster.broadcastRoomList();

            ret = ResponseEntity.ok(Map.of("message", "상담이 종료되었습니다.", "roomId", roomId));
            log.info("✅ Customer chat ended successfully: roomId={}, userId={}", roomId, userId);
        } catch (Exception e) {
            log.error("❌ Failed to end customer chat: roomId={}", roomId, e);
            ret = ResponseEntity.status(500).body("상담 종료 처리 중 오류가 발생했습니다.");
        }

        log.info("▲ endChat E. ret:{}", ret);
        return ret;
    }


/*
    ["SEND\ndestination:/app/customer/chat\ncontent-length:107\n\n{\"roomId\":\"room-dba1f913\",\"sender\":\"홍길철\",\"type\":\"LEAVE\",\"message\":\"홍길철님이 나갔습니다.\"}\u0000"]
    StompHeaderAccessor [headers={simpMessageType=MESSAGE, stompCommand=SEND, nativeHeaders={destination=[/app/customer/chat], content-length=[107]}, simpSessionAttributes={userName=홍길철, userId=cust01, roomId=room-6c736bd7, companyId=apt001, org.springframework.messaging.simp.SimpAttributes.COMPLETED=true, userEmail=cust01@example.com, userRole=CUSTOMER}, simpHeartbeat=[J@11a9323d, lookupDestination=/customer/chat, simpSessionId=mlgk5gek, simpDestination=/app/customer/chat}]
*/
    @MessageMapping("/customer/chat")
    // 고객 메시지를 받아 이력 저장 후 라우팅
    public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        log.info("▶ 고객 메시지를 받아 이력 저장 후 라우팅:onCustomerMessage 시작");
        String sessionId = headerAccessor.getSessionId();
        log.info("sessionId:{}, MessageType:{}", sessionId, message.getType().toString()); // sessionId:xxfuatci, MessageType:LEAVE

        //WebSocketSessionAttribute attr = WebSocketAttributes.getSimpSessionAttributes((StompHeaderAccessor)headerAccessor);
        //log.info("attr:{}", attr);

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String userId = null;

        // 서버에서 메시지 수신 시간 설정
        message.setTimestamp(LocalDateTime.now());
// 세션id
// userId
// roomId
        if (sessionAttributes != null) {
            String roomId = (String) sessionAttributes.get("roomId");
            String userName = (String) sessionAttributes.get("userName");
            String companyId = (String) sessionAttributes.get("companyId");
            userId = (String) sessionAttributes.get("userId");

            // 고객 전용 로직: 세션의 roomId와 userName으로 강제 고정
            if (roomId != null)
            	message.setRoomId(roomId);
            if (userName != null)
            	message.setSender(userName);
            if (companyId != null)
            	message.setCompanyId(companyId);
            message.setSenderRole(UserRole.CUSTOMER);
        }

        log.debug("Customer message received for room: {} at {}", message.getRoomId(), message.getTimestamp());

        // 고객이 LEAVE 메시지를 보낸 경우 상담원에게 알림
        if (MessageType.LEAVE.equals(message.getType())) {
            log.info("🔔 고객 퇴장 메시지 감지 - roomId: {}, userId: {}", message.getRoomId(), userId);

            try {
                ChatRoom room = roomRepository.findRoomById(message.getRoomId());

                if (room != null && room.getAssignedAgent() != null) {
                    // 상담원이 배정된 경우 상담원에게 알림
                    log.info("  - assignedAgent: {}", room.getAssignedAgent());

                    ChatMessage leaveNotice = ChatMessage.builder()
                            .roomId(message.getRoomId())
                            .sender("System")
                            .senderRole(UserRole.SYSTEM)
                            .message(message.getSender() + " 고객이 상담을 종료했습니다.")
                            .type(MessageType.CUSTOMER_LEFT)
                            .timestamp(LocalDateTime.now())
                            .build();

                    messageBroker.publish(leaveNotice);

                    log.info("✅ 고객 퇴장 알림 전송 완료!");
                } else {
                    log.info("  ℹ️ 상담원이 배정되지 않은 방 - 알림 전송 생략");
                }
            } catch (Exception e) {
                log.error("❌ 고객 퇴장 알림 전송 실패", e);
            }
        }

        // PostgreSQL에 채팅 이력 저장
        try {
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(message.getRoomId())
                    .senderId(userId != null ? userId : message.getSender())
                    .senderName(message.getSender())
                    .senderRole(message.getSenderRole().name())
                    .message(message.getMessage())
                    .messageType(message.getType().name())
                    .companyId(message.getCompanyId())
                    .createdAt(message.getTimestamp()) // 서버 타임스탬프 사용
                    .build();
            chatHistoryService.saveChatHistory(chatHistory); // DB

            // 세션의 마지막 활동 시간 DB 업데이트
            chatSessionService.updateLastActivity(message.getRoomId()); // DB
        } catch (Exception e) {
            log.error("Failed to save chat history to DB: roomId={}", message.getRoomId(), e);
            // DB 저장 실패해도 채팅은 계속 진행
        }

        roomRepository.updateLastActivity(message.getRoomId()); // REDIS
        routingStrategy.handleMessage(message.getRoomId(), message);
        log.info("◀ 고객 메시지를 받아 이력 저장 후 라우팅:onCustomerMessage 완료 ");
    }

}

