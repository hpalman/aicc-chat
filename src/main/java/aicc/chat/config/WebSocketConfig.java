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
/**
 * STOMP 기반 WebSocket 설정.
 *
 * 클라이언트 연결:
 *  - WebSocket/STOMP endpoint: /ws-chat
 *  - SockJS fallback 가능하도록 설정.
 *
 * 메시지 라우팅:
 *  - 클라이언트 → 서버: /pub/** (application destination). 여기선 > topic
 *  - 서버 → 클라이언트: /sub/** (simple broker, Redis 기반 로직과 조합). 여기선> app
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenService tokenService;

    @Override
    // STOMP 브로커 및 애플리케이션 목적지 접두어 설정
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic"); // 서버에서 클라이언트로 broadcast 하는 prefix
        // ["SUBSCRIBE\nid:sub-0\ndestination:/topic/room/room-161cedaa\n\n\u0000"]
        
        
        registry.setApplicationDestinationPrefixes("/app"); // 서버로 보내는 메세지 prefix
        // ["SEND\ndestination:/app/customer/chat\ncontent-length:116\n\n{\"roomId\":\"room-161cedaa\",\"sender\":\"고객-5bf4\",\"type\":\"JOIN\",\"message\":\"고객-5bf4님이 입장하셨습니다.\"}\u0000"]
    }

    @Override
    // WebSocket/STOMP 엔드포인트 및 핸드셰이크 인터셉터 등록
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat") // 고객/상담사/관리자 공통 WebSocket endpoint
                .setAllowedOriginPatterns("*")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    // 핸드셰이크 시 토큰/roomId를 읽어 세션 속성에 저장
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
                    // 핸드셰이크 완료 후 별도 처리 없음
                    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                             @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
                    }
                })
                .withSockJS() // SockJS 사용
                .setSessionCookieNeeded(false);
    }

    @Override
    // 인바운드 채널 인터셉터를 추가할 때 사용 (현재 미사용)
    public void configureClientInboundChannel(ChannelRegistration registration) {
        //registration.interceptors(stompHandler);
    }



    // default void configureClientInboundChannel(ChannelRegistration registration) {
    // }
}
