package aicc.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 스케줄러 시작 요청 DTO
 * idle-timeout과 check-interval 값을 동적으로 설정할 수 있습니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerStartRequest {
    /**
     * 유휴 타임아웃 시간 (밀리초)
     * null이면 기본값 사용
     */
    private Long idleTimeout;

    /**
     * 정리 작업 실행 주기 (밀리초)
     * null이면 기본값 사용
     */
    private Long checkInterval;
}
