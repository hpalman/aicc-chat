package aicc.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
    // STOMP 연결 이벤트 로깅
    public void onConnect(SessionConnectedEvent event
        /*
        SessionConnectEvent는 Spring WebSocket + STOMP 환경에서
        클라이언트가 WebSocket 연결을 시작할 때 발생하는 이벤트입니다.
        이 이벤트를 통해 세션 ID, 사용자 정보, STOMP 헤더, 메시지 정보 등을 확인할 수 있습니다.
                
        확인 가능한 정보 목록
        SessionConnectEvent에서 StompHeaderAccessor를 사용하면 다음 정보를 추출할 수 있습니다:
        
        정보 항목       설명
        --------------- ---------------------------------------------------
        sessionId       WebSocket 세션 고유 ID
        user            인증된 사용자 정보 (Principal)
        nativeHeaders   클라이언트가 STOMP CONNECT 시 보낸 커스텀 헤더
        command         STOMP 명령 (예: CONNECT)
        message         전체 메시지 객체                
        */
    ) {
        log.info("ㅁㅁㅁ ▶ WebSocket onConnect: {}", event.getMessage().getHeaders());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        String command = accessor.getCommand() != null ? accessor.getCommand().name() : "UNKNOWN";
        
        log.info("ㅁㅁㅁ Session ID: " + sessionId);
        log.info("ㅁㅁㅁ User: " + user);
        log.info("ㅁㅁㅁ Command: " + command);
        
        // 모든 헤더 출력
        accessor.getMessageHeaders().forEach((key, value) -> {
            log.info("ㅁㅁㅁ Header [" + key + "] = " + value);
        });

        // 특정 Native Header 출력
        List<String> userIds = accessor.getNativeHeader("userId");
        if (userIds != null) {
            log.info("ㅁㅁㅁ userId header: " + userIds);
        }
        log.info("ㅁㅁㅁ ◀ WebSocket onConnect.");        
        /*
     WebSocket connected:
          { simpMessageType=CONNECT_ACK,
            simpConnectMessage=GenericMessage [
               payload=byte[0],
               headers={
                 simpMessageType=CONNECT,
                 stompCommand=CONNECT,
                 nativeHeaders={
                     accept-version=[1.1,1.0], heart-beat=[10000,10000]
                 },
                 simpSessionAttributes={
                     userName=고객-358d,
                     userId=hong,
                     roomId=room-00e386c1,
                     companyId=apt001,
                     userEmail=hong@example.com,
                     userRole=CUSTOMER
                 },
                 simpHeartbeat=[J@5086e4db, simpSessionId=lxvx2g50
               }
               ],
             simpSessionId=lxvx2g50
          }         
         
         */
    }

    // 연결 완료
    @EventListener
    // 세션 연결 완료 시 로깅
    public void onConnected(SessionConnectedEvent event) {
    	StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
    	//log.info("ㅁㅁㅁ onConnected: 세션 연결 완료 - sessionId={}", sha.getSessionId());
    	
        log.info("ㅁㅁㅁ ▶ WebSocket onConnected: {}", event.getMessage().getHeaders());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        String command = accessor.getCommand() != null ? accessor.getCommand().name() : "UNKNOWN";
        
        log.info("ㅁㅁㅁ Session ID: " + sessionId);
        log.info("ㅁㅁㅁ User: " + user);
        log.info("ㅁㅁㅁ Command: " + command);
        
        // 모든 헤더 출력
        accessor.getMessageHeaders().forEach((key, value) -> {
            log.info("ㅁㅁㅁ Header [" + key + "] = " + value);
        });

        // 특정 Native Header 출력
        List<String> userIds = accessor.getNativeHeader("userId");
        if (userIds != null) {
            log.info("ㅁㅁㅁ userId header: " + userIds);
        }
        log.info("ㅁㅁㅁ ◀ WebSocket onConnected.");        
    	
   	}
    
    // 구독
    @EventListener
    // 특정 방 토픽 구독 시 멤버 등록
    public void onSubscribe(SessionSubscribeEvent event
/*            
    Spring Boot 3.4.1 (Spring Messaging 6.x 기반)에서 SessionSubscribeEvent는 클라이언트가
    특정 STOMP destination(예: /topic/chatroom/123)을 구독할 때 발생하는 이벤트입니다.
    이 이벤트를 통해 세션 ID, 사용자 정보, 구독 대상(destination), STOMP 헤더 등을 확인할 수 있습니다.

    🔍 SessionSubscribeEvent에서 확인 가능한 정보
    StompHeaderAccessor를 사용하면 다음을 추출할 수 있습니다:

    항목            설명
    --------------- -----------------------------------------------------------
    sessionId       WebSocket 세션 고유 ID
    user            인증된 사용자(Principal)
    destination     클라이언트가 구독한 STOMP 경로 (예: /topic/chatroom/123)
    command         STOMP 명령 (SUBSCRIBE)
    nativeHeaders   클라이언트가 SUBSCRIBE 시 보낸 커스텀 헤더
    messageHeaders  전체 메시지 헤더 맵            
  */          
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("ㅁㅁㅁ ▶ onSubscribe: 구독 요청"); 
        String sessionId    = accessor.getSessionId();
        String user         = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        String destination  = accessor.getDestination();
        String command      = accessor.getCommand() != null ? accessor.getCommand().name() : "UNKNOWN";
    
        log.info("Session ID: " + sessionId);
        log.info("User: " + user);
        log.info("Destination: " + destination);
        log.info("Command: " + command);
    
        // Native headers 출력
        if (accessor.getMessageHeaders() != null) {
            accessor.getMessageHeaders().forEach((key, value) -> {
                log.info("Header [" + key + "] = " + value);
            });
        }
    
        // 특정 헤더 값 확인 (예: chatRoomId)
        String chatRoomId = accessor.getFirstNativeHeader("chatRoomId");
        if (chatRoomId != null) {
            log.info("chatRoomId header: " + chatRoomId);
        }
        log.info("ㅁㅁㅁ ◀ onSubscribe"); 
        
        
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());

    	String dest = sha.getDestination();
        //String sessionId = sha.getSessionId();
        //String user = null;
        if (sha.getSessionAttributes() != null) {
            Object o = sha.getSessionAttributes().get("userId"); 
            if (o != null)
                user = o.toString();
        }
        if (dest != null && dest.startsWith("/topic/room/")) {
            String roomId = dest.replace("/topic/room/", "");
            if (user != null) {
                roomRepository.addMember(roomId, user); // roomId:room-a3a3a779, user: cust01
            }
            else {
                roomRepository.addMember(roomId, sessionId);
            }
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
    // 연결 종료 시 세션 기반 멤버 정리
    public void onDisconnect(SessionDisconnectEvent event
/*            
    SessionDisconnectEvent는 Spring WebSocket + STOMP 환경에서 클라이언트(WebSocket 세션)가 끊길 때 발생하는 이벤트입니다.
    이 이벤트를 활용하면 세션 종료 시점에 사용자 상태를 갱신하거나 로그아웃 처리, 알림 전송 등을 할 수 있습니다.

SessionDisconnectEvent에서 확인 가능한 정보
StompHeaderAccessor를 사용하면 다음을 추출할 수 있습니다:

항목            설명
--------------- ------------------------------------
sessionId       WebSocket 세션 고유 ID
user            인증된 사용자(Principal)
closeStatus     연결 종료 상태 코드 (예: 정상 종료, 에러 종료)
message         전체 STOMP 메시지 객체
nativeHeaders   연결 종료 시점에 포함된 헤더 (일반적으로 CONNECT 시 전달된 값과 동일)
*/
    ) {
    	log.info("ㅁㅁㅁ onDisconnect: 세션 연결 해제 - sessionId={}, closeStatus={}, msghdr:{}", event.getSessionId(), event.getCloseStatus(),event.getMessage().getHeaders());
        log.info("ㅁㅁㅁ ▶ onDisconnect"); 

    	
    	
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        String closeStatus = event.getCloseStatus() != null ? event.getCloseStatus().toString() : "UNKNOWN";

        log.info("Session ID: " + sessionId);
        log.info("User: " + user);
        log.info("Close Status: " + closeStatus);

        // Native headers 확인 (예: userId, chatRoomId)
        String userId = accessor.getFirstNativeHeader("userId");
        if (userId != null) {
            log.info("userId header: " + userId);
        }
    	
        log.info("ㅁㅁㅁ ◀ onDisconnect"); 
    	
/*
    	sessionId=aroiqtew,
    	closeStatus=CloseStatus[code=1000, reason=null],
    	msghdr:{
    	    simpMessageType=DISCONNECT, stompCommand=DISCONNECT,
    	    simpSessionAttributes={companyId=apt001, userEmail=agent02@aicc.com, userName=상담원-02, userRole=AGENT, userId=agent02},
    	    simpSessionId=aroiqtew
   	    }
*/
        /// String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        roomRepository.removeMemberFromAll(sessionId);
    }
   
}
