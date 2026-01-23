package aicc.chat.mapper;

import aicc.chat.domain.persistence.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 세션 MyBatis Mapper 인터페이스
 */
@Mapper
public interface ChatSessionMapper {
    
    /**
     * 상담 세션 저장
     * 
     * @param chatSession 상담 세션 객체
     * @return 저장된 레코드 수
     */
    int insertChatSession(ChatSession chatSession);
    
    /**
     * ID로 상담 세션 조회
     * 
     * @param id 세션 ID
     * @return 상담 세션 객체
     */
    ChatSession selectChatSessionById(@Param("id") Long id);
    
    /**
     * 채팅방 ID로 상담 세션 조회
     * 
     * @param roomId 채팅방 ID
     * @return 상담 세션 객체
     */
    ChatSession selectChatSessionByRoomId(@Param("roomId") String roomId);
    
    /**
     * 고객 ID로 상담 세션 목록 조회
     * 
     * @param customerId 고객 ID
     * @return 상담 세션 리스트
     */
    List<ChatSession> selectChatSessionsByCustomerId(@Param("customerId") String customerId);
    
    /**
     * 상담원 이름으로 상담 세션 목록 조회
     * 
     * @param assignedAgent 상담원 이름
     * @return 상담 세션 리스트
     */
    List<ChatSession> selectChatSessionsByAgent(@Param("assignedAgent") String assignedAgent);
    
    /**
     * 회사 ID와 상태로 상담 세션 목록 조회
     * 
     * @param companyId 회사 ID
     * @param status 세션 상태
     * @return 상담 세션 리스트
     */
    List<ChatSession> selectChatSessionsByCompanyIdAndStatus(
            @Param("companyId") String companyId,
            @Param("status") String status
    );
    
    /**
     * 회사 ID와 시간 범위로 상담 세션 목록 조회
     * 
     * @param companyId 회사 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 상담 세션 리스트
     */
    List<ChatSession> selectChatSessionsByCompanyIdAndTimeRange(
            @Param("companyId") String companyId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 활성화된 상담 세션 목록 조회 (CLOSED 제외)
     * 
     * @return 활성 상담 세션 리스트
     */
    List<ChatSession> selectActiveChatSessions();
    
    /**
     * 상담 세션 정보 수정
     * 
     * @param chatSession 수정할 상담 세션
     * @return 수정된 레코드 수
     */
    int updateChatSession(ChatSession chatSession);
    
    /**
     * 상담 세션 상태 변경
     * 
     * @param roomId 채팅방 ID
     * @param status 새로운 상태
     * @return 수정된 레코드 수
     */
    int updateChatSessionStatus(
            @Param("roomId") String roomId,
            @Param("status") String status
    );
    
    /**
     * 상담원 배정
     * 
     * @param roomId 채팅방 ID
     * @param assignedAgent 배정된 상담원 이름
     * @return 수정된 레코드 수
     */
    int updateAssignedAgent(
            @Param("roomId") String roomId,
            @Param("assignedAgent") String assignedAgent
    );
    
    /**
     * 상담 종료 시간 설정
     * 
     * @param roomId 채팅방 ID
     * @param endedAt 종료 시간
     * @return 수정된 레코드 수
     */
    int updateEndedAt(
            @Param("roomId") String roomId,
            @Param("endedAt") LocalDateTime endedAt
    );
    
    /**
     * 마지막 활동 시간 갱신
     * 
     * @param roomId 채팅방 ID
     * @param lastActivityAt 마지막 활동 시간
     * @return 수정된 레코드 수
     */
    int updateLastActivityAt(
            @Param("roomId") String roomId,
            @Param("lastActivityAt") LocalDateTime lastActivityAt
    );
    
    /**
     * 상담 세션 삭제
     * 
     * @param id 세션 ID
     * @return 삭제된 레코드 수
     */
    int deleteChatSessionById(@Param("id") Long id);
    
    /**
     * 채팅방 ID로 상담 세션 삭제
     * 
     * @param roomId 채팅방 ID
     * @return 삭제된 레코드 수
     */
    int deleteChatSessionByRoomId(@Param("roomId") String roomId);
}
