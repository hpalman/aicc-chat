package aicc.chat.service.impl;

import aicc.bot.ChatBot;
import aicc.bot.dto.ChatBotRequest;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.persistence.ChatHistory;
import aicc.chat.service.ChatHistoryService;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.ChatSessionService;
import aicc.chat.service.MessageBroker;
import aicc.chat.service.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * MiChat(자체 AI 엔진)을 통해 대화를 처리하는 전략 구현체
 */
@Slf4j
@RequiredArgsConstructor
public class MiChatRoutingStrategy implements ChatRoutingStrategy {

    private final MessageBroker messageBroker;
    private final ChatBot chatBot;
    private final RoomRepository roomRepository;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;
    private final ChatHistoryService chatHistoryService;
    private final ChatSessionService chatSessionService;

    @Override
    // 고객 메시지를 MiChat으로 전달하고 응답을 브로드캐스트
    public void handleMessage(String roomId, ChatMessage message) {
        // 1. 수신된 메시지를 해당 방의 모든 구독자에게 전파
        message.setRoomId(roomId);
        messageBroker.publish(message);

        // 2. 상담원 연결 요청(HANDOFF)인 경우
        if (MessageType.HANDOFF.equals(message.getType())) {
            switchToAgentMode(roomId);
            return;
        }

        // 2-1. 상담원 연결 요청 취소(CANCEL_HANDOFF)인 경우
        if (MessageType.CANCEL_HANDOFF.equals(message.getType())) {
            cancelAgentMode(roomId);
            return;
        }

        // 3. TALK 타입이 아닌 경우 반환
        if (!MessageType.TALK.equals(message.getType())) {
            log.warn("Skipping AI processing for message type: {}", message.getType());
            return;
        }

        // 4. 고객(CUSTOMER)이 보낸 메시지가 아닌 경우 반환
        if (!UserRole.CUSTOMER.equals(message.getSenderRole())) {
            log.debug("Skipping AI processing for non-customer role: {}", message.getSenderRole());
            return;
        }

        // 5. 사용자가 보낸 메시지인 경우 MiChat으로 전달하여 응답 요청
        log.info("Forwarding customer message to MiChat for room: {}", roomId);
        
        ChatBotRequest request = ChatBotRequest.builder()
                .sessionId(roomId)
                .message(message.getMessage())
                .companyId(message.getCompanyId())
                .userId(message.getSender())
                .build();

        // 스트리밍 응답을 누적하기 위한 StringBuilder
        StringBuilder fullResponse = new StringBuilder();
        
        chatBot.ask(request, 
            chunk -> {
                // 수신된 청크를 누적
                fullResponse.append(chunk);
            }, 
            () -> {
                // 스트림 완료 시 누적된 전체 메시지를 발송
                String responseText = fullResponse.toString();
                if (!responseText.isEmpty()) {
                    LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
                    
                    ChatMessage botMessage = ChatMessage.builder()
                            .roomId(roomId)
                            .sender("Bot")
                            .senderRole(UserRole.BOT)
                            .message(responseText)
                            .type(MessageType.TALK)
                            .timestamp(now) // 서버 타임스탬프 설정
                            .build();
                    messageBroker.publish(botMessage);
                    
                    // PostgreSQL에 BOT 응답 저장
                    try {
                        ChatHistory chatHistory = ChatHistory.builder()
                                .roomId(roomId)
                                .senderId("BOT")
                                .senderName("Bot")
                                .senderRole("BOT")
                                .message(responseText)
                                .messageType("TALK")
                                .companyId(message.getCompanyId())
                                .createdAt(now) // 서버 타임스탬프 사용
                                .build();
                        chatHistoryService.saveChatHistory(chatHistory);
                        
                        // 세션 마지막 활동 시간 업데이트
                        chatSessionService.updateLastActivity(roomId);
                    } catch (Exception e) {
                        log.error("Failed to save bot message to DB: roomId={}", roomId, e);
                        // DB 저장 실패해도 채팅은 계속 진행
                    }
                }
            }
        );
    }

