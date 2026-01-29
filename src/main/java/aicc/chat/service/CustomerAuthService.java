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
public class CustomerAuthService {

    @Value("${app.auth.login-api-url}")
    private String loginApiUrl;

    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;

    private UserInfo _login(String id, String password, String companyId) {
        log.info("▶ _login S. userAccountMapper.selectCustomerByLogin call");
        UserAccount account = userAccountMapper.selectCustomerByLogin(id, password, companyId);
        if (account == null) {
            return null;
        }

        UserRole role = UserRole.CUSTOMER;
        if (account.getRole() != null) {
            try {
                role = UserRole.valueOf(account.getRole());
            } catch (IllegalArgumentException ignored) {
                role = UserRole.CUSTOMER;
            }
        }

        UserInfo userInfo = UserInfo.builder()
                .userId(account.getUserId())
                .userName(account.getUserName())
                .role(role)
                .email(account.getEmail())
                .companyId(account.getCompanyId() != null ? account.getCompanyId() : companyId)
                .build();
        // 토큰정보
        userInfo.setToken(tokenService.generateToken(userInfo));
        log.info("◀ _login E");
        return userInfo;

    }
    public UserInfo login(String id, String password, String companyId) {
        // 고객 로그인 후 토큰을 생성해 반환
        log.info("▶ login S. id:{}, password:{}, companyId:{}", id, password, companyId);
        UserInfo userInfo = _login(id, password, companyId);
        log.info("◀ login E. userInfo:{}", userInfo);
        return userInfo;
    }
}

