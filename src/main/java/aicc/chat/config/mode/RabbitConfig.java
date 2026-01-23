package aicc.chat.config.mode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import aicc.chat.domain.ChatMessage;
import aicc.chat.service.MessageBroker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableRabbit
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.system-mode", havingValue = "REDIS_RABBIT")
public class RabbitConfig {

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${spring.rabbitmq.exchange}")
    private String exchangeName;

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange exchange() { 
        log.info("Creating TopicExchange: {}", exchangeName);
        return new TopicExchange(exchangeName); 
    }

    @Bean
    public Queue chatQueue() { 
        return new AnonymousQueue(); 
    }

    @Bean
    public Binding binding(Queue chatQueue, TopicExchange exchange) {
        return BindingBuilder.bind(chatQueue).to(exchange).with("room.*");
    }

    @Bean
    public MessageBroker messageBroker(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setMessageConverter(converter());
        log.info("MessageBroker initialized with exchange: {}", exchangeName);
        return message -> {
            try {
                rabbitTemplate.convertAndSend(exchangeName, "room." + message.getRoomId(), message);
                log.debug("Message sent to RabbitMQ: roomId={}", message.getRoomId());
            } catch (Exception e) {
                log.error("Failed to send message to RabbitMQ: {}", e.getMessage(), e);
            }
        };
    }

    @Bean
    public SimpleMessageListenerContainer messageListenerContainer(
            ConnectionFactory connectionFactory,
            Queue chatQueue) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueues(chatQueue);
        container.setMessageListener(message -> {
            try {
                ChatMessage chatMessage = (ChatMessage) converter().fromMessage(message);
                messagingTemplate.convertAndSend("/topic/room/" + chatMessage.getRoomId(), chatMessage);
                log.debug("Message received from RabbitMQ and sent to WebSocket: roomId={}", chatMessage.getRoomId());
            } catch (Exception e) {
                log.error("Rabbit Receive Error", e);
            }
        });
        log.info("RabbitMQ Listener Container initialized");
        return container;
    }
}
