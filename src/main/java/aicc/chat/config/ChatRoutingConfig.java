package aicc.chat.config;

import aicc.bot.ChatBot;
import aicc.bot.botpress.BotpressService;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.MessageBroker;
import aicc.chat.service.RoomRepository;
import aicc.chat.service.impl.AgentRoutingStrategy;
import aicc.chat.service.impl.BotpressRoutingStrategy;
import aicc.chat.service.impl.DynamicRoutingStrategy;
import aicc.chat.service.impl.MiChatRoutingStrategy;
import aicc.chat.service.impl.SimpleBotRoutingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatRoutingConfig {

    /**
     * app.chat.mode 가 'HYBRID'일 때 활성화 (Bot -> Agent 전환 지원)
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "HYBRID")
    public ChatRoutingStrategy dynamicRoutingStrategy(
            MessageBroker messageBroker, 
            ChatBot chatBot, 
            RoomRepository roomRepository,
            aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster) {
        
        MiChatRoutingStrategy miChat = new MiChatRoutingStrategy(messageBroker, chatBot, roomRepository, roomUpdateBroadcaster);
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
            aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster) {
        return new MiChatRoutingStrategy(messageBroker, chatBot, roomRepository, roomUpdateBroadcaster);
    }

    /**
     * app.chat.mode 가 'BOTPRESS'일 때 활성화 (기본값)
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "BOTPRESS", matchIfMissing = true)
    public ChatRoutingStrategy botpressRoutingStrategy(MessageBroker messageBroker, BotpressService botpressService) {
        return new BotpressRoutingStrategy(messageBroker, botpressService);
    }

    /**
     * app.chat.mode 가 'SIMPLE_BOT'일 때 활성화
     */
    @Bean
    @ConditionalOnProperty(name = "app.chat.mode", havingValue = "SIMPLE_BOT")
    public ChatRoutingStrategy simpleBotRoutingStrategy(MessageBroker messageBroker) {
        return new SimpleBotRoutingStrategy(messageBroker);
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

