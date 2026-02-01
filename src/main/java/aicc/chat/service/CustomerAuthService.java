package aicc.chat.service;

import aicc.chat.consts.Constants;
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
public class CustomerAuthService {

    @Value("${app.auth.login-api-url}")
    private String loginApiUrl;

    private final TokenService tokenService;
    private final UserAccountMapper userAccountMapper;
    private final StringRedisTemplate redisTemplate;

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
        
        if (userInfo != null) {
            // Redis에 온라인 고객 등록 (Hash 구조, 30분 TTL)
            String customerKey = Constants.ONLINE_CUSTOMERS_KEY + ":" + userInfo.getUserId();
            java.util.Map<String, String> customerInfo = new java.util.HashMap<>();
            customerInfo.put("userName", userInfo.getUserName());
            customerInfo.put("userId", userInfo.getUserId());
            customerInfo.put("loginTime", LocalDateTime.now().toString());
            customerInfo.put("companyId", companyId != null ? companyId : "default");
            if (userInfo.getEmail() != null) {
                customerInfo.put("email", userInfo.getEmail());
            }
            
            redisTemplate.opsForHash().putAll(customerKey, customerInfo);
            redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
            log.info("Customer {} registered as online in Redis with Hash structure", userInfo.getUserId());
        }
        
        log.info("◀ login E. userInfo:{}", userInfo);
        return userInfo;
    }
    
    /**
     * 고객 로그아웃 - Redis에서 제거
     */
    public void logout(String userId) {
        log.info("▶ logout S. userId:{}", userId);
        String customerKey = Constants.ONLINE_CUSTOMERS_KEY + ":" + userId;
        redisTemplate.delete(customerKey);
        log.info("Customer {} removed from online list in Redis", userId);
        log.info("◀ logout E");
    }
    
    /**
     * 고객의 roomId 업데이트 (채팅방 생성 시 호출)
     */
    public void updateRoomId(String userId, String roomId) {
        log.info("▶ updateRoomId S. userId:{}, roomId:{}", userId, roomId);
        String customerKey = Constants.ONLINE_CUSTOMERS_KEY + ":" + userId;
        
        // Hash에 roomId 필드 추가/업데이트
        redisTemplate.opsForHash().put(customerKey, "roomId", roomId);
        
        // TTL 재설정 (30분)
        redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
        log.info("Customer {} roomId updated to {}", userId, roomId);
        log.info("◀ updateRoomId E");
    }
    
    /**
     * 고객의 sessionId 업데이트 (WebSocket 연결 시 호출)
     */
    public void updateSessionId(String userId, String sessionId) {
        log.info("▶ updateSessionId S. userId:{}, sessionId:{}", userId, sessionId);
        String customerKey = Constants.ONLINE_CUSTOMERS_KEY + ":" + userId;
        
        // Hash에 sessionId 필드 추가/업데이트
        redisTemplate.opsForHash().put(customerKey, "sessionId", sessionId);
        
        // TTL 재설정 (30분)
        redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
        log.info("Customer {} sessionId updated", userId);
        log.info("◀ updateSessionId E");
    }
}

