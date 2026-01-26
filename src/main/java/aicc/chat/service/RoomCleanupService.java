package aicc.chat.service;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCleanupService {

    private final RoomRepository roomRepository;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final MessageBroker messageBroker;
    private final ChatHistoryService chatHistoryService;
    private final ChatSessionService chatSessionService;
    
    @Value("${app.chat.cleanup.idle-timeout:600000}")
    private long idleTimeout; // 유휴 타임아웃 (기본값: 10분)
    
    @Value("${app.chat.cleanup.check-interval:60000}")
    private long checkInterval; // 정리 작업 주기 (기본값: 1분)

    /**
     * 일정 시간 동안 활동이 없는 채팅방을 정리합니다.
     * 실행 주기는 application.yml의 app.chat.cleanup.check-interval로 설정됩니다.
     */
    @Scheduled(fixedRateString = "${app.chat.cleanup.check-interval:60000}")
    public void cleanupIdleRooms() {
        log.debug("Starting idle room cleanup task... (timeout: {}ms, interval: {}ms)", idleTimeout, checkInterval);
        List<ChatRoom> allRooms = roomRepository.findAllRooms();
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (ChatRoom room : allRooms) {
            long idleTime = now - room.getLastActivityAt();
            if (idleTime > idleTimeout) {
                log.info("Cleaning up idle room: {} (Idle for {} ms, timeout: {} ms)", 
                        room.getRoomId(), idleTime, idleTimeout);
                
                // 1. 고객에게 자동 종료 알림 메시지 전송
                notifyRoomTimeout(room);
                
                // 2. DB에 세션 종료 기록 (PostgreSQL)
                saveRoomTimeoutToDatabase(room);
                
                // 3. Redis에서 채팅방 삭제
                roomRepository.deleteRoom(room.getRoomId());
                
                changed = true;
            }
        }

        if (changed) {
            // 4. 상담원에게 채팅방 목록 업데이트 브로드캐스트
            roomUpdateBroadcaster.broadcastRoomList();
        }
    }
    
    /**
     * 타임아웃된 채팅방에 알림 메시지를 전송합니다.
     * 고객 측 WebSocket으로 LEAVE 메시지를 보내 자동 종료를 알립니다.
     */
    private void notifyRoomTimeout(ChatRoom room) {
        try {
            LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
            
            // 고객에게 전송할 타임아웃 알림 메시지 생성
            ChatMessage timeoutMessage = ChatMessage.builder()
                    .roomId(room.getRoomId())
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
                    .type(MessageType.LEAVE)
                    .companyId(null) // ChatRoom에는 companyId가 없으므로 null 처리
                    .timestamp(now) // 서버 타임스탬프 설정
                    .build();
            
            // WebSocket을 통해 고객에게 메시지 전송
            messageBroker.publish(timeoutMessage);
            
            log.info("Timeout notification sent to room: {}", room.getRoomId());
        } catch (Exception e) {
            log.error("Failed to send timeout notification for room: {}", room.getRoomId(), e);
        }
    }
    
    /**
     * 타임아웃된 채팅방 정보를 데이터베이스에 기록합니다.
     */
    private void saveRoomTimeoutToDatabase(ChatRoom room) {
        try {
            LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
            
            // 1. 채팅 세션 상태를 CLOSED로 업데이트
            chatSessionService.updateSessionStatus(room.getRoomId(), "CLOSED");
            
            // 2. 세션 종료 시간 기록
            chatSessionService.endSession(room.getRoomId());
            
            // 3. 채팅 이력에 타임아웃 메시지 저장
            ChatHistory timeoutHistory = ChatHistory.builder()
                    .roomId(room.getRoomId())
                    .senderId("system")
                    .senderName("System")
                    .senderRole("SYSTEM")
                    .message("장시간 대화가 없어 상담이 자동 종료되었습니다.")
                    .messageType("LEAVE")
                    .companyId(null) // ChatRoom에는 companyId가 없으므로 null 처리
                    .createdAt(now) // 서버 타임스탬프 사용
                    .build();
            
            chatHistoryService.saveChatHistory(timeoutHistory);
            
            log.info("Timeout record saved to database for room: {}", room.getRoomId());
        } catch (Exception e) {
            log.error("Failed to save timeout record to database for room: {}", room.getRoomId(), e);
            // DB 저장 실패는 로그만 남기고 계속 진행 (채팅방은 정리되어야 함)
        }
    }
}

