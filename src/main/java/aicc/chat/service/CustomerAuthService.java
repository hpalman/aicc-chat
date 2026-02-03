package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.UserAccount;
import aicc.chat.domain.redis.RoomInfo;
import aicc.chat.domain.redis.UserCustomer;
import aicc.chat.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
        log.info("▼ _login S. userAccountMapper.selectCustomerByLogin call");
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


String userId = account.getUserId();
String newRoomId = newRoomId(userId);

        UserInfo userInfo = UserInfo.builder()
                .roomId(newRoomId)
                .userId(userId)
                .userName(account.getUserName())
                .role(role)
                .email(account.getEmail())
                .companyId(account.getCompanyId() != null ? account.getCompanyId() : companyId)
                .status(0)
                .build();

        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId; // chat:user-customer:{userId}
        Boolean exists = redisTemplate.hasKey(customerKey);
        if ( exists ) { // 이미 존재함
            userInfo.setStatus(-1); // 이미 로그인됨
            return userInfo;
        }

        // 토큰정보
        userInfo.setToken(tokenService.generateToken(userInfo));
        log.info("▲ _login E");
        return userInfo;

    }
    public UserInfo login(String id, String password, String companyId) {
        // 고객 로그인 후 토큰을 생성해 반환
        log.info("▼ login S. id:{}, password:{}, companyId:{}", id, password, companyId);
        UserInfo userInfo = _login(id, password, companyId);
        /* 여기서 만들면 안될 거 같음
        if (userInfo != null) {
            setUserCustomers(userInfo);
        }
         */
        log.info("▲ login E. userInfo:{}", userInfo);
        return userInfo;
    }

    /*
     *  고객의 채팅 시작 정보 레디스에 설정
     *  chat:user-customer
     *  setUserCustomers
     */
    public void setUserCustomers(UserInfo userInfo, String roomId) {
        String companyId = userInfo.getCompanyId();

        // Redis에 온라인 고객 등록 (Hash 구조, 30분 TTL)
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userInfo.getUserId();

        Map<Object, Object> map = redisTemplate.opsForHash().entries(customerKey);
        String assignedAgent   = (String) map.get("assignedAgent");


        Map<String, String> customerInfo = new HashMap<>();
        customerInfo.put("roomId"   , roomId);
      //customerInfo.put("userId"   , userInfo.getUserId()); // 불필요
        customerInfo.put("userName" , userInfo.getUserName());
        customerInfo.put("loginTime", LocalDateTime.now().toString());
        customerInfo.put("companyId", companyId != null ? companyId : "default");
        customerInfo.put("email"    , userInfo.getEmail());

        log.info("▶ customerKey:{}, customerInfo:{}", customerKey, customerInfo);
        redisTemplate.opsForHash().putAll(customerKey, customerInfo);
        redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);

        log.info("Customer {} registered as online in Redis with Hash structure", userInfo.getUserId());
    }

    /**
     * 고객 로그아웃 - Redis에서 제거
     * chat:user-customer:{userId}
     * chat:room-info:{roomId}
     * chat:room-member:{roomId} routingMode:CLOSED
     */
    public void logout(String userId) {
        log.info("▶ logout S. userId:{}", userId);
        ObjectMapper mapper = new ObjectMapper();


        // Redis에서 채팅방 관련 모든 키 삭제
        // 1. chat:rooms에서 roomId 제거
        // 2. chat:room-info:{roomId} Hash 삭제
        // 3. chat:room-member:{roomId} Set 삭제
        //roomRepository.deleteRoom(roomId);
        //log.info("✅ Redis room keys deleted: roomId={}", roomId);

        // Redis에서 고객의 roomId 제거 및 고객 정보 삭제
        // chat:user-customer:{userId} Hash 삭제
        //customerAuthService.updateRoomId(userId, null);
        /////////////customerAuthService.deleteCustomer(userId, roomId); // REDIS. chat:user-customer:{userId}

        // 1. chat:user-customer:{userId}의 정보 삭제
        String userCustomerKey = Constants.USER_CUSTOMER_KEY + ":" + userId; // chat:user-customer:{userId}

        Map<Object, Object> ucMap = redisTemplate.opsForHash().entries(userCustomerKey);
        UserCustomer userCustomer = mapper.convertValue(ucMap, UserCustomer.class);
        //
        redisTemplate.delete(userCustomerKey);
String roomId = userCustomer.getRoomId();

        // 2. chat:room-member:{roomId} 처리 또는 삭제
        String roomMemberKey = Constants.ROOM_MEMBERS_KEY_PREFIX + userCustomer.getRoomId();
        if ( redisTemplate.hasKey(roomMemberKey) ) {
            // 1) 먼저 해당 값이 Set에 있는지 확인 (선택적)
            Boolean isMember = redisTemplate.opsForSet().isMember(roomMemberKey, userId);
            if (Boolean.TRUE.equals(isMember)) {
                // 2) 있으면 해당 항목만 삭제
                Long removedCount = redisTemplate.opsForSet().remove(roomMemberKey, userId);
                log.info("▶ 삭제된 항목 수:{}", removedCount);
            }
            Long size = redisTemplate.opsForSet().size(roomMemberKey);
            if ( size != null || size == 0) {
                redisTemplate.delete(roomMemberKey);
            }
        }

        // 3. room-info:{roomId} 처리 또는 삭제
        String roomInfoKey = Constants.ROOM_INFO_KEY_PREFIX + roomId;
        if ( redisTemplate.hasKey(roomInfoKey) ) {
            Map<Object, Object> map = redisTemplate.opsForHash().entries(roomInfoKey);
            RoomInfo roomInfo = mapper.convertValue(map, RoomInfo.class);
            log.info("▶ roomInfo:{}", roomInfo);
            boolean delete = true;
            if ( map.containsKey("assignedAgent") ) {
                Object obj = map.get("assignedAgent");
                String assignedAgent = ( obj == null) ? "" : obj.toString();
                if (assignedAgent.length() > 0 ) {
                    delete = false;
                }
            }
            // 삭제
            if ( delete ) {
                redisTemplate.delete(roomInfoKey);
                log.info("▶ roomMemberKey:{} 삭제", roomInfoKey);
            }
            log.info("Customer {} removed from online list in Redis", userId);
        }

        log.info("◀ logout E");
    }

    /**
     * 고객의 roomId 업데이트 (채팅방 생성 시 호출)
     */
    public void updateRoomId(String userId, String roomId) {
        log.info("▶ updateRoomId S. userId:{}, roomId:{}", userId, roomId);
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId;

        // Hash에 roomId 필드 추가/업데이트
        redisTemplate.opsForHash().put(customerKey, "roomId", roomId); // "chat:user-customer:{userId} roomId:value

        // TTL 재설정 (30분)
        redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
        log.info("Customer {} roomId updated to {}", userId, roomId);
        log.info("◀ updateRoomId E");
    }

    /**
     * 고객 정보 삭제
     */
    public void deleteCustomer(String userId, String roomId) {
        log.info("▶ deleteCustomer S. userId:{}, roomId:{}", userId, roomId);
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId; // chat:user-customer:{userId}

        // Hash에 roomId 필드 추가/업데이트
        // redisTemplate.opsForHash().d.put(customerKey, "roomId", roomId);
        redisTemplate.delete(customerKey);

        // TTL 재설정 (30분)
        //redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
        //log.info("Customer {} roomId updated to {}", userId, roomId);
        log.info("◀ deleteCustomer E");
    }

    /**
     * 고객의 sessionId 업데이트 (WebSocket 연결 시 호출)
     */
    public void updateSessionId(String userId, String sessionId) {
        log.info("▶ updateSessionId S. userId:{}, sessionId:{}", userId, sessionId);
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId;

        // Hash에 sessionId 필드 추가/업데이트
        redisTemplate.opsForHash().put(customerKey, "sessionId", sessionId);

        // TTL 재설정 (30분)
        redisTemplate.expire(customerKey, 30, TimeUnit.MINUTES);
        log.info("Customer {} sessionId updated", userId);
        log.info("◀ updateSessionId E");
    }

    /**
     * chat:user-customer:{userId} Hash에서 roomId 값 즉, 고객의 현재 roomId 조회
     */
    public String getRoomId(String userId) {
        log.info("▶ getRoomId S. userId:{}", userId);
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId; // chat:user-customer:{userId}
        Object roomId = redisTemplate.opsForHash().get(customerKey, "roomId");
        String result = roomId != null ? roomId.toString() : null;
        log.info("Customer {} roomId: {}", userId, result);
        log.info("◀ getRoomId E");
        return result;
    }


    /**
     * 고유한 방ID를 생성
     * @param userId 예약
     * @return
     */
    public String newRoomId(String userId) {
        String newRoomId = "room-" + UUID.randomUUID().toString().substring(0, 8);// 550e8400-e29b-41d4-a716-446655440000에서 앞 자리
        return newRoomId;
    }

}

