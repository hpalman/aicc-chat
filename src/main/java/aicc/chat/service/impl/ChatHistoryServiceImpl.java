package aicc.chat.service.impl;

import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.mapper.ChatHistoryMapper;
import aicc.chat.service.inteface.ChatHistoryService;
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
    // 단건 채팅 이력 저장
    public void saveChatHistory(ChatHistory chatHistory) {
        log.info("▼ saveChatHistory");
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
    // 다건 채팅 이력 저장
    public void saveChatHistoryBatch(List<ChatHistory> chatHistories) {
        log.info("▼ saveChatHistoryBatch");
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
    // roomId로 채팅 이력 조회
    public List<ChatHistory> getChatHistoryByRoomId(String roomId) {
        log.info("▼ getChatHistoryByRoomId");
        try {
            return chatHistoryMapper.selectChatHistoryByRoomId(roomId);
        } catch (Exception e) {
            log.error("Failed to get chat history by roomId: {}", roomId, e);
            throw new RuntimeException("채팅 이력 조회 실패", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    // roomId와 시간 범위로 채팅 이력 조회
    public List<ChatHistory> getChatHistoryByRoomIdAndTimeRange(
            String roomId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        log.info("▼ getChatHistoryByRoomIdAndTimeRange");
        try {
            return chatHistoryMapper.selectChatHistoryByRoomIdAndTimeRange(roomId, startTime, endTime);
        } catch (Exception e) {
            log.error("Failed to get chat history by roomId and time range: roomId={}", roomId, e);
            throw new RuntimeException("채팅 이력 조회 실패", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    // 고객 ID 기준 채팅 이력 조회
    public List<ChatHistory> getChatHistoryByCustomerId(String customerId) {
        log.info("▼ getChatHistoryByCustomerId");
        try {
            return chatHistoryMapper.selectChatHistoryBySenderId(customerId, "CUSTOMER");
        } catch (Exception e) {
            log.error("Failed to get chat history by customerId: {}", customerId, e);
            throw new RuntimeException("고객 채팅 이력 조회 실패", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    // 회사 ID와 시간 범위로 채팅 이력 조회
    public List<ChatHistory> getChatHistoryByCompanyIdAndTimeRange(
            String companyId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        log.info("▼ getChatHistoryByCompanyIdAndTimeRange");
        try {
            return chatHistoryMapper.selectChatHistoryByCompanyIdAndTimeRange(companyId, startTime, endTime);
        } catch (Exception e) {
            log.error("Failed to get chat history by companyId and time range: companyId={}", companyId, e);
            throw new RuntimeException("회사별 채팅 이력 조회 실패", e);
        }
    }

    @Override
    @Transactional
    // roomId 기준 채팅 이력 삭제
    public int deleteChatHistoryByRoomId(String roomId) {
        log.info("▼ deleteChatHistoryByRoomId");
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
    // 특정 날짜 이전의 채팅 이력 삭제
    public int deleteOldChatHistory(LocalDateTime beforeDate) {
        log.info("▼ deleteOldChatHistory");
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
