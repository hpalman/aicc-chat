package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.domain.redis.RoomInfo;
import aicc.chat.domain.redis.UserCustomer;
import aicc.chat.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.domain.persistence.ChatSession;
import aicc.chat.service.ChatCustomerService;
import aicc.chat.service.CustomerAuthService;
import aicc.chat.service.RoomUpdateBroadcaster;
import aicc.chat.service.TokenService;
import aicc.chat.service.inteface.ChatHistoryService;
import aicc.chat.service.inteface.ChatRoutingStrategy;
import aicc.chat.service.inteface.ChatSessionService;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import aicc.chat.util.UtilString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCustomerService  extends ChatService {

    @Value("${app.auth.login-api-url}")
    private String loginApiUrl;

    private final TokenService        tokenService;
    private final UserAccountMapper   userAccountMapper;
    private final StringRedisTemplate redisTemplate;

    private final RoomRepository        roomRepository;
    private final ChatRoutingStrategy   routingStrategy;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final ChatSessionService    chatSessionService;
    private final ChatHistoryService    chatHistoryService;
    private final MessageBroker         messageBroker;
    private final CustomerAuthService   customerAuthService;

    private final ObjectMapper mapper; // 자동 주입됨

    /**
     * 고객의 상담 종료 처리
     * @param bearerToken
     * @return
     */
    public ResponseEntity<?> _chatEnd(String bearerToken, Object _roomId) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String userId     = userInfo.getUserId();
        String companyId  = userInfo.getCompanyId();
        String userName   = userInfo.getUserName();

        //String yourRoomId = userInfo.getRoomId(); // 토큰에 실린 roomId
        String roomId = _roomId.toString(); // customerAuthService.getRoomId(userId); // REDIS. chat:user-customer:{userId} 고객의 현재 roomId 조회
        if (_roomId == null) {
            log.info("▶ userId:{}, roomId == null. 활성화된 상담방이 없습니다.", userId);
            return ResponseEntity.ok(Map.of("code",-1, "message", "활성화된 상담방이 없습니다.", "userId",userId));
        }

        ChatRoom room = roomRepository.findRoomById(roomId); // REDIS. chat:room-info:{roomId}
        if (room == null) {
            log.info("▶ Room not found: roomId={}", roomId);
            return ResponseEntity.ok(Map.of("code",-1, "message", "상담방을 찾을 수 없습니다.", "roomId", roomId));
        }

        // 채팅방 상태를 CLOSED로 변경
        roomRepository.setRoutingMode(roomId, "CLOSED"); // REDIS.  "chat:room-info:{roomId} { routingMode CLOSED }

        // 고객과 상담사에게 종료 메시지 전송
        ChatMessage endMessage = ChatMessage.builder()
                .roomId(roomId)
                .sender("System")
                .senderRole(UserRole.SYSTEM)
                .message("고객이 상담을 종료했습니다.")
                .type(MessageType.LEAVE)
                .companyId(companyId)
                .build();
        messageBroker.publish(endMessage);

        // 상담사가 배정된 경우 상담사에게도 알림
        if (room.getAssignedAgent() != null) {
            ChatMessage agentNotice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(userName + " 고객이 상담을 종료했습니다.")
                    .type(MessageType.CUSTOMER_LEFT)
                    .companyId(companyId)
                    .build();
            messageBroker.publish(agentNotice);
        }

        // @TODO: 임시 DB저장막음
        // // PostgreSQL에 세션 종료 기록
        // chatSessionService.updateSessionStatus(roomId, "CLOSED"); // DB
        // chatSessionService.endSession(roomId); // DB
        //
        // // 종료 메시지를 이력에 저장
        // ChatHistory chatHistory = ChatHistory.builder()
        //         .roomId(roomId)
        //         .senderId("system")
        //         .senderName("System")
        //         .senderRole("SYSTEM")
        //         .message("고객이 상담을 종료했습니다.")
        //         .messageType("LEAVE")
        //         .companyId(companyId)
        //         .createdAt(now)
        //         .build();
        // chatHistoryService.saveChatHistory(chatHistory); // DB

        customerAuthService.logout(userId); // REDIS. chat:user-customer:{userId}
        log.info("▶ Redis customer keys deleted: userId={}", userId);

        // 상담사에게 채팅방 목록 업데이트 브로드캐스트
        roomUpdateBroadcaster.broadcastRoomList(); // WebSocket MSG. /topic/rooms의 모든 목록을 전파

        log.info("▶ Customer chat ended successfully: roomId={}, userId={}", roomId, userId);
        return ResponseEntity.ok(Map.of("code",0, "message", "상담이 종료되었습니다.", "roomId", roomId));
    }


    /**
     * - 토큰 유효성 확인
     * - 고유한 방ID 생성
     * 1. 고객 정보 생성
     * 1. 고객의 챗봇 상담방을 생성
     * 하고 세션/목록을 갱신
     * @param bearerToken
     * @param requestBody
     * @return
     */
    public  ResponseEntity<ChatRoom> _chatStart(String bearerToken,ChatRoom chatRoom) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).build();
        }

        UserInfo custInfo = tokenService.parseToken(bearerToken);
        if (custInfo == null) {
            return ResponseEntity.status(401).build();
        }

        String userId   = custInfo.getUserId();
        String roomName = chatRoom.getRoomName();
        // @TODO: 잠시 막음
        //if ( roomRepository.existCustomer(userId) ) {
        //    return ResponseEntity.status(409).build();
        //}

        String newRoomId;
        if ( UtilString.isNotEmpty(chatRoom.getRoomId()) ) {
            newRoomId = chatRoom.getRoomId();
        } else {
            newRoomId = customerAuthService.newRoomId(userId); // ROOM ID 생성
        }

        customerAuthService.setUserCustomers(custInfo, newRoomId); // Constants.USER_CUSTOMER_KEY : "chat:user-customer:{custId}" 해시값 넣음

        ChatRoom room = roomRepository.createRoom(newRoomId, roomName, userId); // REDIS. chat:room-info:{roomId}, chat:rooms -> room-c7db3f46 룸 생성(Redis에 키 및 값들 넣음)

        // chat:room-member
        roomRepository.addMember(newRoomId, custInfo.getUserId()); // REDIS 고객을 멤버로 추가

        // Redis에 고객의 roomId 업데이트
        // customerAuthService.updateRoomId(custInfo.getUserId(), newRoomId); // REDIS

        // @TODO: 잠시 막음
        // PostgreSQL에 세션 정보 저장
        // try {
        //     ChatSession chatSession = ChatSession.builder()
        //             .roomId(newRoomId)
        //             .roomName(roomName) // ex. 배송문의
        //             .customerId(custInfo.getUserId())
        //             .customerName(custInfo.getUserName())
        //             .status("BOT")
        //             .companyId(custInfo.getCompanyId())
        //             .startedAt(LocalDateTime.now())
        //             .lastActivityAt(LocalDateTime.now())
        //             .build();
        //     chatSessionService.createChatSession(chatSession); // DB
        //     log.info("▶ Chat session saved to DB: roomId={}", newRoomId);
        // } catch (Exception e) { // DB 저장 실패해도 채팅은 계속 진행
        //     log.error("▶ Failed to save chat session to DB: roomId={}", newRoomId, e);
        // }

        // 1) chat:room-info:{roomId} routingMode:MODE_BOT
        // 2) room에 환영 메시지 발송
        routingStrategy.onRoomCreated(room); // REDIS

        // "/topic/rooms"에 메시지 PUB
        roomUpdateBroadcaster.broadcastRoomList(); // REDIS & WEBSOCKET

        return ResponseEntity.ok(room);
    }

    // 고객 메시지를 받아 이력 저장 후 라우팅
    public void customerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        message.setTimestamp(LocalDateTime.now());

        log.info("▼ customerMessage S. message>{},headerAccessor>{}", message,headerAccessor);
        //WebSocketSessionAttribute attr = WebSocketAttributes.getSimpSessionAttributes((StompHeaderAccessor)headerAccessor);
        //log.info("attr:{}", attr);

        // {userName=홍길철, userId=cust01, roomId=room-823880d7, companyId=apt001, userEmail=cust01@example.com, userRole=CUSTOMER}
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String _userName  = getKV(sessionAttributes, "userName" );
        String _userId    = getKV(sessionAttributes, "userId"   );
        String _roomId    = getKV(sessionAttributes, "roomId"   );
        String _companyId = getKV(sessionAttributes, "companyId");
        String _userEmail = getKV(sessionAttributes, "userEmail");
        String _userRole  = getKV(sessionAttributes, "userRole" );
