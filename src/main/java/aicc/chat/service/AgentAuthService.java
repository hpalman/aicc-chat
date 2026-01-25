package aicc.chat.service;

import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuthService {

    @Value("${app.auth.agent-login-api-url}")
    private String agentLoginApiUrl;

    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;

    public UserInfo login(String id, String password) {
        // 상담원 로그인 후 토큰을 생성해 반환
        log.info("Attempting agent login via API: {}", agentLoginApiUrl);
        
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
        return userInfo;
    }
}

