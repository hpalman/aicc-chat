package aicc.chat.config;

import aicc.bot.ChatBot;
import aicc.chat.service.ChatHistoryService;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.ChatSessionService;
import aicc.chat.service.MessageBroker;
import aicc.chat.service.RoomRepository;
import aicc.chat.service.impl.AgentRoutingStrategy;
import aicc.chat.service.impl.DynamicRoutingStrategy;
import aicc.chat.service.impl.MiChatRoutingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatRoutingConfig {

    /**
     * app.chat.mode 가 'HYBRID'일 때 활성화 (Bot -> Agent 전환 지원)
     * REDIS_ONLY 모드에서는 HYBRID 모드를 사용
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "HYBRID", matchIfMissing = true)
    public ChatRoutingStrategy dynamicRoutingStrategy(
            MessageBroker messageBroker, 
            ChatBot chatBot, 
            RoomRepository roomRepository,
            aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster,
            ChatHistoryService chatHistoryService,
            ChatSessionService chatSessionService) {
        
        MiChatRoutingStrategy miChat = new MiChatRoutingStrategy(
                messageBroker, chatBot, roomRepository, roomUpdateBroadcaster, 
                chatHistoryService, chatSessionService);
        AgentRoutingStrategy agent = new AgentRoutingStrategy(messageBroker);
        
        return new DynamicRoutingStrategy(roomRepository, miChat, agent, roomUpdateBroadcaster);
    }

    /**
     * app.chat.mode 가 'MICHAT'일 때 활성화
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "MICHAT")
    public ChatRoutingStrategy miChatRoutingStrategy(
            MessageBroker messageBroker, 
            ChatBot chatBot, 
            RoomRepository roomRepository,
            aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster,
            ChatHistoryService chatHistoryService,
            ChatSessionService chatSessionService) {
        return new MiChatRoutingStrategy(
                messageBroker, chatBot, roomRepository, roomUpdateBroadcaster,
                chatHistoryService, chatSessionService);
    }

    /**
     * app.chat.mode 가 'AGENT'일 때 활성화
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "AGENT")
    public ChatRoutingStrategy agentRoutingStrategy(MessageBroker messageBroker) {
        return new AgentRoutingStrategy(messageBroker);
    }
}

