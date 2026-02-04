package aicc.chat.config.mode;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.chat.domain.ChatMessage;
import aicc.chat.dto.SchedulerControlMessage;
import aicc.chat.service.SchedulerControlService;
import aicc.chat.service.inteface.MessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;

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
                log.info("▷ chat.topic > msg:{}", msg);
                redisTemplate.convertAndSend("chat.topic", msg);
            } catch (Exception e) {
                log.error("Redis Publish Error", e);
            }
        };
    }

    @Bean
    // Redis 구독을 처리할 리스너 컨테이너 구성
    public RedisMessageListenerContainer redisContainer(
            MessageListenerAdapter listenerAdapter,
            @Autowired(required = false) MessageListenerAdapter schedulerListenerAdapter) {
        log.info("▼ redisContainer Redis 구독을 처리할 리스너 컨테이너 구성 S.");
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.addMessageListener(listenerAdapter, new ChannelTopic("chat.topic"));

        // 스케줄러 제어 채널 리스너 추가 (cleanup.enabled=true일 때만)
        if (schedulerListenerAdapter != null) {
            log.info("▶ scheduler.control addMessageListener");
            container.addMessageListener(schedulerListenerAdapter, new ChannelTopic("scheduler.control"));
        } else {
            log.info("▶ schedulerListenerAdapter == null");
        }

        log.info("▲ redisContainer Redis 구독을 처리할 리스너 컨테이너 구성 E.");
        return container;
    }

    @Bean
    // Redis 메시지를 STOMP 토픽으로 중계하는 리스너 어댑터
    public MessageListenerAdapter listenerAdapter() {
        log.info("▶ listenerAdapter.");
        return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);

                // 시스템 브로드캐스트 메시지 처리 (상담원 로그인/로그아웃 알림)
                if ("SYSTEM_BROADCAST".equals(chatMessage.getRoomId())) {
                    log.info("System broadcast message received: {}", chatMessage.getMessage());

                    // 상담원 가용 여부 결정
                    boolean available = "AGENT_STATUS".equals(chatMessage.getMessage());

                    Map<String, Object> map = Map.of(
                        "available", available,
                        "message", chatMessage.getMessage(),
                        "timestamp", System.currentTimeMillis()
                    );
                    messagingTemplate.convertAndSend("/topic/agent-availability", map);
                } else {
                    // 일반 채팅 메시지는 해당 방으로 전송
                    messagingTemplate.convertAndSend("/topic/room/" + chatMessage.getRoomId(), chatMessage);
                }
            } catch (Exception e) {
                log.error("Redis Subscribe Error", e);
            }
        });
    }

    @Bean
    // 스케줄러 제어 메시지를 처리하는 리스너 어댑터
    @ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    public MessageListenerAdapter schedulerListenerAdapter(SchedulerControlService schedulerControlService) {
        return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                log.info("▶ Scheduler control message received: {}", body);

                SchedulerControlMessage controlMessage = objectMapper.readValue(body, SchedulerControlMessage.class);
                schedulerControlService.handleControlMessage(controlMessage);
            } catch (Exception e) {
                log.error("Redis Scheduler Control Subscribe Error", e);
            }
        });
    }
}
