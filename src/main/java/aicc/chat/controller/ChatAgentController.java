package aicc.chat.controller;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.service.AgentAuthService;
import aicc.chat.service.TokenService;
import aicc.chat.service.inteface.ChatHistoryService;
import aicc.chat.service.inteface.ChatRoutingStrategy;
import aicc.chat.service.inteface.ChatSessionService;
import aicc.chat.service.inteface.RoomRepository;
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
public class ChatAgentController {
    private final AgentAuthService agentAuthService;

    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
    private final TokenService tokenService;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final aicc.chat.service.inteface.MessageBroker messageBroker;
    private final ChatSessionService chatSessionService;
    private final ChatHistoryService chatHistoryService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    // 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환
    public ResponseEntity<UserInfo> login(
            @RequestParam String id,
            @RequestParam String password) {
        log.info("▼ login S. 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 시작./api/agent > /login S");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = agentAuthService.login(id, password);
        if (userInfo == null) {
            log.warn("▶ userInfo == null");
            ret = ResponseEntity.status(401).build();
        } else {
            if ( userInfo.getStatus() == -1  ) {
                ret = ResponseEntity.status(409).build(); // 충돌 > 현재 상태와 요청이 충돌 > 이미 로그인된 상태에서 다시 로그인 시도
            } else {
                ret = ResponseEntity.ok(userInfo);
            }
        }
        log.info("▲ login E. 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 완료./api/agent > /login E");
        return ret;
    }

    @GetMapping("/me")
    // Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환
    public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ getCurrentAgent E. /api/agent > /me S. Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 시작.");
        //ResponseEntity<UserInfo> ret;
        if (token == null || !token.startsWith("Bearer ")) {
            log.info("◀ token == null || !token.startsWith(\"Bearer \")). Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료 ");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null) {
            log.info("▶ userInfo == null. Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료 ");
            return ResponseEntity.status(401).build();
        }

        // 하트비트 - 온라인 상태 유지. 다른 방식으로 처리할 필요가 있음. 여기서는 토큰만 확인하는 것으로 일을 해야 함
        // agentAuthService.heartbeat(userInfo.getUserId());

        log.info("▲ getCurrentAgent E. /api/agent > /me E. Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료.");
        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/logout")
    // 상담원 로그아웃 처리 및 고객에게 알림
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ 상담원 로그아웃 처리:logout 시작./api/agent > /logout S");

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

        // Redis에서 온라인 상담원 제거 (Hash 구조 전체 삭제)
        String agentKey = Constants.ONLINE_AGENTS_KEY + ":" + userInfo.getUserId();
        redisTemplate.delete(agentKey);
        log.info("Agent {} ({}) removed from online list in Redis", userInfo.getUserId(), userInfo.getUserName());

        // 상담원 로그아웃 알림 브로드캐스트
        ChatMessage logoutMessage = ChatMessage.builder()
            .roomId("SYSTEM_BROADCAST")
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message("AGENT_UNAVAILABLE")
            .type(aicc.chat.domain.MessageType.SYSTEM)
            .timestamp(java.time.LocalDateTime.now())
            .build();
        messageBroker.publish(logoutMessage);

