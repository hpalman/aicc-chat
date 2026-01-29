package aicc.chat.service.impl;

import aicc.chat.domain.ChatMessage;
import aicc.chat.service.inteface.ChatRoutingStrategy;
import aicc.chat.service.inteface.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 봇 없이 상담원과 직접 연결되는 전략 구현체 (단순 중계)
 */
@Slf4j
@RequiredArgsConstructor
public class AgentRoutingStrategy implements ChatRoutingStrategy {

    private final MessageBroker messageBroker;

    @Override
    // 상담원 메시지를 중계하여 구독자에게 전파
    public void handleMessage(String roomId, ChatMessage message) {
        log.info("▼ Agent routing for room: {}", roomId);
        message.setRoomId(roomId);
        messageBroker.publish(message);
    }
}

