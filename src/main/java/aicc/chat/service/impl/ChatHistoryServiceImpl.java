package aicc.chat.service.impl;

import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.mapper.ChatHistoryMapper;
import aicc.chat.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채팅 이력 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {
    
    private final ChatHistoryMapper chatHistoryMapper;
    
    @Override
    @Transactional
    public void saveChatHistory(ChatHistory chatHistory) {
        try {
            chatHistoryMapper.insertChatHistory(chatHistory);
            log.debug("Chat history saved: roomId={}, sender={}, message={}", 
                    chatHistory.getRoomId(), chatHistory.getSenderName(), chatHistory.getMessage());
        } catch (Exception e) {
            log.error("Failed to save chat history: roomId={}", chatHistory.getRoomId(), e);
            throw new RuntimeException("채팅 이력 저장 실패", e);
        }
    }
    
    @Override
    @Transactional
    public void saveChatHistoryBatch(List<ChatHistory> chatHistories) {
        if (chatHistories == null || chatHistories.isEmpty()) {
            return;
        }
        
        try {
            chatHistoryMapper.insertChatHistoryBatch(chatHistories);
            log.debug("Batch chat history saved: count={}", chatHistories.size());
        } catch (Exception e) {
            log.error("Failed to save batch chat history: count={}", chatHistories.size(), e);
            throw new RuntimeException("채팅 이력 일괄 저장 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ChatHistory> getChatHistoryByRoomId(String roomId) {
        try {
            return chatHistoryMapper.selectChatHistoryByRoomId(roomId);
        } catch (Exception e) {
            log.error("Failed to get chat history by roomId: {}", roomId, e);
            throw new RuntimeException("채팅 이력 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ChatHistory> getChatHistoryByRoomIdAndTimeRange(
            String roomId, 
            LocalDateTime startTime, 
            LocalDateTime endTime) {
        try {
            return chatHistoryMapper.selectChatHistoryByRoomIdAndTimeRange(roomId, startTime, endTime);
        } catch (Exception e) {
            log.error("Failed to get chat history by roomId and time range: roomId={}", roomId, e);
            throw new RuntimeException("채팅 이력 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ChatHistory> getChatHistoryByCustomerId(String customerId) {
        try {
            return chatHistoryMapper.selectChatHistoryBySenderId(customerId, "CUSTOMER");
        } catch (Exception e) {
            log.error("Failed to get chat history by customerId: {}", customerId, e);
            throw new RuntimeException("고객 채팅 이력 조회 실패", e);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ChatHistory> getChatHistoryByCompanyIdAndTimeRange(
            String companyId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        try {
            return chatHistoryMapper.selectChatHistoryByCompanyIdAndTimeRange(companyId, startTime, endTime);
        } catch (Exception e) {
            log.error("Failed to get chat history by companyId and time range: companyId={}", companyId, e);
            throw new RuntimeException("회사별 채팅 이력 조회 실패", e);
        }
    }
    
    @Override
    @Transactional
    public int deleteChatHistoryByRoomId(String roomId) {
        try {
            int deletedCount = chatHistoryMapper.deleteChatHistoryByRoomId(roomId);
            log.info("Chat history deleted: roomId={}, count={}", roomId, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("Failed to delete chat history by roomId: {}", roomId, e);
            throw new RuntimeException("채팅 이력 삭제 실패", e);
        }
    }
    
    @Override
    @Transactional
    public int deleteOldChatHistory(LocalDateTime beforeDate) {
        try {
            int deletedCount = chatHistoryMapper.deleteOldChatHistory(beforeDate);
            log.info("Old chat history deleted: beforeDate={}, count={}", beforeDate, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("Failed to delete old chat history: beforeDate={}", beforeDate, e);
            throw new RuntimeException("오래된 채팅 이력 삭제 실패", e);
        }
    }
}