    @Override
    // 방 생성 시 환영 메시지 전송 및 이력 저장
    public void onRoomCreated(ChatRoom room) {
        log.info("New room created for MiChat workflow: {}", room.getRoomId());
        
        LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
        
        ChatMessage welcome = ChatMessage.builder()
                .roomId(room.getRoomId())
                .sender("Bot")
                .senderRole(UserRole.BOT)
                .message("안녕하세요! 무엇을 도와드릴까요? '상담원 연결'을 입력하시면 상담원과 연결해 드립니다.")
                .type(MessageType.TALK)
                .timestamp(now) // 서버 타임스탬프 설정
                .build();
        messageBroker.publish(welcome);
        
        // PostgreSQL에 환영 메시지 저장
        try {
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(room.getRoomId())
                    .senderId("BOT")
                    .senderName("Bot")
                    .senderRole("BOT")
                    .message(welcome.getMessage())
                    .messageType("TALK")
                    .createdAt(now) // 서버 타임스탬프 사용
                    .build();
            chatHistoryService.saveChatHistory(chatHistory);
        } catch (Exception e) {
            log.error("Failed to save welcome message to DB: roomId={}", room.getRoomId(), e);
            // DB 저장 실패해도 채팅은 계속 진행
        }
    }

    private void switchToAgentMode(String roomId) {
        // 상담원 연결 요청 처리: WAITING 전환 및 알림 발송
        log.info("Switching room {} to WAITING mode", roomId);
        roomRepository.setRoutingMode(roomId, "WAITING");
        roomUpdateBroadcaster.broadcastRoomList(); // 상담원 대기 상태 알림
        
        LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
        
        // 고객 화면에 "상담원 연결 중..." 알림 (서버 사이드 발송)
        ChatMessage notice = ChatMessage.builder()
                .roomId(roomId)
                .sender("System")
                .senderRole(UserRole.BOT)
                .message("상담원 연결을 요청하였습니다. 상담원이 연결될 때까지 잠시만 기다려 주세요.")
                .type(MessageType.TALK)
                .timestamp(now) // 서버 타임스탬프 설정
                .build();
        messageBroker.publish(notice);
        
        // PostgreSQL에 세션 상태 업데이트 및 시스템 메시지 저장
        try {
            chatSessionService.updateSessionStatus(roomId, "WAITING");
            
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(roomId)
                    .senderId("SYSTEM")
                    .senderName("System")
                    .senderRole("SYSTEM")
                    .message(notice.getMessage())
                    .messageType("TALK")
                    .createdAt(now) // 서버 타임스탬프 사용
                    .build();
            chatHistoryService.saveChatHistory(chatHistory);
        } catch (Exception e) {
            log.error("Failed to save handoff message to DB: roomId={}", roomId, e);
        }
    }

    private void cancelAgentMode(String roomId) {
        // 상담원 연결 요청 취소 처리: BOT 복귀 및 알림 발송
        log.info("Canceling agent request for room {}, switching back to BOT mode", roomId);
        roomRepository.setRoutingMode(roomId, "BOT");
        roomUpdateBroadcaster.broadcastRoomList();
        
        LocalDateTime now = LocalDateTime.now(); // 서버 타임스탬프
        
        ChatMessage notice = ChatMessage.builder()
                .roomId(roomId)
                .sender("System")
                .senderRole(UserRole.BOT)
                .message("상담원 연결 요청을 취소하였습니다. 다시 챗봇이 도와드리겠습니다.")
                .type(MessageType.TALK)
                .timestamp(now) // 서버 타임스탬프 설정
                .build();
        messageBroker.publish(notice);
        
        // PostgreSQL에 세션 상태 업데이트 및 시스템 메시지 저장
        try {
            chatSessionService.updateSessionStatus(roomId, "BOT");
            
            ChatHistory chatHistory = ChatHistory.builder()
                    .roomId(roomId)
                    .senderId("SYSTEM")
                    .senderName("System")
                    .senderRole("SYSTEM")
                    .message(notice.getMessage())
                    .messageType("TALK")
                    .createdAt(now) // 서버 타임스탬프 사용
                    .build();
            chatHistoryService.saveChatHistory(chatHistory);
        } catch (Exception e) {
            log.error("Failed to save cancel handoff message to DB: roomId={}", roomId, e);
        }
    }
}

