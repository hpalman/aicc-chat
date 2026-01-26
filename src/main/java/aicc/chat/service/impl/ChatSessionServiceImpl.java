package aicc.chat.service.impl;

import aicc.chat.domain.persistence.ChatSession;
import aicc.chat.mapper.ChatSessionMapper;
import aicc.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담 세션 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {
    
    private final ChatSessionMapper chatSessionMapper;
    
    @Override
    @Transactional
    // 상담 세션 생성
    public void createChatSession(ChatSession chatSession) {
        try {
            chatSessionMapper.insertChatSession(chatSession);
            log.info("Chat session created: roomId={}, customerId={}", 
                    chatSession.getRoomId(), chatSession.getCustomerId());
        } catch (Exception e) {
            log.error("Failed to create chat session: roomId={}", chatSession.getRoomId(), e);
            throw new RuntimeException("상담 세션 생성 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    // roomId로 상담 세션 조회
    public ChatSession getChatSessionByRoomId(String roomId) {
        try {
            return chatSessionMapper.selectChatSessionByRoomId(roomId);
        } catch (Exception e) {
            log.error("Failed to get chat session by roomId: {}", roomId, e);
            throw new RuntimeException("상담 세션 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    // 고객 ID로 상담 세션 목록 조회
    public List<ChatSession> getChatSessionsByCustomerId(String customerId) {
        try {
            return chatSessionMapper.selectChatSessionsByCustomerId(customerId);
        } catch (Exception e) {
            log.error("Failed to get chat sessions by customerId: {}", customerId, e);
            throw new RuntimeException("고객별 상담 세션 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    // 상담원 이름으로 상담 세션 목록 조회
    public List<ChatSession> getChatSessionsByAgent(String assignedAgent) {
        try {
            return chatSessionMapper.selectChatSessionsByAgent(assignedAgent);
        } catch (Exception e) {
            log.error("Failed to get chat sessions by agent: {}", assignedAgent, e);
            throw new RuntimeException("상담원별 상담 세션 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    // 회사 ID와 상태로 상담 세션 목록 조회
    public List<ChatSession> getChatSessionsByCompanyIdAndStatus(String companyId, String status) {
        try {
            return chatSessionMapper.selectChatSessionsByCompanyIdAndStatus(companyId, status);
        } catch (Exception e) {
            log.error("Failed to get chat sessions by companyId and status: companyId={}, status={}", 
                    companyId, status, e);
            throw new RuntimeException("회사별 상담 세션 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    // 활성 상담 세션 목록 조회
    public List<ChatSession> getActiveChatSessions() {
        try {
            return chatSessionMapper.selectActiveChatSessions();
        } catch (Exception e) {
            log.error("Failed to get active chat sessions", e);
            throw new RuntimeException("활성 상담 세션 조회 실패", e);
        }
    }
    
    @Override
    @Transactional
    // 상담 세션 상태 변경
    public void updateSessionStatus(String roomId, String status) {
        try {
            chatSessionMapper.updateChatSessionStatus(roomId, status);
            log.info("Chat session status updated: roomId={}, status={}", roomId, status);
        } catch (Exception e) {
            log.error("Failed to update session status: roomId={}, status={}", roomId, status, e);
            throw new RuntimeException("상담 세션 상태 변경 실패", e);
        }
    }
    
    @Override
    @Transactional
    // 상담원 배정 정보 업데이트
    public void assignAgent(String roomId, String assignedAgent) {
        try {
            chatSessionMapper.updateAssignedAgent(roomId, assignedAgent);
            log.info("Agent assigned to session: roomId={}, agent={}", roomId, assignedAgent);
        } catch (Exception e) {
            log.error("Failed to assign agent: roomId={}, agent={}", roomId, assignedAgent, e);
            throw new RuntimeException("상담원 배정 실패", e);
        }
    }
    
    @Override
    @Transactional
    // 상담 종료 시간 기록
    public void endSession(String roomId) {
        try {
            chatSessionMapper.updateEndedAt(roomId, LocalDateTime.now());
            log.info("Chat session ended: roomId={}", roomId);
        } catch (Exception e) {
            log.error("Failed to end session: roomId={}", roomId, e);
            throw new RuntimeException("상담 종료 실패", e);
        }
    }
    
    @Override
    @Transactional
    // 마지막 활동 시간 갱신 - DB
    public void updateLastActivity(String roomId) {
        try {
            chatSessionMapper.updateLastActivityAt(roomId, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to update last activity: roomId={}", roomId, e);
            // 마지막 활동 시간 업데이트 실패는 치명적이지 않으므로 예외를 던지지 않음
        }
    }
}
