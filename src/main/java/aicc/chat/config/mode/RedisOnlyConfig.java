package aicc.chat.config.mode;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.chat.domain.ChatMessage;
import aicc.chat.service.inteface.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.system-mode", havingValue = "REDIS_ONLY")
public class RedisOnlyConfig {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    // Redis pub 채널로 메시지를 발행하는 MessageBroker 구현
    public MessageBroker messageBroker() {
        return message -> {
            try {
                String msg = objectMapper.writeValueAsString(message);
                redisTemplate.convertAndSend("chat.topic", msg);
            } catch (Exception e) {
                log.error("Redis Publish Error", e);
            }
        };
    }

    @Bean
    // Redis 구독을 처리할 리스너 컨테이너 구성
    public RedisMessageListenerContainer redisContainer(MessageListenerAdapter adapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.addMessageListener(adapter, new ChannelTopic("chat.topic"));
        return container;
    }

    @Bean
    // Redis 메시지를 STOMP 토픽으로 중계하는 리스너 어댑터
    public MessageListenerAdapter listenerAdapter() {
        return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
                messagingTemplate.convertAndSend("/topic/room/" + chatMessage.getRoomId(), chatMessage);
            } catch (Exception e) {
                log.error("Redis Subscribe Error", e);
            }
        });
    }
}
