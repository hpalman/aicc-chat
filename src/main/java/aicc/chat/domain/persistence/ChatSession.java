package aicc.chat.domain.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 상담 세션 도메인 모델
 * 각 상담방의 전체 세션 정보를 저장
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    
    /**
     * 세션 고유 ID (자동 증가)
     */
    private Long id;
    
    /**
     * 채팅방 ID
     */
    private String roomId;
    
    /**
     * 채팅방 이름
     */
    private String roomName;
    
    /**
     * 고객 ID
     */
    private String customerId;
    
    /**
     * 고객 이름
     */
    private String customerName;
    
    /**
     * 배정된 상담원 이름
     */
    private String assignedAgent;
    
    /**
     * 세션 상태 (BOT, WAITING, AGENT, CLOSED)
     */
    private String status;
    
    /**
     * 회사 ID
     */
    private String companyId;
    
    /**
     * 상담 시작 시간
     */
    private LocalDateTime startedAt;
    
    /**
     * 상담 종료 시간
     */
    private LocalDateTime endedAt;
    
    /**
     * 마지막 활동 시간
     */
    private LocalDateTime lastActivityAt;
    
    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;
    
    /**
     * 수정 시간
     */
    private LocalDateTime updatedAt;
}