        log.info("▲ 상담원 로그아웃 처리:logout 완료./api/agent > /logout E");
        return ResponseEntity.ok().build();
    }


    @GetMapping("/rooms")
    // 상담원에게 전체 상담방 목록을 반환
    public ResponseEntity<List<ChatRoom>> findAllRooms() {
        log.info("▼ Agent request findAllRooms./api/agent > /rooms S");
        ResponseEntity<List<ChatRoom>> ret
             = ResponseEntity.ok(roomRepository.findAllRooms());
        log.info("▲ Agent request findAllRooms./api/agent > /rooms E");
        return ret;
    }

    @GetMapping("/availability")
    // 상담원 가용성 확인: 로그인한 상담원이 있고 3개 미만의 상담을 하고 있는지 확인
    public ResponseEntity<Map<String, Object>> checkAgentAvailability() {
        log.info("▼ checkAgentAvailability S. /api/agent > /availability S");

        // 1. 온라인 상담원 목록 조회 (Hash 구조)
        java.util.Set<String> onlineAgentKeys = redisTemplate.keys(Constants.ONLINE_AGENTS_KEY + ":*");
        java.util.Map<String, String> onlineAgents = new java.util.HashMap<>(); // agentId -> userName

        if (onlineAgentKeys != null) {
            for (String key : onlineAgentKeys) {
                String agentId = key.substring((Constants.ONLINE_AGENTS_KEY + ":").length());

                // Hash에서 userName 조회
                Object userNameObj = redisTemplate.opsForHash().get(key, "userName");
                if (userNameObj != null) {
                    String userName = userNameObj.toString();
                    onlineAgents.put(agentId, userName);
                }
            }
        }

        log.info("Online agents: {} (count: {})", onlineAgents, onlineAgents.size());

        // 온라인 상담원이 없으면 즉시 불가 반환
        if (onlineAgents.isEmpty()) {
            log.info("▲ checkAgentAvailability E. No online agents available");
            return ResponseEntity.ok(Map.of(
                "available", false,
                "onlineAgentCount", 0,
                "agentCount", 0,
                "agentRoomCount", java.util.Collections.emptyMap()
            ));
        }

        // 2. 상담원이 배정된 방 개수 세기
        List<ChatRoom> allRooms = roomRepository.findAllRooms();
        java.util.Map<String, Long> agentRoomCount = allRooms.stream()
            .filter(room -> "AGENT".equals(room.getStatus()) && room.getAssignedAgent() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                ChatRoom::getAssignedAgent,
                java.util.stream.Collectors.counting()
            ));

        // 3. 온라인 상담원 중 3개 미만의 상담을 하고 있는 상담원이 있는지 확인
        boolean hasAvailableAgent = onlineAgents.values().stream()
            .anyMatch(agentName -> {
                // 현재 상담 개수 확인
                long currentChats = agentRoomCount.getOrDefault(agentName, 0L);
                boolean available = currentChats < 3;
                log.info("▲ checkAgentAvailability E. Agent {} - current chats: {}, available: {}", agentName, currentChats, available);
                return available;
            });

        log.info("Agent availability check - Online: {}, Available: {}, Room count: {}",
                 onlineAgents.size(), hasAvailableAgent, agentRoomCount);

        log.info("▲ checkAgentAvailability E. /api/agent > /availability E");
        return ResponseEntity.ok(Map.of(
            "available", hasAvailableAgent,
            "onlineAgentCount", onlineAgents.size(),
            "agentCount", agentRoomCount.size(),
            "agentRoomCount", agentRoomCount
        ));
    }

    /* 특정 상담방 상세 정보를 조회
    $ curl -X GET http://localhost:28070/api/agent/rooms/room-dd7e66c
    {"roomId":"room-dd7e66c","roomName":"room-dd7e66c","members":[],"status":"BOT","assignedAgent":null,"createdAt":0,"lastActivityAt":0,"custId":null}
    */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> findRoomById(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {

        log.info("▼ findRoomById S. Agent request findRoomById: roomId={}", roomId);
        // 토큰 체크 추가 (선택사항이지만 일관성을 위해)
        if (token != null && token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            tokenService.validateToken(actualToken);
        }

        ChatRoom room = roomRepository.findRoomById(roomId);

        log.info("▲ findRoomById E. room:{}", room);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(room);
    }

    // 상담원을 방에 배정하고 상태/이력을 갱신
    private ResponseEntity<?> _assignAgent(
            String roomId,
            String token,
            boolean force) {
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("token == null || !token.startsWith(\"Bearer\")) ");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            log.warn("(userInfo == null || userInfo.getRole() != UserRole.AGENT");
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
                chatSessionService.updateSessionStatus(roomId, "AGENT"); // DB
                chatSessionService.assignAgent(roomId, userInfo.getUserName()); // DB

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
                chatHistoryService.saveChatHistory(chatHistory); // DB

            } catch (Exception e) {
                log.error("Failed to post-assign actions", e);
            }
            log.warn("ResponseEntity.ok().build()");
            return ResponseEntity.ok().build();
        } else {
            String currentAgent = roomRepository.getAssignedAgent(roomId);
            if (userInfo.getUserName().equals(currentAgent)) {
                log.info("Room {} already assigned to the same agent: {}", roomId, currentAgent);
                return ResponseEntity.ok().build(); // 이미 본인에게 배정된 경우 성공 처리
            }
            if (force) {
                log.info("Force assigning agent {} to room {} (current: {})", userInfo.getUserName(), roomId, currentAgent);
                // 강제 배정: 기존 배정 상담원 교체
                roomRepository.setAssignedAgent(roomId, userInfo.getUserName());
                roomRepository.setRoutingMode(roomId, "AGENT");
                roomRepository.updateLastActivity(roomId);

                LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
                ChatMessage notice = ChatMessage.builder()
                        .roomId(roomId)
                        .sender("System")
                        .senderRole(UserRole.SYSTEM)
                        .message(userInfo.getUserName() + " 상담원이 상담에 개입했습니다.")
                        .type(aicc.chat.domain.MessageType.INTERVENE)
                        .timestamp(now)
                        .build();

                try {
                    messageBroker.publish(notice);
                    roomUpdateBroadcaster.broadcastRoomList();

                    chatSessionService.updateSessionStatus(roomId, "AGENT"); // DB
                    chatSessionService.assignAgent(roomId, userInfo.getUserName()); // DB

                    ChatHistory chatHistory = ChatHistory.builder()
                            .roomId(roomId)
                            .senderId("SYSTEM")
                            .senderName("System")
                            .senderRole("SYSTEM")
                            .message(notice.getMessage())
                            .messageType("INTERVENE")
                            .createdAt(now)
                            .build();
                    chatHistoryService.saveChatHistory(chatHistory); // DB
                } catch (Exception e) {
                    log.error("Failed to post-force-assign actions", e);
                }

                return ResponseEntity.ok().build();
            }

            return ResponseEntity.status(409).body("이미 다른 상담원(" + currentAgent + ")이 배정되었습니다.");
        }
    }

    @PostMapping("/rooms/{roomId}/assign")
    // 상담원을 방에 배정하고 상태/이력을 갱신
    public ResponseEntity<?> assignAgent(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {

        log.info("▼ assignAgent S. roomId={}", roomId);
        ResponseEntity<?> ret = _assignAgent(roomId, token, force );
        log.info("▲ assignAgent E.");
        return ret;
    }

    @DeleteMapping("/rooms/{roomId}")
    // 상담 종료 또는 종료 방 삭제 처리
    public ResponseEntity<?> deleteRoom(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ deleteRoom S. Agent request deleteRoom: roomId={}", roomId);
    	ResponseEntity<?> ret = _deleteRoom(roomId, token);
        log.info("▲ deleteRoom E. ret:{}", ret);
    	return ret;
    }
    public ResponseEntity<?> _deleteRoom(String roomId, String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("token == null || !token.startsWith(\"Bearer \"))");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            log.warn("상담원만 방을 종료할 수 있습니다.");
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
                // 상담원 멤버 정보 제거 (Redis 멤버 목록 정리)
                roomRepository.removeMember(roomId, userInfo.getUserId());

                // PostgreSQL에 상태 업데이트
                chatSessionService.updateSessionStatus(roomId, "BOT"); // DB

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
                chatHistoryService.saveChatHistory(chatHistory); // DB
            }

            roomUpdateBroadcaster.broadcastRoomList();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("▲ Failed to close room", e);
            return ResponseEntity.status(500).build();
        }
    }

    @MessageMapping("/agent/chat")
    // 상담원 채팅 메시지를 받아 이력 저장 후 라우팅
    public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
