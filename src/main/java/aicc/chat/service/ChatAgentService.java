package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.mapper.UserAccountMapper;
import aicc.chat.service.inteface.ChatHistoryService;
import aicc.chat.service.inteface.ChatRoutingStrategy;
import aicc.chat.service.inteface.ChatSessionService;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import aicc.chat.util.UtilString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentService extends ChatService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService tokenService;
//    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final MessageBroker messageBroker;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;

    private final RoomRepository roomRepository;


//    private final AgentAuthService agentAuthService;

//    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
//    private final TokenService tokenService;
//    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
//    private final aicc.chat.service.inteface.MessageBroker messageBroker;
    private final ChatSessionService chatSessionService;
    private final ChatHistoryService chatHistoryService;
//    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final AgentAuthService agentAuthService;

    /**
     * 상담사 가용성 확인: agentStatus가 WAITING인 상담사만 조회하여 가용성 확인
     * @return
     */
    public Map<String, Object> checkAgentAvailability() {
        // 1. Redis의 chat:user-agent:{agentId} 키들을 조회하여 agentStatus가 WAITING인 상담사만 필터링
        Set<String> onlineAgentKeys = redisTemplate.keys(Constants.USER_AGENT_KEY + ":*"); // "chat:user-agent:*"
        Map<String, String> waitingAgents = new java.util.HashMap<>(); // agentId -> userName

int agentCount = onlineAgentKeys.size(); // 전체 Agent 수
int waitingAgentCount = 0; // WAITING Agent 수

        if (onlineAgentKeys != null) {
            for (String key : onlineAgentKeys) {
                String agentId = key.substring((Constants.USER_AGENT_KEY + ":").length());

                // Hash에서 agentStatus 조회
                Object agentStatusObj = redisTemplate.opsForHash().get(key, "agentStatus");
                String agentStatus = agentStatusObj != null ? agentStatusObj.toString() : null;

                // agentStatus가 WAITING인 경우만 추가
                if ("WAITING".equals(agentStatus)) {
                    waitingAgentCount++;
                    // // Hash에서 userName 조회
                    // Object userNameObj = redisTemplate.opsForHash().get(key, "userName");
                    // if (userNameObj != null) {
                    //     String userName = userNameObj.toString();
                    //     waitingAgents.put(agentId, userName);
                    //     log.debug("▶ WAITING agent found: agentId={}, userName={}", agentId, userName);
                    // }
                }
            }
        }
Boolean hasAvailableAgent = waitingAgentCount > 0 ? true : false; // hasAvailableAgent,; // 상담사연결 신청 가능 여부

        return Map.of(
            "available"        , hasAvailableAgent,
            //"onlineAgentCount" , waitingAgents.size(),
            "agentCount"       , agentCount, //agentRoomCount.size(),
            //"agentRoomCount"   , agentRoomCount,
            "waitingAgentCount", waitingAgentCount //waitingAgents.size()
        );

        //log.info("▶ WAITING agents: {} (count: {})", waitingAgents, waitingAgents.size());
        //
        //// WAITING 상태인 상담사가 없으면 즉시 불가 반환
        //if (waitingAgents.isEmpty()) {
        //    log.info("▶ checkAgentAvailability E. No WAITING agents available");
        //    return Map.of(
        //        "available"       , false,
        //        "onlineAgentCount", 0,
        //        "agentCount"      , 0,
        //        "agentRoomCount"  , java.util.Collections.emptyMap(),
        //        "waitingAgentCount", 0
        //    );
        //}
        //
        //// 2. 상담사가 배정된 방 개수 세기
        //List<ChatRoom> allRooms = roomRepository.findAllRooms();
        //Map<String, Long> agentRoomCount = allRooms.stream()
        //    .filter(room -> "AGENT".equals(room.getStatus()) && room.getAssignedAgent() != null)
        //    .collect(java.util.stream.Collectors.groupingBy(
        //        ChatRoom::getAssignedAgent,
        //        java.util.stream.Collectors.counting()
        //    ));
        //
        //// 3. WAITING 상태인 상담사 중 3개 미만의 상담을 하고 있는 상담사가 있는지 확인
        //boolean hasAvailableAgent = waitingAgents.values().stream()
        //    .anyMatch(agentName -> {
        //        // 현재 상담 개수 확인
        //        long currentChats = agentRoomCount.getOrDefault(agentName, 0L);
        //        boolean available = currentChats < 3;
        //        log.info("▶ checkAgentAvailability E. WAITING Agent {} - current chats: {}, available: {}", agentName, currentChats, available);
        //        return available;
        //    });
        //
        //log.info("▶ Agent availability check - WAITING agents: {}, Available: {}, Room count: {}",
        //         waitingAgents.size(), hasAvailableAgent, agentRoomCount);
        //
        //log.info("▶ checkAgentAvailability E.");
        //return Map.of(
        //    "available"       , hasAvailableAgent,
        //    "onlineAgentCount", waitingAgents.size(),
        //    "agentCount"      , agentRoomCount.size(),
        //    "agentRoomCount"  , agentRoomCount,
        //    "waitingAgentCount", waitingAgents.size()
        //);
    }


    /**
     * 상담사 로그아웃 처리
     * 고객에게 알림
     * @param bearerToken
     * @return
     */
    public HttpStatus logout(String bearerToken) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return HttpStatus.UNAUTHORIZED;
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null) {
            return HttpStatus.UNAUTHORIZED;
        }

        // Redis에서 온라인 상담사 제거 (Hash 구조 전체 삭제)
        String agentKey = Constants.USER_AGENT_KEY + ":" + userInfo.getUserId(); // chat:user-agent:{userId}
        redisTemplate.delete(agentKey);
        log.info("▶ Agent {} ({}) removed from online list in Redis", userInfo.getUserId(), userInfo.getUserName());

        // 상담사 로그아웃 알림 브로드캐스트
        ChatMessage logoutMessage = ChatMessage.builder()
            .roomId("SYSTEM_BROADCAST")
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message("AGENT_STATUS") // AGENT_UNAVAILABLE
            .type(MessageType.SYSTEM)
            .build();
        messageBroker.publish(logoutMessage);

        return HttpStatus.OK;
    }

    /**
     *
     * @param agentId
     * @param stat
     */
    public Boolean setAgentStatus(String agentId, String stat) {
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId; // chat:user-agents:{agentId}
        boolean eq = false;

        Boolean exists = redisTemplate.hasKey(agentKey);
        if ( !exists ) { // 미존재
            return false;
        }

        Object o = redisTemplate.opsForHash().get(agentKey, "agentStatus"); // Hash 구조에서 agentStatus 필드 조회
        if ( o != null ) {
            String _stat = o.toString();
            if ( _stat.equals(stat)) {
                eq = true;
            }
        }

        if ( !eq ) {
            redisTemplate.opsForHash().put(agentKey, "agentStatus", stat); // Hash 구조에서 agentStatus 필드 업데이트

            // @TODO 알림발송
            // 상담사 로그아웃 알림 브로드캐스트
            ChatMessage logoutMessage = ChatMessage.builder()
                .roomId("SYSTEM_BROADCAST")
                .sender("System")
                .senderRole(UserRole.SYSTEM)
                .message("AGENT_STATUS") // AGENT_UNAVAILABLE
                .type(MessageType.SYSTEM)
                .build();
            messageBroker.publish(logoutMessage);
        }
        return true;
    }

    /**
     * 상담사 상태 조회
     * @param agentId
     * @return
     */
    public Map<?,?> getAgentStatus(String agentId) {
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId; // chat:user-agents:{agentId}

        Boolean exists = redisTemplate.hasKey(agentKey);
        if ( !exists ) { // 미존재
            return Map.of("code", -1, "agentId", agentId, "status","ERROR");
        }
        if ( !redisTemplate.opsForHash().hasKey(agentKey, "agentStatus") ) { // Hash 구조에서 agentStatus 필드 조회
            return Map.of("code", 0, "agentId", agentId, "status","NONE");
        }

        String _stat = "";
        Object o = redisTemplate.opsForHash().get(agentKey, "agentStatus"); // Hash 구조에서 agentStatus 필드 조회
        if ( o != null ) {
            _stat = o.toString();
        }
        return Map.of("code", 0, "agentId", agentId, "status",_stat);
    }

    /** 상담사 메시지 수신후 처리 서비스
     *
     * @param message
     * @param headerAccessor
     */
    public void agentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        log.info("▼ agentMessage S. message>{},headerAccessor>{}", message,headerAccessor);

        // ChatMessage(
        //    roomId=room-e7cb51e4, sender=상담사-01, senderRole=AGENT, message=d,
        //    type=TALK, companyId=null, timestamp=2026-02-06T13:25:22.559366900, targetTopic=null)
        //
        // simpSessionAttributes={
        //    companyId=apt001, userEmail=agent01@aicc.com, userName=상담사-01,
        //    userRole=AGENT, userId=agent01}, simpHeartbeat=[J@15e26d11,
        //    lookupDestination=/agent/chat, simpSessionId=faftlzig, simpDestination=/app/agent/chat}

        // 상담사 정보
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String _userName  = getKV(sessionAttributes, "userName" );
        String _userId    = getKV(sessionAttributes, "userId"   );
     // String _roomId    = getKV(sessionAttributes, "roomId"   );
        String _companyId = getKV(sessionAttributes, "companyId");
     // String _userEmail = getKV(sessionAttributes, "userEmail");
        String _userRole  = getKV(sessionAttributes, "userRole" );
