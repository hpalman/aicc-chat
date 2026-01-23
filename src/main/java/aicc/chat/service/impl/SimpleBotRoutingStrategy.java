package aicc.chat.service.impl;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 외부 엔진 없이 내부 로직으로 응답하는 단순 봇 전략 구현체
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleBotRoutingStrategy implements ChatRoutingStrategy {

    private final MessageBroker messageBroker;

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

        // 3. 사용자가 보낸 메시지인 경우 내부 로직으로 응답
        String userText = message.getMessage();
        String replyText = "안녕하세요? '" + userText + "'라고 말씀하셨나요? 현재 단순 봇 모드입니다.";
        
        ChatMessage botMessage = ChatMessage.builder()
                .roomId(roomId)
                .sender("Bot")
                .senderRole(UserRole.BOT)
                .message(replyText)
                .type(MessageType.TALK)
                .build();
        messageBroker.publish(botMessage);
    }

    @Override
    public void onRoomCreated(ChatRoom room) {
        ChatMessage welcome = ChatMessage.builder()
                .roomId(room.getRoomId())
                .sender("Bot")
                .senderRole(UserRole.BOT)
                .message("안녕하세요! 단순 상담 봇입니다. 무엇을 도와드릴까요?")
                .type(MessageType.TALK)
                .build();
        messageBroker.publish(welcome);
    }
}

