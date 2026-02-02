package aicc.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 스케줄러 제어 메시지 DTO
 * Redis PUB/SUB 채널을 통해 스케줄러를 제어하기 위한 메시지 구조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SchedulerControlMessage {
    /**
     * 제어 액션 타입
     * START: 스케줄러 시작
     * STOP: 스케줄러 중지
     * STATUS: 상태 조회 (응답은 Redis 채널로 발행되지 않음)
     */
    private String action;

    /**
     * 유휴 타임아웃 시간 (밀리초)
     * START 액션에서만 사용, null이면 기본값 사용
     */
    private Long idleTimeout;

    /**
     * 정리 작업 실행 주기 (밀리초)
     * START 액션에서만 사용, null이면 기본값 사용
     */
    private Long checkInterval;

    /**
     * 요청 ID (선택사항)
     * 요청과 응답을 매칭하기 위한 식별자
     */
    private String requestId;

    /**
     * 서버 식별자 (선택사항)
     * 메시지를 발행한 서버를 식별하기 위한 값
     */
    private String serverId;
}
