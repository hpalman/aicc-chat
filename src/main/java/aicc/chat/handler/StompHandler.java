package aicc.chat.handler;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

// @Component
public class StompHandler { /* implements ChannelInterceptor
    private final JwtTokenProvider jwtTokenProvider;

    public StompHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }


    // 서버 검증 코드 (Spring Boot)
    // StompHandler에서 CONNECT 명령 시 토큰을 검증합니다.

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                Map<String, Object> claims = jwtTokenProvider.validateToken(token);
                Long userId = Long.parseLong(claims.get("userId").toString());
                accessor.setUser(new Principal() {
                    public String getName() { return userId.toString(); }
                });
            }
        }
        return message;
    } */
}
