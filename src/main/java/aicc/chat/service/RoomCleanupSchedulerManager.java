package aicc.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * cleanupIdleRooms() 스케줄러의 시작/중지 및 상태를 관리합니다.
 * API를 통해 제어할 수 있습니다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCleanupSchedulerManager {

    private final TaskScheduler taskScheduler;
    private final RoomCleanupService roomCleanupService;

    @Value("${app.chat.cleanup.check-interval:60000}")
    private long checkIntervalMs;

    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile Instant lastRunTime;

    public RoomCleanupSchedulerManager(TaskScheduler taskScheduler, RoomCleanupService roomCleanupService) {
        this.taskScheduler = taskScheduler;
        this.roomCleanupService = roomCleanupService;
    }

    @PostConstruct
    public void init() {
        // 설정이 활성화된 경우 기존 동작 유지를 위해 스케줄러 자동 시작
        start();
    }

    /**
     * 스케줄러를 시작합니다. 이미 실행 중이면 무시합니다.
     * 
     * @param idleTimeout 유휴 타임아웃 시간 (밀리초), null이면 기본값 사용
     * @param checkInterval 정리 작업 실행 주기 (밀리초), null이면 기본값 사용
     * @return 시작 성공 여부
     */
    public synchronized boolean start(Long idleTimeout, Long checkInterval) {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            log.info("cleanupIdleRooms scheduler is already running.");
            return false;
        }
        
        // 동적 값 설정
        if (idleTimeout != null) {
            roomCleanupService.setIdleTimeout(idleTimeout);
            log.info("Idle timeout updated to: {} ms", idleTimeout);
        }
        
        long intervalToUse = checkInterval != null ? checkInterval : checkIntervalMs;
        if (checkInterval != null) {
            this.checkIntervalMs = checkInterval;
            log.info("Check interval updated to: {} ms", checkInterval);
        }
        
        Runnable task = () -> {
            lastRunTime = Instant.now();
            roomCleanupService.cleanupIdleRooms();
        };
        scheduledFuture = taskScheduler.scheduleAtFixedRate(task, Duration.ofMillis(intervalToUse));
        log.info("cleanupIdleRooms scheduler started (interval: {} ms).", intervalToUse);
        return true;
    }
    
    /**
     * 스케줄러를 시작합니다. 기본값을 사용합니다.
     */
    public synchronized boolean start() {
        return start(null, null);
    }

    /**
     * 스케줄러를 중지합니다.
     */
    public synchronized boolean stop() {
        if (scheduledFuture == null) {
            log.info("cleanupIdleRooms scheduler is not running.");
            return false;
        }
        scheduledFuture.cancel(false);
        scheduledFuture = null;
        log.info("cleanupIdleRooms scheduler stopped.");
        return true;
    }

    /**
     * 스케줄러 현재 상태를 반환합니다.
     */
    public SchedulerStatus getStatus() {
        boolean running = scheduledFuture != null && !scheduledFuture.isCancelled();
        return SchedulerStatus.builder()
                .running(running)
                .checkIntervalMs(checkIntervalMs)
                .idleTimeoutMs(roomCleanupService.getIdleTimeout())
                .lastRunTime(lastRunTime != null ? lastRunTime.toString() : null)
                .build();
    }

    public static class SchedulerStatus {
        private final boolean running;
        private final long checkIntervalMs;
        private final long idleTimeoutMs;
        private final String lastRunTime;

        SchedulerStatus(boolean running, long checkIntervalMs, long idleTimeoutMs, String lastRunTime) {
            this.running = running;
            this.checkIntervalMs = checkIntervalMs;
            this.idleTimeoutMs = idleTimeoutMs;
            this.lastRunTime = lastRunTime;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isRunning() {
            return running;
        }

        public long getCheckIntervalMs() {
            return checkIntervalMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public String getLastRunTime() {
            return lastRunTime;
        }

        public static class Builder {
            private boolean running;
            private long checkIntervalMs;
            private long idleTimeoutMs;
            private String lastRunTime;

            Builder running(boolean running) {
                this.running = running;
                return this;
            }

            Builder checkIntervalMs(long checkIntervalMs) {
                this.checkIntervalMs = checkIntervalMs;
                return this;
            }

            Builder idleTimeoutMs(long idleTimeoutMs) {
                this.idleTimeoutMs = idleTimeoutMs;
                return this;
            }

            Builder lastRunTime(String lastRunTime) {
                this.lastRunTime = lastRunTime;
                return this;
            }

            SchedulerStatus build() {
                return new SchedulerStatus(running, checkIntervalMs, idleTimeoutMs, lastRunTime);
            }
        }
    }
}
