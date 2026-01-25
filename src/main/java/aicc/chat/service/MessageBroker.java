package aicc.chat.service;

import aicc.chat.domain.ChatMessage;

public interface MessageBroker {
    // 채팅 메시지를 브로커로 발행
    void publish(ChatMessage message);
}
