package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuthService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;
    private final aicc.chat.service.inteface.MessageBroker messageBroker;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
    
    public UserInfo login(String id, String password) {
        // 상담원 로그인 후 토큰을 생성해 반환
        log.info("▼ Attempting agent login via API: {}", agentLoginApiUrl);

        UserAccount account = userAccountMapper.selectAgentByLogin(id, password);
        if (account == null) {
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
                .build();

        userInfo.setToken(tokenService.generateToken(userInfo));

        // Redis에 온라인 상담원 등록 (Hash 구조, 10분 TTL)
        String agentKey = Constants.ONLINE_AGENTS_KEY + ":" + account.getUserId();
        java.util.Map<String, String> agentInfo = new java.util.HashMap<>();
        agentInfo.put("userName", account.getUserName());
        agentInfo.put("userId", account.getUserId());
        agentInfo.put("loginTime", LocalDateTime.now().toString());
        agentInfo.put("lastHeartbeat", LocalDateTime.now().toString());
        
        redisTemplate.opsForHash().putAll(agentKey, agentInfo);
        redisTemplate.expire(agentKey, 10, TimeUnit.MINUTES);
        log.info("Agent {} registered as online in Redis with Hash structure", account.getUserId());

        // ✅ 추가: 상담원 로그인 알림 브로드캐스트
        messageBroker.publish(ChatMessage.builder()
            .roomId("SYSTEM_BROADCAST")
            .sender("System")
            .senderRole(UserRole.SYSTEM)
            .message("AGENT_AVAILABLE")
            .type(MessageType.SYSTEM)
            .timestamp(LocalDateTime.now())
            .build());

        /// roomUpdateBroadcaster.broadcastAgentLogin();
        
        return userInfo;
    }

    /**
     * 상담원 하트비트 - 온라인 상태 유지
     */
    public void heartbeat(String userId) {
        log.info("▼ heartbeat. userId:{}", userId);
        String agentKey = Constants.ONLINE_AGENTS_KEY + ":" + userId;
        
        // Hash 구조에서 lastHeartbeat 필드 업데이트
        redisTemplate.opsForHash().put(agentKey, "lastHeartbeat", LocalDateTime.now().toString());
        
        // TTL 재설정 (10분)
        redisTemplate.expire(agentKey, 10, TimeUnit.MINUTES);
        log.debug("Agent {} heartbeat updated", userId);
    }
}

