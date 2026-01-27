package aicc.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import aicc.chat.domain.UserRole;
import aicc.chat.service.RoomRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomRepository roomRepository;
    private final StringRedisTemplate redisTemplate;
    
    private static final String ONLINE_AGENTS_KEY = "chat:online:agents";

    
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
    // STOMP 연결 이벤트 로깅
    public void onConnect(SessionConnectedEvent event) {
        log.info("ㅁㅁㅁ WebSocket connected: {}", event.getMessage().getHeaders());
        // WebSocket connected: {simpMessageType=CONNECT_ACK, simpConnectMessage=GenericMessage [payload=byte[0], headers={simpMessageType=CONNECT, stompCommand=CONNECT, nativeHeaders={accept-version=[1.1,1.0], heart-beat=[10000,10000]}, simpSessionAttributes={userName=고객-358d, userId=hong, roomId=room-00e386c1, companyId=apt001, userEmail=hong@example.com, userRole=CUSTOMER}, simpHeartbeat=[J@5086e4db, simpSessionId=lxvx2g50}], simpSessionId=lxvx2g50}
    }

    // 연결 완료
    @EventListener
    // 세션 연결 완료 시 로깅
    public void onConnected(SessionConnectedEvent event) {
    	StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	log.info("ㅁㅁㅁ onConnected: 세션 연결 완료 - sessionId={}", sha.getSessionId());
   	}
    
    // 구독
    @EventListener
    // 특정 방 토픽 구독 시 멤버 등록
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
    // 토픽 구독 해제 이벤트 로깅
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
    	StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	log.info("ㅁㅁㅁ onUnsubscribe: 구독 해제 - sessionId={}", sha.getSessionId());
	}    

    // 연결 해제
    @EventListener
    // 연결 종료 시 세션 기반 멤버 정리 및 상담원 온라인 상태 제거
    public void onDisconnect(SessionDisconnectEvent event) {
    	log.info("ㅁㅁㅁ onDisconnect: 세션 연결 해제 - sessionId={}, closeStatus={}", event.getSessionId(), event.getCloseStatus());
    	
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        
        // 1. 모든 방에서 세션 ID로 멤버 제거
        roomRepository.removeMemberFromAll(sessionId);
        
        // 2. 상담원인 경우 Redis 온라인 상태 제거
        if (sha.getSessionAttributes() != null) {
            Object userIdObj = sha.getSessionAttributes().get("userId");
            Object userRoleObj = sha.getSessionAttributes().get("userRole");
            
            if (userIdObj != null && userRoleObj != null) {
                String userId = userIdObj.toString();
                String userRoleStr = userRoleObj.toString();
                
                // 상담원(AGENT)인 경우에만 Redis 온라인 상태 제거
                if ("AGENT".equals(userRoleStr) || UserRole.AGENT.toString().equals(userRoleStr)) {
                    String agentKey = ONLINE_AGENTS_KEY + ":" + userId;
                    Boolean deleted = redisTemplate.delete(agentKey);
                    
                    if (Boolean.TRUE.equals(deleted)) {
                        log.info("✅ 상담원 오프라인 처리 완료 - userId={}, sessionId={}", userId, sessionId);
                    } else {
                        log.warn("⚠️ 상담원 온라인 키 삭제 실패 (이미 만료됨?) - userId={}, sessionId={}", userId, sessionId);
                    }
                }
            }
        }
        
        log.debug("세션 연결 해제 처리 완료 - sessionId={}", sessionId);
    }
   
}
