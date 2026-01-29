package aicc.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 세션ID와 사용자ID 매핑을 Redis에 저장/관리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSessionService {

    private final StringRedisTemplate redisTemplate;

    // Redis 키 구조
    private static final String WS_SESSION_TO_USER_PREFIX  = "ws:session:";     // sessionId -> userId 매핑
    private static final String WS_USER_TO_SESSIONS_PREFIX = "ws:user:";        // userId -> Set<sessionId> 매핑
    private static final String WS_ALL_SESSIONS_KEY        = "ws:sessions:all"; // 모든 활성 세션 Set

    // TTL 설정 (기본 24시간)
    private static final long SESSION_TTL_HOURS = 24;

    /**
     * 웹소켓 연결 시 세션ID와 사용자ID를 Redis에 저장
     *
     * @param sessionId WebSocket 세션 ID
     * @param userId 사용자 ID
     * @param userRole 사용자 역할 (CUSTOMER, AGENT 등)
     */
    public void registerSession(String sessionId, String userId, String userRole) {
        log.info("▼ broadcastRoomList. sessionId:{}, userId:{}, userRole:{}", sessionId, userId, userRole);
        if (sessionId == null || userId == null) {
            log.warn("sessionId 또는 userId가 null입니다. 등록하지 않습니다.");
            return;
        }

        log.info("WebSocket 세션 등록 - sessionId: {}, userId: {}, role: {}", sessionId, userId, userRole);

        try {
            // 1. sessionId -> userId 매핑 저장
            String sessionKey = WS_SESSION_TO_USER_PREFIX + sessionId;
            redisTemplate.opsForValue().set(sessionKey, userId, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // 2. userId -> sessionId 매핑 (Set)에 추가
            String userSessionsKey = WS_USER_TO_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().add(userSessionsKey, sessionId);
            redisTemplate.expire(userSessionsKey, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // 3. 전체 활성 세션 Set에 추가
            redisTemplate.opsForSet().add(WS_ALL_SESSIONS_KEY, sessionId);

            // 4. 역할 정보 저장 (옵션)
            if (userRole != null) {
                String roleKey = WS_SESSION_TO_USER_PREFIX + sessionId + ":role";
                redisTemplate.opsForValue().set(roleKey, userRole, SESSION_TTL_HOURS, TimeUnit.HOURS);
            }

            log.info("WebSocket 세션 등록 완료 - Redis 키: {}, {}", sessionKey, userSessionsKey);

        } catch (Exception e) {
            log.error("WebSocket 세션 등록 실패 - sessionId: {}, userId: {}", sessionId, userId, e);
        }
    }

    /**
     * 웹소켓 연결 해제 시 세션ID를 Redis에서 제거
     *
     * @param sessionId WebSocket 세션 ID
     */
    public void unregisterSession(String sessionId) {
        log.info("▼ unregisterSession. sessionId:{}",sessionId);

        if (sessionId == null) {
            log.warn("sessionId가 null입니다. 제거하지 않습니다.");
            return;
        }

        log.info("WebSocket 세션 해제 - sessionId: {}", sessionId);

        try {
            // 1. sessionId로 userId 조회
            String sessionKey = WS_SESSION_TO_USER_PREFIX + sessionId;
            String userId = redisTemplate.opsForValue().get(sessionKey);

            if (userId != null) {
                // 2. userId -> sessionId Set에서 제거
                String userSessionsKey = WS_USER_TO_SESSIONS_PREFIX + userId;
                redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

                // 3. userId의 세션이 모두 제거되었으면 키 삭제
                Long remainingSessions = redisTemplate.opsForSet().size(userSessionsKey);
                if (remainingSessions != null && remainingSessions == 0) {
                    redisTemplate.delete(userSessionsKey);
                    log.info("사용자 {}의 모든 세션이 종료되었습니다.", userId);
                }
            }

            // 4. sessionId -> userId 매핑 삭제
            redisTemplate.delete(sessionKey);

            // 5. 역할 정보 삭제
            String roleKey = WS_SESSION_TO_USER_PREFIX + sessionId + ":role";
            redisTemplate.delete(roleKey);

            // 6. 전체 활성 세션 Set에서 제거
            redisTemplate.opsForSet().remove(WS_ALL_SESSIONS_KEY, sessionId);

            log.info("WebSocket 세션 해제 완료 - sessionId: {}, userId: {}", sessionId, userId);

        } catch (Exception e) {
            log.error("WebSocket 세션 해제 실패 - sessionId: {}", sessionId, e);
        }
    }

    /**
     * 세션ID로 사용자ID 조회
     *
     * @param sessionId WebSocket 세션 ID
     * @return 사용자 ID (없으면 null)
     */
    public String getUserIdBySessionId(String sessionId) {
        log.info("▼ getUserIdBySessionId. sessionId:{}",sessionId);
        if (sessionId == null) {
            return null;
        }

        String sessionKey = WS_SESSION_TO_USER_PREFIX + sessionId;
        return redisTemplate.opsForValue().get(sessionKey);
    }

    /**
     * 사용자ID로 모든 활성 세션ID 조회
     *
     * @param userId 사용자 ID
     * @return 세션 ID Set (없으면 빈 Set)
     */
    public Set<String> getSessionIdsByUserId(String userId) {
        log.info("▼ getSessionIdsByUserId. userId:{}", userId);

        if (userId == null) {
            return Collections.emptySet();
        }

        String userSessionsKey = WS_USER_TO_SESSIONS_PREFIX + userId;
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);
        return sessions != null ? sessions : Collections.emptySet();
    }

    /**
     * 세션ID로 사용자 역할 조회
     *
     * @param sessionId WebSocket 세션 ID
     * @return 사용자 역할 (CUSTOMER, AGENT 등, 없으면 null)
     */
    public String getUserRoleBySessionId(String sessionId) {
        log.info("▼ getUserRoleBySessionId. sessionId:{}",sessionId);

        if (sessionId == null) {
            return null;
        }

        String roleKey = WS_SESSION_TO_USER_PREFIX + sessionId + ":role";
        return redisTemplate.opsForValue().get(roleKey);
    }

    /**
     * 모든 활성 세션 ID 조회
     *
     * @return 모든 활성 세션 ID Set
     */
    public Set<String> getAllActiveSessions() {
        log.info("▼ getAllActiveSessions");
        Set<String> sessions = redisTemplate.opsForSet().members(WS_ALL_SESSIONS_KEY);
        return sessions != null ? sessions : Collections.emptySet();
    }

    /**
     * 사용자가 현재 온라인인지 확인 (활성 세션이 있는지)
     *
     * @param userId 사용자 ID
     * @return 온라인 여부
     */
    public boolean isUserOnline(String userId) {
        log.info("▼ isUserOnline. userId:{}", userId);

        if (userId == null) {
            return false;
        }

        String userSessionsKey = WS_USER_TO_SESSIONS_PREFIX + userId;
        Long sessionCount = redisTemplate.opsForSet().size(userSessionsKey);
        return sessionCount != null && sessionCount > 0;
    }

    /**
     * 세션 TTL 갱신 (하트비트용)
     *
     * @param sessionId WebSocket 세션 ID
     */
    public void refreshSessionTTL(String sessionId) {
        log.info("▼ refreshSessionTTL. sessionId:{}", sessionId);
        if (sessionId == null) {
            return;
        }

        String sessionKey = WS_SESSION_TO_USER_PREFIX + sessionId;
        String userId = redisTemplate.opsForValue().get(sessionKey);

        if (userId != null) {
            // sessionId -> userId 매핑 TTL 갱신
            redisTemplate.expire(sessionKey, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // userId -> sessionId Set TTL 갱신
            String userSessionsKey = WS_USER_TO_SESSIONS_PREFIX + userId;
            redisTemplate.expire(userSessionsKey, SESSION_TTL_HOURS, TimeUnit.HOURS);

            // 역할 정보 TTL 갱신
            String roleKey = WS_SESSION_TO_USER_PREFIX + sessionId + ":role";
            redisTemplate.expire(roleKey, SESSION_TTL_HOURS, TimeUnit.HOURS);

            log.debug("세션 TTL 갱신 - sessionId: {}, userId: {}", sessionId, userId);
        }
    }

    /**
     * 모든 세션 정보 조회 (디버깅용)
     *
     * @return 세션 수
     */
    public long getTotalSessionCount() {
        log.info("▼ getTotalSessionCount");

        Long count = redisTemplate.opsForSet().size(WS_ALL_SESSIONS_KEY);
        return count != null ? count : 0;
    }
}
