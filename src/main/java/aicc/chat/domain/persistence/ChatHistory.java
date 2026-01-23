package aicc.chat.domain.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 채팅 이력 도메인 모델
 * 상담사와 고객 간의 모든 대화 내용을 저장
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {
    
    /**
     * 채팅 이력 고유 ID (자동 증가)
     */
    private Long id;
    
    /**
     * 채팅방 ID
     */
    private String roomId;
    
    /**
     * 발신자 ID
     */
    private String senderId;
    
    /**
     * 발신자 이름
     */
    private String senderName;
    
    /**
     * 발신자 역할 (CUSTOMER, AGENT, BOT, SYSTEM)
     */
    private String senderRole;
    
    /**
     * 메시지 내용
     */
    private String message;
    
    /**
     * 메시지 타입 (ENTER, TALK, LEAVE, JOIN, HANDOFF, CANCEL_HANDOFF)
     */
    private String messageType;
    
    /**
     * 회사 ID
     */
    private String companyId;
    
    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;
    
    /**
     * 수정 시간
     */
    private LocalDateTime updatedAt;
}