message.setSenderId   (_userId  );
message.setSender     (_userName);
message.setSenderRole (UserRole.valueOf(_userRole) );
message.setCompanyId  (_companyId);

        // String userId = null;
        //
        // // 서버에서 메시지 수신 시간 설정
        // // message.setTimestamp(LocalDateTime.now());
        // if (sessionAttributes != null) {
        //     String userName = (String) sessionAttributes.get("userName");
        //     String companyId = (String) sessionAttributes.get("companyId");
        //     userId = (String) sessionAttributes.get("userId");
        //
        //     // 상담사 전용 로직: 클라이언트가 보낸 roomId 유지 (여러 방 관리 가능)
        //     // 이름과 역할만 세션 정보로 강제
        //     message.setSenderRole(UserRole.AGENT);
        //     if (userName != null) {
        //         message.setSender(userName);
        //     }
        //     if (companyId != null) {
        //         message.setCompanyId(companyId);
        //     }
        // }

        //String roomId = message.getRoomId();
        ////LocalDateTime localDateTime = message.getTimestamp();
        ////log.debug("Agent message received for room: {} at {}", roomId, localDateTime);
        //
        //
        //ChatRoom chatRoom = roomRepository.findRoomById(roomId);
        //if ( "CLOSED".equals(chatRoom.getStatus()) ) {
        ////if ( !roomRepository.existRoomsMember(roomId) ) {
        //    log.warn("ㅁㅁㅁㅁㅁㅁㅁㅁ 방이 닫혔어요! ㅁㅁㅁㅁㅁㅁㅁㅁㅁ ");
        //    return;
        //}
        // // @TODO: DB저장 임시 막음
        // // PostgreSQL에 채팅 이력 저장
        // try {
        //     ChatHistory chatHistory = ChatHistory.builder()
        //             .roomId(roomId)
        //             .senderId(userId != null ? userId : message.getSender())
        //             .senderName(message.getSender())
        //             .senderRole(message.getSenderRole().name())
        //             .message(message.getMessage())
        //             .messageType(message.getType().name())
        //             .companyId(message.getCompanyId())
        //             .createdAt(localDateTime) // 서버 타임스탬프 사용
        //             .build();
        //     chatHistoryService.saveChatHistory(chatHistory); // DB
        //
        //     // 세션의 마지막 활동 시간 업데이트
        //     chatSessionService.updateLastActivity(roomId); // 마지막 활동 시간 갱신 - DB
        // } catch (Exception e) {
        //     log.error("Failed to save chat history to DB: roomId={}", roomId, e);
        //     // DB 저장 실패해도 채팅은 계속 진행
        // }

        //roomRepository.updateLastActivity(roomId); // REDIS VALUE
        routingStrategy.handleMessage(message.getRoomId(), message); // DynamicRoutingStrategy
        log.info("▲ agentMessage E.");
    }

    /**
     * 상담사가 고객채팅방에 개입하고자 할 때 상담사에게 고객방에 할당
     * @param roomId
     * @param bearerToken
     * @param force
     * @return
     */
    public ResponseEntity<?> assignAgent(
            String roomId,
            String bearerToken,
            boolean force) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다.");
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            return ResponseEntity.status(403).body("상담사만 배정 가능합니다.");
        }

        // assignAgent할당, routingMode:AGENT 설정, lastActivity 설정
        boolean success = roomRepository.assignAgent(roomId, userInfo.getUserId());
        if (success) {
            return ResponseEntity.ok("{}");
            //// ChatMessage notice = ChatMessage.builder()
            ////         .roomId(roomId)
            ////         .sender("System")
            ////         .senderRole(UserRole.SYSTEM)
            ////         .message(userInfo.getUserName() + " 상담사와 연결되었습니다.")
            ////         .type(MessageType.TALK)
            ////         .build();
            //// messageBroker.publish(notice);
            //// 
            //// roomUpdateBroadcaster.broadcastRoomList(); // "/topic/rooms" 발행. @TODO: publish로 변경 필요함
            //// 
            //// try {
            ////     // @TODO : DB저장 임시 막음
            ////     // // PostgreSQL에 상담사 배정 정보 저장
            ////     // chatSessionService.updateSessionStatus(roomId, "AGENT"); // DB
            ////     // chatSessionService.assignAgent(roomId, userInfo.getUserName()); // DB
            ////     //
            ////     // // 시스템 메시지도 이력에 저장
            ////     // ChatHistory chatHistory = ChatHistory.builder()
            ////     //         .roomId(roomId)
            ////     //         .senderId("SYSTEM")
            ////     //         .senderName("System")
            ////     //         .senderRole("SYSTEM")
            ////     //         .message(notice.getMessage())
            ////     //         .messageType("TALK")
            ////     //         .createdAt(now) // 서버 타임스탬프 사용
            ////     //         .build();
            ////     // chatHistoryService.saveChatHistory(chatHistory); // DB
            //// 
            //// } catch (Exception e) {
            ////     log.error("Failed to post-assign actions", e);
            //// }
            //// log.warn("ResponseEntity.ok().build()");
            //// return ResponseEntity.ok().build();
        }

        
        else {
            String currentAgent = roomRepository.getAssignedAgent(roomId);
            if (userInfo.getUserName().equals(currentAgent)) {
                log.info("Room {} already assigned to the same agent: {}", roomId, currentAgent);
                return ResponseEntity.ok().build(); // 이미 본인에게 배정된 경우 성공 처리
            }
            if (force) {
                log.info("Force assigning agent {} to room {} (current: {})", userInfo.getUserName(), roomId, currentAgent);
                // 강제 배정: 기존 배정 상담사 교체
                roomRepository.setAssignedAgent(roomId, userInfo.getUserName());
                roomRepository.setRoutingMode(roomId, "AGENT");
                roomRepository.updateLastActivity(roomId);


                //try {
                //    ChatMessage notice = ChatMessage.builder()
                //            .roomId(roomId)
                //            .sender("System")
                //            .senderRole(UserRole.SYSTEM)
                //            .message(userInfo.getUserName() + " 상담사가 상담에 개입했습니다.")
                //            .type(MessageType.INTERVENE)
                //            .build();
                //    messageBroker.publish(notice);
                //
                //    roomUpdateBroadcaster.broadcastRoomList();
                //
                //    // @TODO : DB저장 임시 막음
                //    // chatSessionService.updateSessionStatus(roomId, "AGENT"); // DB
                //    // chatSessionService.assignAgent(roomId, userInfo.getUserName()); // DB
                //    // ChatHistory chatHistory = ChatHistory.builder()
                //    //         .roomId(roomId)
                //    //         .senderId("SYSTEM")
                //    //         .senderName("System")
                //    //         .senderRole("SYSTEM")
                //    //         .message(notice.getMessage())
                //    //         .messageType("INTERVENE")
                //    //         .createdAt(now)
                //    //         .build();
                //    // chatHistoryService.saveChatHistory(chatHistory); // DB
                //} catch (Exception e) {
                //    log.error("Failed to post-force-assign actions", e);
                //}

                return ResponseEntity.ok("{}");
            }

            return ResponseEntity.status(409).body("이미 다른 상담사(" + currentAgent + ")이 배정되었습니다.");
        }
    }
    
    /**
     * 상담사가 고객채팅방에 개입하고자 할 때 상담사에게 고객방에 할당
     * @param roomId
     * @param bearerToken
     * @param force
     * @return
     */
    public ResponseEntity<?> assignNotify(
            String roomId,
            String bearerToken) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다.");
        }
        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            return ResponseEntity.status(403).body("상담사만 배정 가능합니다.");
        }

        // assignAgent할당, routingMode:AGENT 설정, lastActivity 설정
        // boolean success = roomRepository.assignAgent(roomId, userInfo.getUserId());
            ChatMessage notice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(userInfo.getUserName() + " 상담사와 연결되었습니다.")
                    .type(MessageType.TALK)
                    .build();
            messageBroker.publish(notice);
            roomUpdateBroadcaster.broadcastRoomList(); // "/topic/rooms" 발행. @TODO: publish로 변경 필요함

        try {
            // @TODO : DB저장 임시 막음
            // // PostgreSQL에 상담사 배정 정보 저장
            // chatSessionService.updateSessionStatus(roomId, "AGENT"); // DB
            // chatSessionService.assignAgent(roomId, userInfo.getUserName()); // DB
            //
            // // 시스템 메시지도 이력에 저장
            // ChatHistory chatHistory = ChatHistory.builder()
            //         .roomId(roomId)
            //         .senderId("SYSTEM")
            //         .senderName("System")
            //         .senderRole("SYSTEM")
            //         .message(notice.getMessage())
            //         .messageType("TALK")
            //         .createdAt(now) // 서버 타임스탬프 사용
            //         .build();
            // chatHistoryService.saveChatHistory(chatHistory); // DB

        } catch (Exception e) {
            log.error("Failed to post-assign actions", e);
        }
        log.warn("ResponseEntity.ok().build()");
        return ResponseEntity.ok("{}");
    }


    public ResponseEntity<?> deleteRoom(String roomId, String bearerToken) {
        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).build();
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null || userInfo.getRole() != UserRole.AGENT) {
            log.warn("상담사만 방을 종료할 수 있습니다.");
            return ResponseEntity.status(403).body("상담사만 방을 종료할 수 있습니다.");
        }

        try {
            String currentMode = roomRepository.getRoutingMode(roomId);

            // 이미 종료된 상태에서 한 번 더 요청하면 실제 삭제 수행
            if ("CLOSED".equals(currentMode)) {
                log.info("Permanently deleting closed room: {}", roomId);
                roomRepository.deleteRoom(roomId);
            } else {
                // 상담사가 상담 종료 시 BOT 모드로 복귀 (CLOSED가 아닌 BOT으로 변경)
                log.info("Agent ending consultation, switching room {} back to BOT mode", roomId);

                // 상담 종료 알림 메시지 발송
                ChatMessage notice = ChatMessage.builder()
                        .roomId(roomId)
                        .sender("System")
                        .senderRole(UserRole.BOT)
                        .message("상담사와의 상담이 종료되었습니다. 다시 챗봇과 대화하실 수 있습니다.")
                        .type(MessageType.TALK)
                        .build();
                messageBroker.publish(notice);

                // 방 상태를 BOT으로 변경 (고객이 다시 봇과 대화 가능)
                roomRepository.setRoutingMode(roomId, "BOT");

                // 상담사 배정 해제 (assignedAgent 키 삭제)
                roomRepository.setAssignedAgent(roomId, null); // null로 설정하여 키 삭제
                // 상담사 멤버 정보 제거 (Redis 멤버 목록 정리)
                roomRepository.removeMember(roomId, userInfo.getUserId());

                // // @TODO: DB저장 임시 막음
                // // PostgreSQL에 상태 업데이트
                // chatSessionService.updateSessionStatus(roomId, "BOT"); // DB
                //
                // // 종료 메시지도 이력에 저장
                // ChatHistory chatHistory = ChatHistory.builder()
                //         .roomId(roomId)
                //         .senderId("SYSTEM")
                //         .senderName("System")
                //         .senderRole("SYSTEM")
                //         .message(notice.getMessage())
                //         .messageType("TALK")
                //         .createdAt(now) // 서버 타임스탬프 사용
                //         .build();
                // chatHistoryService.saveChatHistory(chatHistory); // DB
            }

            roomUpdateBroadcaster.broadcastRoomList();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("▲ Failed to close room", e);
            return ResponseEntity.status(500).build();
        }
    }


    /**
     *     // 상담사에게 전체 상담방 목록을 반환
     * @return
     */
    public ResponseEntity<List<ChatRoom>> findAllRooms() {
        log.info("▼ Agent request findAllRooms./api/agent > /rooms S");
        ResponseEntity<List<ChatRoom>> ret
             = ResponseEntity.ok(roomRepository.findAllRooms());
        log.info("▲ Agent request findAllRooms./api/agent > /rooms E");
        return ret;
    }

    /* 특정 상담방 상세 정보를 조회
    $ curl -X GET http://localhost:28070/api/agent/rooms/room-dd7e66c
    {"roomId":"room-dd7e66c","roomName":"room-dd7e66c","members":[],"status":"BOT","assignedAgent":null,"createdAt":0,"lastActivityAt":0,"custId":null}
    */

    /**
     * 
     * @param roomId
     * @param bearerToken
     * @return
     */
    public ResponseEntity<ChatRoom> findRoomById(
            String roomId,
            String bearerToken) {

        log.info("▼ findRoomById S. Agent request findRoomById: roomId={}", roomId);
        // 토큰 체크 추가 (선택사항이지만 일관성을 위해)
        if ( tokenService.isValidBearerToken(bearerToken) ) {
            tokenService.parseToken(bearerToken);
        }

        ChatRoom room = roomRepository.findRoomById(roomId);

        log.info("▲ findRoomById E. room:{}", room);

        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(room);
    }

    public ResponseEntity<UserInfo> login(String id, String pw, String status) {
        log.info("▼ login S. 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 시작./api/agent > /login S");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = agentAuthService.login(id, pw, status);
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
        log.info("▲ login E. 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 완료./api/agent > /login E");
        return ret;
    }

    // Authorization 헤더의 토큰을 검증해 현재 상담사 정보 반환
    public ResponseEntity<UserInfo> getCurrentAgent(String bearerToken) {
        log.info("▼ getCurrentAgent E. Uri:{} S.", UtilString.getUriPath());

        if ( !tokenService.isValidBearerToken(bearerToken) ) {
            return ResponseEntity.status(401).build();
        }

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        if (userInfo == null) {
            log.warn("▶ userInfo == null.");
            return ResponseEntity.status(401).build();
        }

        // 하트비트 - 온라인 상태 유지. 다른 방식으로 처리할 필요가 있음. 여기서는 토큰만 확인하는 것으로 일을 해야 함
        // agentAuthService.heartbeat(userInfo.getUserId());

        log.info("▲ getCurrentAgent E. Uri:{} E. userInfo:{}", UtilString.getUriPath(), userInfo);
        return ResponseEntity.ok(userInfo);
    }
}

