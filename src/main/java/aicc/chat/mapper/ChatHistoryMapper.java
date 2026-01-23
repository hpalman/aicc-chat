package aicc.chat.mapper;

import aicc.chat.domain.persistence.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 이력 MyBatis Mapper 인터페이스
 */
@Mapper
public interface ChatHistoryMapper {
    
    /**
     * 채팅 메시지 저장
     * 
     * @param chatHistory 채팅 이력 객체
     * @return 저장된 레코드 수
     */
    int insertChatHistory(ChatHistory chatHistory);
    
    /**
     * 채팅 메시지 일괄 저장
     * 
     * @param chatHistories 채팅 이력 리스트
     * @return 저장된 레코드 수
     */
    int insertChatHistoryBatch(List<ChatHistory> chatHistories);
    
    /**
     * ID로 채팅 이력 조회
     * 
     * @param id 채팅 이력 ID
     * @return 채팅 이력 객체
     */
    ChatHistory selectChatHistoryById(@Param("id") Long id);
    
    /**
     * 채팅방 ID로 채팅 이력 조회 (시간순 정렬)
     * 
     * @param roomId 채팅방 ID
     * @return 채팅 이력 리스트
     */
    List<ChatHistory> selectChatHistoryByRoomId(@Param("roomId") String roomId);
    
    /**
     * 채팅방 ID와 시간 범위로 채팅 이력 조회
     * 
     * @param roomId 채팅방 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 채팅 이력 리스트
     */
    List<ChatHistory> selectChatHistoryByRoomIdAndTimeRange(
            @Param("roomId") String roomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 고객 ID로 채팅 이력 조회
     * 
     * @param senderId 발신자 ID (고객 ID)
     * @param senderRole 발신자 역할 (CUSTOMER)
     * @return 채팅 이력 리스트
     */
    List<ChatHistory> selectChatHistoryBySenderId(
            @Param("senderId") String senderId,
            @Param("senderRole") String senderRole
    );
    
    /**
     * 회사 ID와 시간 범위로 채팅 이력 조회
     * 
     * @param companyId 회사 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 채팅 이력 리스트
     */
    List<ChatHistory> selectChatHistoryByCompanyIdAndTimeRange(
            @Param("companyId") String companyId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 채팅 이력 수정
     * 
     * @param chatHistory 수정할 채팅 이력
     * @return 수정된 레코드 수
     */
    int updateChatHistory(ChatHistory chatHistory);
    
    /**
     * 채팅 이력 삭제
     * 
     * @param id 채팅 이력 ID
     * @return 삭제된 레코드 수
     */
    int deleteChatHistoryById(@Param("id") Long id);
    
    /**
     * 채팅방 ID로 모든 이력 삭제
     * 
     * @param roomId 채팅방 ID
     * @return 삭제된 레코드 수
     */
    int deleteChatHistoryByRoomId(@Param("roomId") String roomId);
    
    /**
     * 특정 날짜 이전의 오래된 이력 삭제
     * 
     * @param beforeDate 기준 날짜
     * @return 삭제된 레코드 수
     */
    int deleteOldChatHistory(@Param("beforeDate") LocalDateTime beforeDate);
}
