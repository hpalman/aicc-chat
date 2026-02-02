package aicc.chat.controller;

import aicc.chat.dto.SchedulerControlMessage;
import aicc.chat.dto.SchedulerStartRequest;
import aicc.chat.service.RoomCleanupSchedulerManager;
import aicc.chat.service.RoomCleanupSchedulerManager.SchedulerStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

/**
 * cleanupIdleRooms() 스케줄러의 시작/중지 및 상태 조회 API.
 * app.chat.cleanup.enabled=true 일 때만 노출됩니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cleanup/scheduler")
@ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCleanupController {

    private final RoomCleanupSchedulerManager schedulerManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.system-mode:REDIS_ONLY}")
    private String systemMode;

    private static final String SCHEDULER_CONTROL_CHANNEL = "scheduler.control";

    /**
     * 스케줄러 시작
     * RequestBody로 idle-timeout과 check-interval 값을 전달할 수 있습니다.
     * 값이 없으면 application.yml의 기본값을 사용합니다.
     * 
     * 다중 서버 환경에서는 Redis PUB/SUB 채널로도 메시지를 발행하여
     * 모든 서버 인스턴스에서 스케줄러가 시작되도록 합니다.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody(required = false) SchedulerStartRequest request) {
        Long idleTimeout = request != null ? request.getIdleTimeout() : null;
        Long checkInterval = request != null ? request.getCheckInterval() : null;
        
        // Redis PUB/SUB으로 메시지 발행 (다중 서버 환경 지원)
        publishSchedulerControl("START", idleTimeout, checkInterval);
        
        // 현재 서버에서도 실행
        boolean started = schedulerManager.start(idleTimeout, checkInterval);
        SchedulerStatus status = schedulerManager.getStatus();
        return ResponseEntity.ok(Map.of(
                "message", started ? "스케줄러가 시작되었습니다." : "스케줄러가 이미 실행 중입니다.",
                "status", status
        ));
    }

    /**
     * 스케줄러 중지
     * 
     * 다중 서버 환경에서는 Redis PUB/SUB 채널로도 메시지를 발행하여
     * 모든 서버 인스턴스에서 스케줄러가 중지되도록 합니다.
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        // Redis PUB/SUB으로 메시지 발행 (다중 서버 환경 지원)
        publishSchedulerControl("STOP", null, null);
        
        // 현재 서버에서도 실행
        boolean stopped = schedulerManager.stop();
        SchedulerStatus status = schedulerManager.getStatus();
        return ResponseEntity.ok(Map.of(
                "message", stopped ? "스케줄러가 중지되었습니다." : "스케줄러가 실행 중이 아닙니다.",
                "status", status
        ));
    }

    /**
     * 스케줄러 현재 상태 조회
     * 
     * 참고: STATUS는 현재 서버의 상태만 반환합니다.
     * 다중 서버 환경에서는 각 서버의 상태가 다를 수 있습니다.
     */
    @GetMapping("/status")
    public ResponseEntity<SchedulerStatus> status() {
        return ResponseEntity.ok(schedulerManager.getStatus());
    }

    /**
     * Redis PUB/SUB 채널로 스케줄러 제어 메시지를 발행합니다.
     * 
     * @param action 제어 액션 (START, STOP, STATUS)
     * @param idleTimeout 유휴 타임아웃 (START 액션에서만 사용)
     * @param checkInterval 체크 간격 (START 액션에서만 사용)
     */
    private void publishSchedulerControl(String action, Long idleTimeout, Long checkInterval) {
        // REDIS_ONLY 모드일 때만 Redis PUB/SUB 사용
        if (!"REDIS_ONLY".equals(systemMode)) {
            log.debug("System mode is not REDIS_ONLY, skipping Redis PUB/SUB");
            return;
        }

        try {
            String serverId = getServerId();
            String requestId = UUID.randomUUID().toString();

            SchedulerControlMessage controlMessage = SchedulerControlMessage.builder()
                    .action(action)
                    .idleTimeout(idleTimeout)
                    .checkInterval(checkInterval)
                    .requestId(requestId)
                    .serverId(serverId)
                    .build();

            String messageJson = objectMapper.writeValueAsString(controlMessage);
            redisTemplate.convertAndSend(SCHEDULER_CONTROL_CHANNEL, messageJson);
            
            log.info("Published scheduler control message to Redis channel '{}': action={}, requestId={}, serverId={}", 
                    SCHEDULER_CONTROL_CHANNEL, action, requestId, serverId);
        } catch (Exception e) {
            log.error("Failed to publish scheduler control message to Redis", e);
        }
    }

    /**
     * 서버 식별자를 반환합니다.
     * 호스트명과 포트를 조합하여 고유한 서버 ID를 생성합니다.
     */
    private String getServerId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            return String.format("%s-%s", hostname, hostAddress);
        } catch (Exception e) {
            log.warn("Failed to get server ID, using default", e);
            return "unknown-server";
        }
    }
}
