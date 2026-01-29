package aicc.chat.service.inteface;

import aicc.chat.domain.persistence.ChatHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 이력 서비스 인터페이스
 */
public interface ChatHistoryService {
    
    /**
     * 채팅 메시지 저장
     */
    void saveChatHistory(ChatHistory chatHistory);
    
    /**
     * 채팅 메시지 일괄 저장
     */
    void saveChatHistoryBatch(List<ChatHistory> chatHistories);
    
    /**
     * 채팅방 ID로 이력 조회
     */
    List<ChatHistory> getChatHistoryByRoomId(String roomId);
    
    /**
     * 채팅방 ID와 시간 범위로 이력 조회
     */
    List<ChatHistory> getChatHistoryByRoomIdAndTimeRange(
            String roomId, 
            LocalDateTime startTime, 
            LocalDateTime endTime
    );
    
    /**
     * 고객 ID로 이력 조회
     */
    List<ChatHistory> getChatHistoryByCustomerId(String customerId);
    
    /**
     * 회사 ID와 시간 범위로 이력 조회
     */
    List<ChatHistory> getChatHistoryByCompanyIdAndTimeRange(
            String companyId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
    
    /**
     * 채팅방 ID로 모든 이력 삭제
     */
    int deleteChatHistoryByRoomId(String roomId);
    
    /**
     * 오래된 이력 삭제 (특정 날짜 이전)
     */
    int deleteOldChatHistory(LocalDateTime beforeDate);
}
