package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.mapper.UserAccountMapper;
import aicc.chat.service.inteface.RoomRepository;
import aicc.chat.util.UtilString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
public class ChatAgentService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final aicc.chat.service.inteface.MessageBroker messageBroker;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;

    private final RoomRepository roomRepository;

    /**
     * 상담원 가용성 확인: agentStatus가 WAITING인 상담원만 조회하여 가용성 확인
     * @return
     */
    public Map<String, Object> checkAgentAvailability() {
        // 1. Redis의 chat:user-agent:{agentId} 키들을 조회하여 agentStatus가 WAITING인 상담원만 필터링
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
Boolean hasAvailableAgent = waitingAgentCount > 0 ? true : false; // hasAvailableAgent,; // 상담원연결 신청 가능 여부

        return Map.of(
            "available"        , hasAvailableAgent,
            //"onlineAgentCount" , waitingAgents.size(),
            "agentCount"       , agentCount, //agentRoomCount.size(),
            //"agentRoomCount"   , agentRoomCount,
            "waitingAgentCount", waitingAgentCount //waitingAgents.size()
        );

        //log.info("▶ WAITING agents: {} (count: {})", waitingAgents, waitingAgents.size());
        //
        //// WAITING 상태인 상담원이 없으면 즉시 불가 반환
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
        //// 2. 상담원이 배정된 방 개수 세기
        //List<ChatRoom> allRooms = roomRepository.findAllRooms();
        //Map<String, Long> agentRoomCount = allRooms.stream()
        //    .filter(room -> "AGENT".equals(room.getStatus()) && room.getAssignedAgent() != null)
        //    .collect(java.util.stream.Collectors.groupingBy(
        //        ChatRoom::getAssignedAgent,
        //        java.util.stream.Collectors.counting()
        //    ));
        //
        //// 3. WAITING 상태인 상담원 중 3개 미만의 상담을 하고 있는 상담원이 있는지 확인
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
     * 상담원 로그아웃 처리
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

        // Redis에서 온라인 상담원 제거 (Hash 구조 전체 삭제)
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
            .timestamp(LocalDateTime.now())
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
                .timestamp(LocalDateTime.now())
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

}

