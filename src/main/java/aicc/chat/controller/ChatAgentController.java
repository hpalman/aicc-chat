package aicc.chat.controller;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.service.AgentAuthService;
import aicc.chat.service.ChatAgentService;
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

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
@Slf4j
public class ChatAgentController {
    private final ChatAgentService chatAgentService;

    private final AgentAuthService agentAuthService;

    private final RoomRepository roomRepository;
    private final ChatRoutingStrategy routingStrategy;
    private final TokenService tokenService;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final MessageBroker messageBroker;
    private final ChatSessionService chatSessionService;
    private final ChatHistoryService chatHistoryService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    // 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환
    public ResponseEntity<UserInfo> login(
            @RequestBody Map<String,String> body/*
            @RequestParam String id,
            @RequestParam String pw */) {
        String id = body.get("id");
        String pw = body.get("pw");
        String status = body.get("status");

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

    @GetMapping("/me")
    // Authorization 헤더의 토큰을 검증해 현재 상담사 정보 반환
    public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
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

    @GetMapping("/heartbeat")
    //
    public ResponseEntity<UserInfo> heartbeat(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
        log.info("▼ heartbeat E. Uri:{} S.", UtilString.getUriPath());

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

        log.info("▲ heartbeat E. Uri:{} E. userInfo:{}", UtilString.getUriPath(), userInfo);
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 상담사 로그아웃
     * @param bearerToken
     * @return
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
        log.info("▼ logout S. 상담사 로그아웃. path:{}", UtilString.getUriPath());
        HttpStatus status = chatAgentService.logout(bearerToken);
        log.info("▼ logout E. 상담사 로그아웃. status:{}", status);
        return ResponseEntity.status(HttpStatusCode.valueOf(status.value())).build();
    }

    @GetMapping("/rooms")
    // 상담사에게 전체 상담방 목록을 반환
    public ResponseEntity<List<ChatRoom>> findAllRooms() {
        log.info("▼ Agent request findAllRooms./api/agent > /rooms S");
        ResponseEntity<List<ChatRoom>> ret
             = ResponseEntity.ok(roomRepository.findAllRooms());
        log.info("▲ Agent request findAllRooms./api/agent > /rooms E");
        return ret;
    }

    /**
     * 상담사 가용성 확인: 로그인한 상담사가 있고 3개 미만의 상담을 하고 있는지 확인
     * @return
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> availability() {
        log.info("▼ checkAgentAvailability S. Uri:{} S", ServletUriComponentsBuilder.fromCurrentRequest().toUriString() );
        Map<String, Object> map
            = chatAgentService.checkAgentAvailability();
        log.info("▲ checkAgentAvailability E. map:{}", map);
        return ResponseEntity.ok(map);
    }

    /* 특정 상담방 상세 정보를 조회
    $ curl -X GET http://localhost:28070/api/agent/rooms/room-dd7e66c
    {"roomId":"room-dd7e66c","roomName":"room-dd7e66c","members":[],"status":"BOT","assignedAgent":null,"createdAt":0,"lastActivityAt":0,"custId":null}
    */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> findRoomById(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

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

    @PostMapping("/rooms/{roomId}/assign")
    // 상담사를 방에 배정하고 상태/이력을 갱신
    public ResponseEntity<?> assignAgent(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(/*value = "force",*/ required = false, defaultValue = "false") boolean force) {
        log.info("▼ assignAgent S. roomId={},force:{}, UriPath:{}", roomId, force, UtilString.getUriPath());
        ResponseEntity<?> ret = chatAgentService.assignAgent(roomId, token, force );
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
    public ResponseEntity<?> _deleteRoom(String roomId, String bearerToken) {
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
     * 상담사 메시지 수신 컨트롤러
     * @param message
     * @param headerAccessor
     */
    @MessageMapping("/agent/chat")
    // 상담사 채팅 메시지를 받아 이력 저장 후 라우팅
    public void onAgentMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        message.setTimestamp(LocalDateTime.now());
        log.info("▼ onAgentMessage S. 상담사 메시지 처리. S");
        chatAgentService.agentMessage(message, headerAccessor);
        log.info("▲ onAgentMessage E. 상담사 메시지 처리. E");
    }

    @PostMapping("/status/{status}")
    public ResponseEntity<?> setAgentStatus(
            @RequestHeader(value = "Authorization", required = false) String bearerToken,
            @PathVariable String status
    ) {
        log.info("▼ setAgentStatus S. token:{}, status:{}", UtilString.leftRight(bearerToken,15,5), status);

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        String agentId = userInfo.getUserId();
        Boolean ret = chatAgentService.setAgentStatus(agentId, status);

        log.info("▲ setAgentStatus E. ret:{}, agentId:{}, status:{}", ret, agentId, status);
        return ResponseEntity.ok(Map.of("code", (ret == true) ? 0 : -1, "agentId",agentId, "status",status));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getAgentStatus(
            @RequestHeader(value = "Authorization", required = false) String bearerToken
    ) {
        log.info("▼ getAgentStatus S. token:{}", UtilString.leftRight(bearerToken,15,5));

        UserInfo userInfo = tokenService.parseToken(bearerToken);
        String agentId = userInfo.getUserId();
        Map<?,?> map = chatAgentService.getAgentStatus(agentId);

        log.info("▲ getAgentStatus E. {}", map);
        return ResponseEntity.ok(map);
    }
}