// 방이 있는지 체크 필요

        log.info("▶ onAgentMessage S");
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
            if (userName != null) {
                message.setSender(userName);
            }
            if (companyId != null) {
                message.setCompanyId(companyId);
            }
        }

        String roomId = message.getRoomId();
        LocalDateTime localDateTime = message.getTimestamp();
        log.debug("Agent message received for room: {} at {}", roomId, localDateTime);


        ChatRoom chatRoom = roomRepository.findRoomById(roomId);
        if ( "CLOSED".equals(chatRoom.getStatus()) ) {
        //if ( !roomRepository.existRoomsMember(roomId) ) {
            log.warn("ㅁㅁㅁㅁㅁㅁㅁㅁ 방이 닫혔어요! ㅁㅁㅁㅁㅁㅁㅁㅁㅁ ");
            return;
        }

        // PostgreSQL에 채팅 이력 저장
        try {
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(roomId)
                    .senderId(userId != null ? userId : message.getSender())
                    .senderName(message.getSender())
                    .senderRole(message.getSenderRole().name())
                    .message(message.getMessage())
                    .messageType(message.getType().name())
                    .companyId(message.getCompanyId())
                    .createdAt(localDateTime) // 서버 타임스탬프 사용
                    .build();
            chatHistoryService.saveChatHistory(chatHistory); // DB

            // 세션의 마지막 활동 시간 업데이트
            chatSessionService.updateLastActivity(roomId); // 마지막 활동 시간 갱신 - DB
        } catch (Exception e) {
            log.error("Failed to save chat history to DB: roomId={}", roomId, e);
            // DB 저장 실패해도 채팅은 계속 진행
        }

        roomRepository.updateLastActivity(roomId); // REDIS VALUE
        routingStrategy.handleMessage(roomId, message);
        log.info("▲ onAgentMessage E.");
    }
}

