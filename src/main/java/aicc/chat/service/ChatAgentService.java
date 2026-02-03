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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
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
     * 상담원 가용성 확인: 로그인한 상담사가 있고 3개 미만의 상담을 하고 있는지 확인
     * @return
     */

    public Map<String, Object> checkAgentAvailability() {
        // 1. 온라인 상담원 목록 조회 (Hash 구조)
        Set<String> onlineAgentKeys = redisTemplate.keys(Constants.ONLINE_AGENTS_KEY + ":*"); // "chat:user-agents:*"
        Map<String, String> onlineAgents = new java.util.HashMap<>(); // agentId -> userName

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

        log.info("▶ Online agents: {} (count: {})", onlineAgents, onlineAgents.size());

        // 온라인 상담원이 없으면 즉시 불가 반환
        if (onlineAgents.isEmpty()) {
            log.info("▶ checkAgentAvailability E. No online agents available");
            return Map.of(
                "available"       , false,
                "onlineAgentCount", 0,
                "agentCount"      , 0,
                "agentRoomCount"  , java.util.Collections.emptyMap()
            );
        }

        // 2. 상담원이 배정된 방 개수 세기
        List<ChatRoom> allRooms = roomRepository.findAllRooms();
        Map<String, Long> agentRoomCount = allRooms.stream()
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
                log.info("▶ checkAgentAvailability E. Agent {} - current chats: {}, available: {}", agentName, currentChats, available);
                return available;
            });

        log.info("▶ Agent availability check - Online: {}, Available: {}, Room count: {}",
                 onlineAgents.size(), hasAvailableAgent, agentRoomCount);

        log.info("▶ checkAgentAvailability E.");
        return Map.of(
            "available"       , hasAvailableAgent,
            "onlineAgentCount", onlineAgents.size(),
            "agentCount"      , agentRoomCount.size(),
            "agentRoomCount"  , agentRoomCount
        );
    }
}

