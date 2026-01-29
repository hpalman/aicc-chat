package aicc.chat.service.inteface;

import aicc.chat.domain.persistence.ChatSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 세션 서비스 인터페이스
 */
public interface ChatSessionService {
    
    /**
     * 상담 세션 생성
     */
    void createChatSession(ChatSession chatSession);
    
    /**
     * 채팅방 ID로 세션 조회
     */
    ChatSession getChatSessionByRoomId(String roomId);
    
    /**
     * 고객 ID로 세션 목록 조회
     */
    List<ChatSession> getChatSessionsByCustomerId(String customerId);
    
    /**
     * 상담원 이름으로 세션 목록 조회
     */
    List<ChatSession> getChatSessionsByAgent(String assignedAgent);
    
    /**
     * 회사 ID와 상태로 세션 목록 조회
     */
    List<ChatSession> getChatSessionsByCompanyIdAndStatus(String companyId, String status);
    
    /**
     * 활성 세션 목록 조회
     */
    List<ChatSession> getActiveChatSessions();
    
    /**
     * 세션 상태 변경
     */
    void updateSessionStatus(String roomId, String status);
    
    /**
     * 상담원 배정
     */
    void assignAgent(String roomId, String assignedAgent);
    
    /**
     * 상담 종료
     */
    void endSession(String roomId);
    
    /**
     * 마지막 활동 시간 갱신
     */
    void updateLastActivity(String roomId);
}
