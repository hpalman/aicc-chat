package aicc.chat.controller;

import aicc.chat.service.WebSocketSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * WebSocket 세션 관리 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class WebSocketSessionController {

    private final WebSocketSessionService webSocketSessionService;

    /**
     * 모든 활성 세션 조회
     *
     * GET /api/session/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllSessions() {
        log.info("▶ 전체 세션 조회 요청");

        Set<String> sessions = webSocketSessionService.getAllActiveSessions();
        long totalCount = webSocketSessionService.getTotalSessionCount();

        Map<String, Object> response = new HashMap<>();
        response.put("totalCount", totalCount);
        response.put("sessions", sessions);

        log.info("◀ 전체 활성 세션: {} 개", totalCount);
        return ResponseEntity.ok(response);
    }

    /**
     * 세션ID로 사용자 정보 조회
     *
     * GET /api/session/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        log.info("▼ 세션 정보 조회 - sessionId: {}", sessionId);

        String userId = webSocketSessionService.getUserIdBySessionId(sessionId);
        String userRole = webSocketSessionService.getUserRoleBySessionId(sessionId);

        if (userId == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("userId", userId);
        response.put("userRole", userRole);

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자ID로 모든 활성 세션 조회
     *
     * GET /api/session/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserSessions(@PathVariable String userId) {
        log.info("▼ 사용자 세션 조회 - userId: {}", userId);

        Set<String> sessions = webSocketSessionService.getSessionIdsByUserId(userId);
        boolean isOnline = webSocketSessionService.isUserOnline(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("isOnline", isOnline);
        response.put("sessionCount", sessions.size());
        response.put("sessions", sessions);

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 온라인 상태 확인
     *
     * GET /api/session/user/{userId}/online
     */
    @GetMapping("/user/{userId}/online")
    public ResponseEntity<Map<String, Object>> checkUserOnline(@PathVariable String userId) {
        log.info("▼ 사용자 온라인 확인 - userId: {}", userId);

        boolean isOnline = webSocketSessionService.isUserOnline(userId);
        Set<String> sessions = webSocketSessionService.getSessionIdsByUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("isOnline", isOnline);
        response.put("sessionCount", sessions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * 세션 TTL 갱신 (하트비트)
     *
     * POST /api/session/{sessionId}/refresh
     */
    @PostMapping("/{sessionId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshSession(@PathVariable String sessionId) {
        log.info("▼ 세션 TTL 갱신 - sessionId: {}", sessionId);

        String userId = webSocketSessionService.getUserIdBySessionId(sessionId);
        if (userId == null) {
            return ResponseEntity.notFound().build();
        }

        webSocketSessionService.refreshSessionTTL(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("userId", userId);
        response.put("refreshed", true);

        return ResponseEntity.ok(response);
    }

    /**
     * 세션 통계 조회
     *
     * GET /api/session/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        log.info("▼ 세션 통계 조회 요청");

        long totalSessions = webSocketSessionService.getTotalSessionCount();

        Map<String, Object> response = new HashMap<>();
        response.put("totalActiveSessions", totalSessions);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
}
