package aicc.chat.service;

import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuthService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService tokenService;

    public UserInfo login(String id, String password) {
        log.info("Attempting agent login via API: {}", agentLoginApiUrl);
        
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4);
        String userName = "상담원-" + randomSuffix;
        String userId = id + "-" + randomSuffix;

        UserInfo userInfo = UserInfo.builder()
                .userId(userId)
                .userName(userName)
                .role(UserRole.AGENT)
                .email(userId + "@aicc.com")
                .companyId("SYSTEM") // 상담원은 시스템 소속
                .build();

        userInfo.setToken(tokenService.generateToken(userInfo));
        return userInfo;
    }
}

