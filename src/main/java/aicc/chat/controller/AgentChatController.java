package aicc.chat.controller;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.service.ChatHistoryService;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.ChatSessionService;
import aicc.chat.service.RoomRepository;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final ChatSessionService chatSessionService;
    private final ChatHistoryService chatHistoryService;

    @GetMapping("/rooms")
    // 상담원에게 전체 상담방 목록을 반환
    public ResponseEntity<List<ChatRoom>> findAllRooms() {
        log.debug("Agent request findAllRooms");
        return ResponseEntity.ok(roomRepository.findAllRooms());
    }

    @GetMapping("/rooms/{roomId}")
    // 특정 상담방 상세 정보를 조회
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
    // 상담원을 방에 배정하고 상태/이력을 갱신
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
            LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
            
            ChatMessage notice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(userInfo.getUserName() + " 상담원과 연결되었습니다.")
                    .type(aicc.chat.domain.MessageType.TALK)
                    .timestamp(now) // 서버 타임스탬프 설정
                    .build();
            
            try {
                messageBroker.publish(notice);
                roomUpdateBroadcaster.broadcastRoomList();
                
                // PostgreSQL에 상담원 배정 정보 저장
                chatSessionService.updateSessionStatus(roomId, "AGENT");
                chatSessionService.assignAgent(roomId, userInfo.getUserName());
                
                // 시스템 메시지도 이력에 저장
                ChatHistory chatHistory = ChatHistory.builder()
                        .roomId(roomId)
                        .senderId("SYSTEM")
                        .senderName("System")
                        .senderRole("SYSTEM")
                        .message(notice.getMessage())
                        .messageType("TALK")
                        .createdAt(now) // 서버 타임스탬프 사용
                        .build();
                chatHistoryService.saveChatHistory(chatHistory);
                
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
    // 상담 종료 또는 종료 방 삭제 처리
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

        LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
        
        try {
            String currentMode = roomRepository.getRoutingMode(roomId);
            
            // 이미 종료된 상태에서 한 번 더 요청하면 실제 삭제 수행
            if ("CLOSED".equals(currentMode)) {
                log.info("Permanently deleting closed room: {}", roomId);
                roomRepository.deleteRoom(roomId);
            } else {
                // 상담원이 상담 종료 시 BOT 모드로 복귀 (CLOSED가 아닌 BOT으로 변경)
                log.info("Agent ending consultation, switching room {} back to BOT mode", roomId);
                
                // 상담 종료 알림 메시지 발송
                ChatMessage notice = ChatMessage.builder()
                        .roomId(roomId)
                        .sender("System")
                        .senderRole(UserRole.BOT)
                        .message("상담원과의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다.")
                        .type(aicc.chat.domain.MessageType.TALK)
                        .timestamp(now) // 서버 타임스탬프 설정
                        .build();
                
                messageBroker.publish(notice);
                
                // 방 상태를 BOT으로 변경 (고객이 다시 봇과 대화 가능)
                roomRepository.setRoutingMode(roomId, "BOT");
                
                // 상담원 배정 해제 (assignedAgent 키 삭제)
                roomRepository.setAssignedAgent(roomId, null); // null로 설정하여 키 삭제
                
                // PostgreSQL에 상태 업데이트
                chatSessionService.updateSessionStatus(roomId, "BOT");
                
                // 종료 메시지도 이력에 저장
                ChatHistory chatHistory = ChatHistory.builder()
                        .roomId(roomId)
                        .senderId("SYSTEM")
                        .senderName("System")
                        .senderRole("SYSTEM")
                        .message(notice.getMessage())
                        .messageType("TALK")
                        .createdAt(now) // 서버 타임스탬프 사용
                        .build();
                chatHistoryService.saveChatHistory(chatHistory);
            }
            
            roomUpdateBroadcaster.broadcastRoomList();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to close room", e);
            return ResponseEntity.status(500).build();
        }
    }

    @MessageMapping("/agent/chat")
    // 상담원 채팅 메시지를 받아 이력 저장 후 라우팅
    public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String userId = null;
        
        // 서버에서 메시지 수신 시간 설정
        message.setTimestamp(LocalDateTime.now());
        
        if (sessionAttributes != null) {
            String userName = (String) sessionAttributes.get("userName");
            String companyId = (String) sessionAttributes.get("companyId");
            userId = (String) sessionAttributes.get("userId");
            
            // 상담원 전용 로직: 클라이언트가 보낸 roomId 유지 (여러 방 관리 가능)
            // 이름과 역할만 세션 정보로 강제
            message.setSenderRole(UserRole.AGENT);
            if (userName != null) message.setSender(userName);
            if (companyId != null) message.setCompanyId(companyId);
        }
        
        log.debug("Agent message received for room: {} at {}", message.getRoomId(), message.getTimestamp());
        
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
            chatHistoryService.saveChatHistory(chatHistory);
            
            // 세션의 마지막 활동 시간 업데이트
            chatSessionService.updateLastActivity(message.getRoomId());
        } catch (Exception e) {
            log.error("Failed to save chat history to DB: roomId={}", message.getRoomId(), e);
            // DB 저장 실패해도 채팅은 계속 진행
        }
        
        roomRepository.updateLastActivity(message.getRoomId());
        routingStrategy.handleMessage(message.getRoomId(), message);
    }
}

