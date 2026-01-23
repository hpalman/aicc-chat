package aicc.chat.service;

import aicc.chat.domain.ChatMessage;

public interface MessageBroker {
    void publish(ChatMessage message);
}
