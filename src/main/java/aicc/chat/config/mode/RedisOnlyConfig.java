package aicc.chat.config.mode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.domain.msg.PubMessage;
import aicc.chat.dto.SchedulerControlMessage;
import aicc.chat.service.ChannelName;
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
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.system-mode", havingValue = "REDIS_ONLY")
public class RedisOnlyConfig {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // Redis pub 채널로 메시지를 발행하는 MessageBroker 구현
    @Bean
    public MessageBroker messageBroker() {
        return new MessageBroker() {
            @Override
            public void publish(ChatMessage _message) {
                // 기본 토픽으로 ChatMessage 발행

                PubMessage message = new PubMessage("CHAT", _message);
                publish(ChannelName.CHAT, message);
            }

            // 객체를 JSON으로 직렬화
            private String fromMessage(Object _message) throws JsonProcessingException {
                if (_message instanceof PubMessage) {
                    PubMessage pubMessage = (PubMessage) _message;
                    ChatMessage chatMessage = (ChatMessage)pubMessage.getChatMessage();
                    if ( chatMessage != null) {
                        chatMessage.setTimestamp(LocalDateTime.now());
                    }
                    return objectMapper.writeValueAsString(pubMessage);
                } else if (_message instanceof ChatMessage) {
                    ChatMessage chatMessage = (ChatMessage)_message;
                    chatMessage.setTimestamp(LocalDateTime.now());
                    return objectMapper.writeValueAsString(chatMessage);
                }
                return "";
            }

            /**
             * 레디스 채널에 메시지 PUBLISH
             * @param <T>
             * @param channel
             * @param message
             */
            @SuppressWarnings("null")
            @Override
            public <T> void publish(ChannelName channel, T _message) {
                try {
                    String message = fromMessage(_message);

                    String channelName = channel.getValue();

                    log.info("▷ {} > msg:{}", channel.getValue(), message);

                    // Redis Pub/Sub으로 발행
                    redisTemplate.convertAndSend(channelName, message);
                } catch (Exception e) {
                    log.error("Redis Publish Error - topic: {}, message: {}", channel.getValue(), _message, e);
                }
            }
        };
    }

    //@Bean
    //public MessageBroker messageBroker(/* ObjectMapper objectMapper, RedisTemplate<String, String> redisTemplate */) {
    //    return (topic, message) -> {
    //        try {
    //            // ChatMessage라면 timestamp 설정
    //            if (message instanceof ChatMessage chatMessage) {
    //                chatMessage.setTimestamp(LocalDateTime.now());
    //            }
    //
    //            // JSON 직렬화
    //            String msg = objectMapper.writeValueAsString(message);
    //
    //            // 로그 출력
    //            log.info("▷ {} > msg:{}", topic.getValue(), msg);
    //
    //            // Redis Pub/Sub 발행
    //            redisTemplate.convertAndSend(topic.getValue(), msg);
    //        } catch (Exception e) {
    //            log.error("Redis Publish Error", e);
    //        }
    //    };
    //}

    @SuppressWarnings({ "null" })
    @Bean
    // Redis 구독을 처리할 리스너 컨테이너 구성
    public RedisMessageListenerContainer redisContainer(
            @NonNull MessageListenerAdapter listenerAdapter,
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

    // redis-cli PUBLISH chat.topic '{"roomId":"SYSTEM_BROADCAST","sender":"System","senderRole":"SYSTEM","message":"공지사항입니다.","type":"TALK","companyId":"apt001"}'
    @Bean
    // Redis 메시지를 STOMP 토픽으로 중계하는 리스너 어댑터
    // Redis의 ChannelTopic("chat.topic")을 구독중
    // messageBroker.publish는 레디스 "chat.topic" 토픽에 메시지 발행 후 발행된 것은 다시 여기서 수신해서 연결된 모든 STOMP endpoint로 메시지 보냄
    MessageListenerAdapter listenerAdapter() {
        log.info("▶ listenerAdapter.");
        return new MessageListenerAdapter((MessageListener) (_message, pattern) -> {
            try {
                PubMessage  pubMessage;
                ChatMessage chatMessage;

                // body와 channel 추출
                String body    = new String(_message.getBody()   , StandardCharsets.UTF_8);
                String channel = new String(_message.getChannel(), StandardCharsets.UTF_8);

                // JSON 파싱
                JsonNode node = objectMapper.readTree(body);

                String msgName = null;
                // msgName 필드 확인 후 클래스 매핑
                if ( node.has("msgName") ) {
                    msgName = node.get("msgName").asText();
                }
                log.info("msgName:{}", msgName);
                if ( "TOPIC_ROOMS".equals(msgName) ) {
                    pubMessage = objectMapper.treeToValue(node, PubMessage.class);
                    //chatMessage = pubMessage.getChatMessage();
                    List<ChatRoom> rooms = pubMessage.getRooms();

                    messagingTemplate.convertAndSend("/topic/rooms", rooms);
                    return;
                }
                else if ( "CHAT".equals(msgName) ) {
                    pubMessage = objectMapper.treeToValue(node, PubMessage.class);
                    chatMessage = pubMessage.getChatMessage();
                } else {
                    chatMessage = ChatMessage.builder().build();
                }
                if ( chatMessage == null ) {
                    log.warn("뭐여?");
                }
                /*
                {
                    "msgName" : "CHAT",
                    "targetTopic" : null,
                    "message" : {
                      "roomId" : "SYSTEM_BROADCAST",
                      "senderId" : null,
                      "sender" : "System",
                      "senderRole" : "SYSTEM",
                      "message" : "AGENT_STATUS",
                      "type" : "SYSTEM",
                      "companyId" : null,
                      "timestamp" : "2026-02-06T17:53:46.5374245",
                      "targetTopic" : null
                    }
                  }
                */

                // message
                //   - ChatMessage class
                //   - Map class
                //   - SchedulerControlMessage class

                // ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
//String channel = new String( message.getChannel() );
//log.info("channel:{}", channel); // chat.topic

                String targetTopic = chatMessage.getTargetTopic();
                if ( null != targetTopic ) {
                    // redis-cli PUBLISH chat.topic '{"roomId":"SYSTEM_BROADCAST","sender":"System","senderRole":"SYSTEM","message":"공지사항입니다.","type":"TALK","companyId":"apt001", "targetTopic":"/topic/customerwaiting" }'
                    messagingTemplate.convertAndSend(targetTopic, chatMessage);
                } else if ("SYSTEM_BROADCAST".equals(chatMessage.getRoomId())) { // 시스템 브로드캐스트 메시지 처리 (상담원 로그인/로그아웃 알림)
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

                // messagingTemplate.convertAndSend("/topic/rooms", rooms);
            } catch (Exception e) {
                log.error("Redis Subscribe Error", e);
            }
        });
    }

    @Bean
    // 스케줄러 제어 메시지를 처리하는 리스너 어댑터
    @ConditionalOnProperty(name = "app.chat.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    /* public */ MessageListenerAdapter schedulerListenerAdapter(SchedulerControlService schedulerControlService) {
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
