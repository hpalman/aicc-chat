package aicc.chat.controller;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.RoomRepository;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
@Slf4j
public class CustomerChatController {

    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
    private final TokenService tokenService;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;

    @PostMapping("/chatbot")
    public ResponseEntity<ChatRoom> createRoomWithBot(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("Customer request createRoomWithBot");
        if (token == null || !token.startsWith("Bearer ")) return ResponseEntity.status(401).build();
        
        String actualToken = token.substring(7);
        UserInfo custInfo = tokenService.validateToken(actualToken);
        if (custInfo == null) return ResponseEntity.status(401).build();

        String newRoomId = "room-" + UUID.randomUUID().toString().substring(0, 8);
        ChatRoom room = roomRepository.createRoom(newRoomId, custInfo.getUserId());
        roomRepository.addMember(newRoomId, custInfo.getUserId()); // 고객을 멤버로 추가
        
        routingStrategy.onRoomCreated(room);
        roomUpdateBroadcaster.broadcastRoomList();
        return ResponseEntity.ok(room);
    }

    @MessageMapping("/customer/chat")
    public void onCustomerMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String roomId = (String) sessionAttributes.get("roomId");
            String userName = (String) sessionAttributes.get("userName");
            String companyId = (String) sessionAttributes.get("companyId");
            
            // 고객 전용 로직: 세션의 roomId와 userName으로 강제 고정
            if (roomId != null) message.setRoomId(roomId);
            if (userName != null) message.setSender(userName);
            if (companyId != null) message.setCompanyId(companyId);
            message.setSenderRole(UserRole.CUSTOMER);
        }
        
        log.debug("Customer message received for room: {}", message.getRoomId());
        roomRepository.updateLastActivity(message.getRoomId());
        routingStrategy.handleMessage(message.getRoomId(), message);
    }
}

