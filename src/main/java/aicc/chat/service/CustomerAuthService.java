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
public class CustomerAuthService {

    @Value("${app.auth.login-api-url}")
    private String loginApiUrl;

    private final TokenService tokenService;

    public UserInfo login(String id, String password, String companyId) {
        log.info("Attempting customer login via API: {} for company: {}", loginApiUrl, companyId);
        
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4);
        String userName = "고객-" + randomSuffix;
        String userId = id;

        UserInfo userInfo = UserInfo.builder()
                .userId(userId)
                .userName(userName)
                .role(UserRole.CUSTOMER)
                .email(userId + "@example.com")
                .companyId(companyId)
                .build();
// 토큰정보
        userInfo.setToken(tokenService.generateToken(userInfo));
        return userInfo;
    }
}

