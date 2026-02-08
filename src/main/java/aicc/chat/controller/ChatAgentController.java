package aicc.chat.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.UserInfo;
import aicc.chat.service.ChatAgentService;
import aicc.chat.service.TokenService;
import aicc.chat.util.UtilString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
@Slf4j
public class ChatAgentController {
    private final ChatAgentService chatAgentService;
    private final TokenService          tokenService;

    /**
     * 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환
     * 자동 배정인 경우
     * 자동 배정이 아닌경우는 자동 대기 모드로 진입
     * @param body
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<UserInfo> login(
            @RequestBody Map<String,String> body) {
        String id = body.get("id");
        String pw = body.get("pw");
        String status = body.get("status");
        log.info("▼ login S. 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 시작./api/agent > /login S");
        ResponseEntity<UserInfo> ret
            = chatAgentService.login(id, pw, status);
        log.info("▲ login E. 상담사 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 완료./api/agent > /login E");
        return ret;
    }

    /**
     * Authorization 헤더의 토큰을 검증해 현재 상담사 정보 반환
     * @param bearerToken
     * @return
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
        log.info("▼ getCurrentAgent E. Uri:{} S.", UtilString.getUriPath());
        ResponseEntity<UserInfo> ret = getCurrentAgent(bearerToken);
        log.info("▲ getCurrentAgent E. ret:{}", ret);
        return ret;
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
             = chatAgentService.findAllRooms();
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

    /**
     * 특정 상담방 상세 정보를 조회
     * @param roomId
     * @param bearerToken
     * @return
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> findRoomById(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {

        log.info("▼ findRoomById S. Agent request findRoomById: roomId={}", roomId);
        ResponseEntity<ChatRoom> ret = chatAgentService.findRoomById(roomId, bearerToken);
        log.info("▲ findRoomById E. room:{}", ret);

        return ret;
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

    /**
     * 상담사를 방에 배정하였다는 알림을 발송
     * @param roomId
     * @param token
     * @param force
     * @return
     */
    @PostMapping("/rooms/assign-notify")
    public ResponseEntity<?> assignNotify(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody ChatRoom chatRoom
            ) {
        log.info("▼ assign-notify S. roomId={},force:{}, UriPath:{}", chatRoom.getRoomId(), UtilString.getUriPath());
        ResponseEntity<?> ret = chatAgentService.assignNotify(chatRoom.getRoomId(), token );
        log.info("▲ assign-notify E.");
        return ret;
    }
    
    @DeleteMapping("/rooms/{roomId}")
    // 상담 종료 또는 종료 방 삭제 처리
    public ResponseEntity<?> deleteRoom(
            @PathVariable String roomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▼ deleteRoom S. Agent request deleteRoom: roomId={}", roomId);
    	ResponseEntity<?> ret = chatAgentService.deleteRoom(roomId, token);
        log.info("▲ deleteRoom E. ret:{}", ret);
    	return ret;
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

    /**
     * 상담사 대기/대기종료 설정. 자동 배정이 아닌경우에만 처리해야 함
     * @param bearerToken
     * @param status
     * @return
     */
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

    /**
     * 상담사 대기/대기종료 상태 조회
     * @param bearerToken
     * @return
     */
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

