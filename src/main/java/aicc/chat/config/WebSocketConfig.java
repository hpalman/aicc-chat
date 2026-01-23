package aicc.chat.config;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenService tokenService;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                                 @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest) {
                            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
                            String token = servletRequest.getParameter("token");
                            String roomId = servletRequest.getParameter("roomId"); // roomId 파라미터 추가
                            if (token != null) {
                                UserInfo userInfo = tokenService.validateToken(token);
                                if (userInfo != null) {
                                    log.debug("WebSocket Handshake - User: {}, RequestRoom: {}, Company: {}", 
                                        userInfo.getUserId(), roomId, userInfo.getCompanyId());
                                    
                                    attributes.put("userId", userInfo.getUserId());
                                    attributes.put("userName", userInfo.getUserName());
                                    attributes.put("userRole", userInfo.getRole());
                                    if (userInfo.getEmail() != null) attributes.put("userEmail", userInfo.getEmail());
                                    
                                    // 1. 토큰에 roomId가 있으면 사용 (주로 상담원 등 고정된 경우)
                                    // 2. 파라미터로 roomId가 전달되면 사용 (주로 고객이 상담 시작 시 생성한 경우)
                                    String finalRoomId = (userInfo.getRoomId() != null) ? userInfo.getRoomId() : roomId;
                                    if (finalRoomId != null) attributes.put("roomId", finalRoomId);
                                    
                                    if (userInfo.getCompanyId() != null) attributes.put("companyId", userInfo.getCompanyId());
                                }
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                             @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
                    }
                })
                .withSockJS()
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        //registration.interceptors(stompHandler);
    }



    // default void configureClientInboundChannel(ChannelRegistration registration) {
    // }
}
