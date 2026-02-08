package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.mapper.UserAccountMapper;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuthService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService          tokenService;
    private final UserAccountMapper     userAccountMapper;
    private final StringRedisTemplate   redisTemplate;
    private final MessageBroker         messageBroker;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final RoomRepository        roomRepository;

    /**
     * 상담사 로그인, 토큰을 생성해 반환
     * @param id
     * @param pw
     * @param status 대기
     * @return
     */
    public UserInfo login(String id, String pw, String status) {
        log.info("▼ login S. Attempting agent login via API: {}", agentLoginApiUrl);

        UserAccount account = userAccountMapper.selectAgentByLogin(id, pw); // DB
        if (account == null) {
            log.info("▶ account == null. id:{}, pw:{}", id, pw);
            return null;
        }

        UserRole role = UserRole.AGENT;
        if (account.getRole() != null) {
            try {
                role = UserRole.valueOf(account.getRole());
            } catch (IllegalArgumentException ignored) {
                role = UserRole.AGENT;
            }
        }

        UserInfo userInfo = UserInfo.builder()
                .userId(account.getUserId())
                .userName(account.getUserName())
                .role(role)
                .email(account.getEmail())
                .companyId(account.getCompanyId() != null ? account.getCompanyId() : "SYSTEM")
                .status(0)
                .build();

        userInfo.setToken(tokenService.generateToken(userInfo));

        // Redis에 온라인 상담사 등록 (Hash 구조, 10분 TTL)
        String agentKey = Constants.USER_AGENT_KEY + ":" + account.getUserId(); // chat:user-agents:{agentId}
        java.util.Map<String, String> agentInfo = new java.util.HashMap<>();
        agentInfo.put("userName"     , account.getUserName());
        agentInfo.put("userId"       , account.getUserId());
        agentInfo.put("loginTime"    , LocalDateTime.now().toString());
        agentInfo.put("lastHeartbeat", LocalDateTime.now().toString());

        agentInfo.put("agentStatus"  , status); // 상담사 초기 상태. NONE WORKING"WORKING"

        log.info("▶ opsForHash().putAll > agentKey:{}, agentInfo:{}", agentKey, agentInfo);
        Boolean exists = redisTemplate.hasKey(agentKey); // 이미 존재한지 검사
        if ( exists ) { // 이미 존재함
            userInfo.setStatus(-1); // 이미 로그인됨
            return userInfo;
        }

        redisTemplate.opsForHash().putAll(agentKey, agentInfo); // chat:user-agents:{agentId} 에 agent 정보 설정
     // redisTemplate.expire(agentKey, 10, TimeUnit.MINUTES); // 상담사는 TTL 넣지 말자.
        log.info("▶ Agent {} registered as online in Redis with Hash structure", account.getUserId());

        // 상담사 로그인 알림 브로드캐스트. /topic/agent-availability의 STOMP 토픽으로 메시지 발송
        messageBroker.publish(ChatMessage.builder()
            .roomId("SYSTEM_BROADCAST")
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message("AGENT_STATUS") // AGENT 상태가 변경되면 웹소켓으로 전달
            .type(MessageType.SYSTEM)
            .build());

        /// roomUpdateBroadcaster.broadcastAgentLogin();
        log.info("▲ login E. Attempting agent login via API: {}", agentLoginApiUrl);

        
        // @TODO: 자동 배정이 아닐 경우
        
        return userInfo;
    }

    /**
     * 상담사 하트비트 - 온라인 상태 유지
     */
    public void heartbeat(String userId) {
        String agentKey = Constants.USER_AGENT_KEY + ":" + userId;
        log.info("▼ heartbeat. userId:{}, agentKey:{}", userId,agentKey);

        // Hash 구조에서 lastHeartbeat 필드 업데이트
        redisTemplate.opsForHash().put(agentKey, "lastHeartbeatxx", LocalDateTime.now().toString());

        // TTL 재설정 (10분)
        redisTemplate.expire(agentKey, 10, TimeUnit.MINUTES);
        log.info("▲ Agent {} heartbeat updated");
    }
}

