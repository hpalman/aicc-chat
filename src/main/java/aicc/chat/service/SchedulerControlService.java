package aicc.chat.service;

import aicc.chat.dto.SchedulerControlMessage;
import aicc.chat.service.RoomCleanupSchedulerManager.SchedulerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 스케줄러 제어 서비스
 * Redis PUB/SUB 채널을 통해 받은 스케줄러 제어 메시지를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerControlService {

    private final RoomCleanupSchedulerManager schedulerManager;

    /**
     * 스케줄러 제어 메시지를 처리합니다.
     *
     * @param controlMessage 제어 메시지
     */
    public void handleControlMessage(SchedulerControlMessage controlMessage) {
        log.info("▼ handleControlMessage S. controlMessage:{}", controlMessage);
        if (controlMessage == null || controlMessage.getAction() == null) {
            log.warn("▶ Invalid scheduler control message received: {}", controlMessage);
            return;
        }

        String action = controlMessage.getAction().toUpperCase();
        log.info("▶ action:{}, requestId:{}, serverId:{}",
                action, controlMessage.getRequestId(), controlMessage.getServerId());

        try {
            switch (action) {
                case "START":
                    handleStart(controlMessage);
                    break;
                case "STOP":
                    handleStop();
                    break;
                case "STATUS":
                    handleStatus();
                    break;
                default:
                    log.warn("▶ Unknown scheduler control action: {}", action);
            }
        } catch (Exception e) {
            log.error("▶ Error handling scheduler control message: {}", controlMessage, e);
        }

        log.info("▲ handleControlMessage E.");
    }

    /**
     * 스케줄러 시작 처리
     */
    private void handleStart(SchedulerControlMessage controlMessage) {
        Long idleTimeout = controlMessage.getIdleTimeout();
        Long checkInterval = controlMessage.getCheckInterval();

        boolean started = schedulerManager.start(idleTimeout, checkInterval);
        SchedulerStatus status = schedulerManager.getStatus();

        log.info("▶ Scheduler START command processed. started: {}, status: {}", started, status);
    }

    /**
     * 스케줄러 중지 처리
     */
    private void handleStop() {
        boolean stopped = schedulerManager.stop();
        SchedulerStatus status = schedulerManager.getStatus();

        log.info("▶ Scheduler STOP command processed. stopped: {}, status: {}", stopped, status);
    }

    /**
     * 스케줄러 상태 조회 처리
     */
    private void handleStatus() {
        SchedulerStatus status = schedulerManager.getStatus();
        log.info("▶ Scheduler STATUS command processed. status: {}", status);
    }
}
