package aicc.chat.service.inteface;

import aicc.chat.domain.ChatMessage;
import aicc.chat.service.ChannelName;

public interface MessageBroker {
    /**
     * 채팅 메시지를 기본 채널("chat.topic")으로 발행
     *
     * @param message 채팅 메시지
     */
    void publish(ChatMessage message);

    /**
     * 지정된 토픽으로 객체를 발행
     *
     * @param channelName Redis Pub/Sub 채널 이름
     * @param message 발행할 객체 (모든 타입 가능)
     * @param <T> 메시지 객체 타입
     */
    <T> void publish(ChannelName channelName, T message);
}