/*
        public class ChatMessage {
            private String        roomId;
            private String        sender;
            private UserRole      senderRole; // CUSTOMER, AGENT, BOT, SYSTEM
            private String        message;
            private MessageType   type;
            private String        companyId;
            private LocalDateTime timestamp; // 메시지 발행 시간 (서버에서 설정)

            private String        targetTopic; // 2026.02.05 허) 발행 토픽명
*/
        // simpSessionAttributes={userName=홍길수, userId=cust02, roomId=room-cd2483d7, companyId=apt001, userEmail=cust02@example.com, userRole=CUSTOMER},
        /*switch ( message.getType()) {
            case HANDOFF: // 상담사 연결 요청
                message.setUserName  (_userName );//= getKV(sessionAttributes, "userName" );
                message.setUserId    (_userId   );//= getKV(sessionAttributes, "userId"   );
                message.setRoomId    (_roomId   );//= getKV(sessionAttributes, "roomId"   );
                message.setCompanyId (_companyId);//= getKV(sessionAttributes, "companyId");
                message.setUserEmail (_userEmail);//= getKV(sessionAttributes, "userEmail");
                message.setUserRole  (_userRole );//= getKV(sessionAttributes, "userRole" );
                message.setRoomId(_roomId);
                message.setRoomId(_roomId);
                message.setRoomId(_roomId);
                message.setRoomId(_roomId);
                message.setRoomId(_roomId);

            case CANCEL_HANDOFF: // 상담사 연결 요청 취소
                String userEmail = (String) sessionAttributes.get("userEmail");
                System.out.println(userEmail);

                break;
            default:
        } */
        /*
        02-06 09:31:42.349 [ clientInboundChannel-41] [INFO ] aicc.chat.service.ChatCustomerService           :customerMessage           :246 ▼ customerMessage S.
        message>ChatMessage
        (roomId=room-f2746d60, sender=홍길철, senderRole=null, message=상담사 연결을 요청합니다., type=HANDOFF, companyId=null, timestamp=null, targetTopic=null),
        headerAccessor>StompHeaderAccessor [headers={simpMessageType=MESSAGE, stompCommand=SEND, nativeHeaders={destination=[/app/customer/chat], content-length=[113]},
        simpSessionAttributes={userName=홍길철, userId=cust01, roomId=room-f2746d60, companyId=apt001, userEmail=cust01@example.com, userRole=CUSTOMER}, simpHeartbeat=[J@226102b9, lookupDestination=/customer/chat, simpSessionId=yefybvdd, simpDestination=/app/customer/chat}]
       */

        String userId = null;

        // 서버에서 메시지 수신 시간 설정
       // message.setTimestamp(LocalDateTime.now());

        if (sessionAttributes != null) {
            String roomId    = (String) sessionAttributes.get("roomId");
            String userName  = (String) sessionAttributes.get("userName");
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

        log.debug("▶ Customer message received for room: {} at {}", message.getRoomId(), message.getTimestamp());

        // 고객이 LEAVE 메시지를 보낸 경우 상담사에게 알림
        if (MessageType.LEAVE.equals(message.getType())) {
            log.info("🔔 고객 퇴장 메시지 감지 - roomId: {}, userId: {}", message.getRoomId(), userId);

            try {
                ChatRoom room = roomRepository.findRoomById(message.getRoomId());

                if (room != null && room.getAssignedAgent() != null) {
                    // 상담사가 배정된 경우 상담사에게 알림
                    log.info("  - assignedAgent: {}", room.getAssignedAgent());

                    ChatMessage leaveNotice = ChatMessage.builder()
                            .roomId(message.getRoomId())
                            .sender("System")
                            .senderRole(UserRole.SYSTEM)
                            .message(message.getSender() + " 고객이 상담을 종료했습니다.")
                            .type(MessageType.CUSTOMER_LEFT)
                            .build();
                    messageBroker.publish(leaveNotice);

                    log.info("✅ 고객 퇴장 알림 전송 완료!");
                } else {
                    log.info("  ℹ️ 상담사가 배정되지 않은 방 - 알림 전송 생략");
                }
            } catch (Exception e) {
                log.error("❌ 고객 퇴장 알림 전송 실패", e);
            }
        }

        // @TODO: 임시 DB 저장 막음
        // // PostgreSQL에 채팅 이력 저장
        // try {
        //     ChatHistory chatHistory = ChatHistory.builder()
        //             .roomId(message.getRoomId())
        //             .senderId(userId != null ? userId : message.getSender())
        //             .senderName(message.getSender())
        //             .senderRole(message.getSenderRole().name())
        //             .message(message.getMessage())
        //             .messageType(message.getType().name())
        //             .companyId(message.getCompanyId())
        //             .createdAt(message.getTimestamp()) // 서버 타임스탬프 사용
        //             .build();
        //     chatHistoryService.saveChatHistory(chatHistory); // DB
        //
        //     // 세션의 마지막 활동 시간 DB 업데이트
        //     chatSessionService.updateLastActivity(message.getRoomId()); // DB
        // } catch (Exception e) {
        //     log.error("Failed to save chat history to DB: roomId={}", message.getRoomId(), e);
        //     // DB 저장 실패해도 채팅은 계속 진행
        // }

        log.info("   >>> customerMessage>handleMessage S");
        routingStrategy.handleMessage(message.getRoomId(), message); // DynamicRoutingStrategy
        log.info("   <<< customerMessage>handleMessage E");
        log.info("▲ customerMessage E.");
    }

    /**
     * 고객의 마지막 채팅 정보 - 이전 연결 끊김을 이어서 하기 위함
     * @param
     * @return
     */
    public ResponseEntity<?> chatLastInfo(String bearerToken) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 1. chat:user-customer:{userId}의 정보
        String userCustomerKey = Constants.USER_CUSTOMER_KEY + ":" + userInfo.getUserId(); // chat:user-customer:{userId}
        if ( !redisTemplate.hasKey(userCustomerKey) ) {
            return ResponseEntity.ok("{}");
        }

        Map<Object, Object> map1 = redisTemplate.opsForHash().entries(userCustomerKey);
        UserCustomer userCustomer = mapper.convertValue(map1, UserCustomer.class);

        if ( UtilString.isEmpty(userCustomer.getRoomId())) {
            return ResponseEntity.ok("{}");
        }

        // 2. chat:room-info:{roomId} 정보
        String roomInfoKey = Constants.ROOM_INFO_KEY_PREFIX + userCustomer.getRoomId();
        if ( !redisTemplate.hasKey(roomInfoKey) ) {
            return ResponseEntity.ok("{}");
        }

        Map<Object, Object> map2 = redisTemplate.opsForHash().entries(roomInfoKey);
        do {
            if ( map2.containsKey("routingMode") ) {
                String routingMode = String.valueOf( map2.get("routingMode") );
                if ( !"CLOSED".equals(routingMode)) {
                    break;
                }
            }
            return ResponseEntity.ok("{}");
        } while (false );

        RoomInfo roomInfo = mapper.convertValue(map2, RoomInfo.class);

        roomInfo.getRoutingMode();
        roomInfo.getCreatorId();
        roomInfo.getName();

        userCustomer.getRoomId();

        // 새로운 맵에 합치기
        Map<Object, Object> result = new HashMap<>(map1);
        result.putAll(map2);

        return ResponseEntity.ok(result);//.build();
    }
}
