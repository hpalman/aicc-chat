package aicc.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import aicc.chat.service.RoomRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomRepository roomRepository;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        log.info("WebSocket connected: {}", event.getMessage().getHeaders());
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String dest = sha.getDestination();
        String sessionId = sha.getSessionId();
        String user = null;
        if (sha.getSessionAttributes() != null) {
            Object o = sha.getSessionAttributes().get("userId"); 
            if (o != null) user = o.toString();
        }
        if (dest != null && dest.startsWith("/topic/room/")) {
            String roomId = dest.replace("/topic/room/", "");
            if (user != null) roomRepository.addMember(roomId, user);
            else roomRepository.addMember(roomId, sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        roomRepository.removeMemberFromAll(sessionId);
    }
}
