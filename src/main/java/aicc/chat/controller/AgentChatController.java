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

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
@Slf4j
public class AgentChatController {

    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
    private final TokenService tokenService;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final aicc.chat.service.MessageBroker messageBroker;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> findAllRooms() {
        log.debug("Agent request findAllRooms");
        return ResponseEntity.ok(roomRepository.findAllRooms());
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> findRoomById(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        log.info("Agent request findRoomById: roomId={}", roomId);
        // 토큰 체크 추가 (선택사항이지만 일관성을 위해)
        if (token != null && token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            tokenService.validateToken(actualToken);
        }

        ChatRoom room = roomRepository.findRoomById(roomId);
        if (room == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{roomId}/assign")
    public ResponseEntity<?> assignAgent(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        log.info("Agent request assignAgent: roomId={}", roomId);
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            return ResponseEntity.status(403).body("상담원만 배정 가능합니다.");
        }

        boolean success = roomRepository.assignAgent(roomId, userInfo.getUserName());
        if (success) {
            ChatMessage notice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(userInfo.getUserName() + " 상담원과 연결되었습니다.")
                    .type(aicc.chat.domain.MessageType.TALK)
                    .build();
            
            try {
                messageBroker.publish(notice);
                roomUpdateBroadcaster.broadcastRoomList();
            } catch (Exception e) {
                log.error("Failed to post-assign actions", e);
            }
            
            return ResponseEntity.ok().build();
        } else {
            String currentAgent = roomRepository.getAssignedAgent(roomId);
            if (userInfo.getUserName().equals(currentAgent)) {
                log.info("Room {} already assigned to the same agent: {}", roomId, currentAgent);
                return ResponseEntity.ok().build(); // 이미 본인에게 배정된 경우 성공 처리
            }
            return ResponseEntity.status(409).body("이미 다른 상담원(" + currentAgent + ")이 배정되었습니다.");
        }
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<?> deleteRoom(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        log.info("Agent request deleteRoom: roomId={}", roomId);
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            return ResponseEntity.status(403).body("상담원만 방을 종료할 수 있습니다.");
        }

        // 상담 종료 메시지 발송
        ChatMessage notice = ChatMessage.builder()
                .roomId(roomId)
                .sender("System")
                .senderRole(UserRole.BOT)
                .message("상담원 " + userInfo.getUserName() + "에 의해 상담이 종료되었습니다.")
                .type(aicc.chat.domain.MessageType.LEAVE)
                .build();
        
        try {
            String currentMode = roomRepository.getRoutingMode(roomId);
            
            // 이미 종료된 상태에서 한 번 더 요청하면 실제 삭제 수행
            if ("CLOSED".equals(currentMode)) {
                log.info("Permanently deleting closed room: {}", roomId);
                roomRepository.deleteRoom(roomId);
            } else {
                messageBroker.publish(notice);
                // 방 상태를 CLOSED로 변경
                roomRepository.setRoutingMode(roomId, "CLOSED");
            }
            
            roomUpdateBroadcaster.broadcastRoomList();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to close room", e);
            return ResponseEntity.status(500).build();
        }
    }

    @MessageMapping("/agent/chat")
    public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            String userName = (String) sessionAttributes.get("userName");
            String companyId = (String) sessionAttributes.get("companyId");
            
            // 상담원 전용 로직: 클라이언트가 보낸 roomId 유지 (여러 방 관리 가능)
            // 이름과 역할만 세션 정보로 강제
            message.setSenderRole(UserRole.AGENT);
            if (userName != null) message.setSender(userName);
            if (companyId != null) message.setCompanyId(companyId);
        }
        
        log.debug("Agent message received for room: {}", message.getRoomId());
        roomRepository.updateLastActivity(message.getRoomId());
        routingStrategy.handleMessage(message.getRoomId(), message);
    }
}

