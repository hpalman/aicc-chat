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

    
    /*
    ㅁ WebSocket/STOMP 이벤트 종류
    이벤트 클래스           설명                                       사용 예시
    ----------------------  ------------------------------------------- --------------------------
    SessionConnectEvent     클라이언트가 STOMP 연결을 시도할 때 발생   연결 요청 로깅, 인증 처리
    SessionConnectedEvent   STOMP 연결이 성공적으로 완료되었을 때 발생 사용자 접속 상태 관리
    SessionDisconnectEvent  클라이언트가 연결을 끊을 때 발생           접속 종료 처리, 리소스 정리
    SessionSubscribeEvent   클라이언트가 특정 토픽을 구독할 때 발생    채팅방 참여 추적, 알림 등록
    SessionUnsubscribeEvent 클라이언트가 구독을 해제할 때 발생         채팅방 탈퇴 추적, 알림 해제
   */
    
    // 연결 시도
    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        log.info("ㅁㅁㅁ WebSocket connected: {}", event.getMessage().getHeaders());
        // WebSocket connected: {simpMessageType=CONNECT_ACK, simpConnectMessage=GenericMessage [payload=byte[0], headers={simpMessageType=CONNECT, stompCommand=CONNECT, nativeHeaders={accept-version=[1.1,1.0], heart-beat=[10000,10000]}, simpSessionAttributes={userName=고객-358d, userId=hong, roomId=room-00e386c1, companyId=apt001, userEmail=hong@example.com, userRole=CUSTOMER}, simpHeartbeat=[J@5086e4db, simpSessionId=lxvx2g50}], simpSessionId=lxvx2g50}
    }

    // 연결 완료
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
    	StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	log.info("ㅁㅁㅁ onConnected: 세션 연결 완료 - sessionId={}", sha.getSessionId());
   	}
    
    // 구독
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
    	
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	log.info("ㅁㅁㅁ onSubscribe: 구독 요청 - destination={}, sessionId={}", sha.getDestination(), sha.getSessionId());

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

    // 구독 해제
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
    	StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	log.info("ㅁㅁㅁ onUnsubscribe: 구독 해제 - sessionId={}", sha.getSessionId());
	}    

    // 연결 해제
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
    	log.info("ㅁㅁㅁ onDisconnect: 세션 연결 해제 - sessionId={}, closeStatus={}", event.getSessionId(), event.getCloseStatus());
    	
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        roomRepository.removeMemberFromAll(sessionId);
    }
   
}
