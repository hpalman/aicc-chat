package aicc.chat.service.impl;

import aicc.bot.botpress.BotpressService;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Botpress를 통해 대화 워크플로우를 처리하는 전략 구현체
 */
@Slf4j
@RequiredArgsConstructor
public class BotpressRoutingStrategy implements ChatRoutingStrategy {

    private final MessageBroker messageBroker;
    private final BotpressService botpressService;

    @Override
    public void handleMessage(String roomId, ChatMessage message) {
        // 1. 수신된 메시지를 해당 방의 모든 구독자에게 전파
        message.setRoomId(roomId);
        messageBroker.publish(message);

        // 2. TALK 타입이 아니거나 고객(CUSTOMER)이 보낸 메시지가 아닌 경우 반환
        if (!MessageType.TALK.equals(message.getType())) {
            return;
        }
        if (!UserRole.CUSTOMER.equals(message.getSenderRole())) {
            return;
        }

        // 3. 사용자가 보낸 메시지인 경우 Botpress로 전달하여 응답 요청
        log.info("Forwarding message to Botpress for room: {}", roomId);
        botpressService.sendMessage(roomId, message.getMessage(), replyText -> {
            // Botpress 응답을 채팅방에 게시
            ChatMessage botMessage = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("Bot")
                    .senderRole(UserRole.BOT)
                    .message(replyText)
                    .type(MessageType.TALK)
                    .build();
            messageBroker.publish(botMessage);
        });
    }

    @Override
    public void onRoomCreated(ChatRoom room) {
        log.info("New room created for Botpress workflow: {}", room.getRoomId());
        // 필요 시 봇의 초기 인사말을 보낼 수도 있음
    }
}

