package aicc.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.service.CustomerAuthService;
import aicc.chat.service.WebSocketSessionService;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import aicc.chat.websocket.domain.WebSocketSessionAttribute;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RoomRepository roomRepository;
    private final WebSocketSessionService webSocketSessionService;
    private final MessageBroker messageBroker;
    private final CustomerAuthService customerAuthService;
static private boolean skip = true;

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
	private WebSocketSessionAttribute getSimpSessionAttributes(StompHeaderAccessor accessor) {
		return WebSocketAttributes.getSimpSessionAttributes(accessor);
    }

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
        ▼▲ ▶
        ㅁ 연결 시도 : STOMP 연결 이벤트 로깅
	 */
    @EventListener
    public void onConnect(SessionConnectEvent event
    ) {
        log.info("▼ onConnect S. {}", event.getMessage());
if ( skip ) return;
        log.info("ㅁㅁㅁ ▶ WebSocket onConnect: {}", event.getMessage().getHeaders());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);
        log.info(">>>>>>>>>>>>> sessionAttribute:{}", sessionAttribute);

        //MessageHeaders headers = accessor.getMessageHeaders();
        //
        //// 2. keySet() + get()
        //for (String key : headers.keySet()) {
        //    Object value = headers.get(key);
        //    log.info("1>> onConnect Header: [" + key + "] = " + value);
        //}
        //Object v;
        //if ( (v = accessor.getMessageHeaders().get("simpSessionAttributes")) != null ) {
        //    if ( v instanceof java.util.Map) {
        //        String uid = (String) ((java.util.Map) v).get("userId");
        //        log.info(">>>>>>>> uid:{}",uid);
        //    }
        //
        //}
        //
        //// 헤더 전체를 로그로 출력
        //headers.forEach((key, value) -> {
        //    if ( key.equals("simpSessionAttributes")) {
        //        log.info("aaaa");
        //        if ( value instanceof java.util.Map) {
        //            String uid = (String) ((java.util.Map) value).get("userId");
        //            // log.info("uid:{}",uid);
        //        }
        //    }
        //    log.info("1 onConnect Header: " + key + " = " + value);
        //});


        // // simpSessionAttributes 가져오기
        //Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        //if (sessionAttributes != null) {
        //    Object myValue = sessionAttributes.get("userId"); // 예: 특정 키로 값 꺼내기 System.out.println("세션에 저장된 값: " + myValue); }
        //    log.info("userId:{}", myValue);
        //}

        //String user = accessor.getUser() != null ? accessor.getUser().getName() : "anonymous";
        //String command = accessor.getCommand() != null ? accessor.getCommand().name() : "UNKNOWN";

        // 모든 헤더 출력
        //accessor.getMessageHeaders().forEach((key, value) -> {
        //    log.info("2 onConnect Header [" + key + "] = " + value);
        //});

        // 특정 Native Header 출력
        //List<String> userIds = accessor.getNativeHeader("userId");
        //if (userIds != null) {
        //    log.info("ㅁㅁㅁ userId header: " + userIds);
        //}
        log.info("◀ WebSocket 연결 이벤트 종료 ◀◀◀◀◀◀◀◀◀◀");

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

    // 연결 완료 : 세션 연결 완료 시 로깅 및 Redis 세션 등록
    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        log.info("▶ onConnected S. {}", event.getMessage());
if ( skip ) return;
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);

        // 3. Redis에 세션 정보 저장
        if (sessionAttribute != null ) {
            log.info("▶▶ Redis에 세션 정보 저장 시작. webSocketSessionService.registerSession call. sessionAttribute:{}", sessionAttribute);
            webSocketSessionService.registerSession(sessionAttribute.getSessionId(), sessionAttribute.getUserId(), sessionAttribute.getUserRole());
            log.info("◀◀ Redis에 세션 저장 저장 완료.");

            // 고객인 경우 sessionId 업데이트
            if ("CUSTOMER".equals(sessionAttribute.getUserRole()) && sessionAttribute.getUserId() != null) {
                customerAuthService.updateSessionId(sessionAttribute.getUserId(), sessionAttribute.getSessionId());
                log.info("Customer {} sessionId updated in Redis", sessionAttribute.getUserId());
            }
        } else {
            log.error("❌ Redis 세션 등록 실패 - sessionId 또는 userId가 null입니다.");
        }

        log.info("◀ WebSocket 연결 완료 이벤트 종료.");
   	}

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
	    ㅁ 구독:특정 방 토픽 구독 시 멤버 등록
     */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event
    ) {
        log.info("▶ onSubscribe S. {}", event.getMessage());
if ( skip ) return;
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);
        log.info("📌 sessionAttribute:{}", sessionAttribute);

    	String dest = sessionAttribute.getDestination();

        if (dest != null && dest.startsWith("/topic/room/")) {
            String roomId = dest.replace("/topic/room/", "");
            if (sessionAttribute.getUserId() != null) {
                roomRepository.addMember(roomId, sessionAttribute.getUserId()); // roomId:room-a3a3a779, user: cust01
            }
            else {
                roomRepository.addMember(roomId, sessionAttribute.getSessionId());
            }
        }
        log.info("◀ WebSocket 토픽 구독 이벤트 처리 종료.");
    }

    // 구독 해제
    @EventListener
    // 토픽 구독 해제 이벤트 로깅
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        log.info("▶ onUnsubscribe S. {}",event.getMessage());
if ( skip ) return;
    	StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);

        log.info("◀ WebSocket 토픽 구독 해제 처리 이벤트 종료. sessionAttribute:{}", sessionAttribute);
	}

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

		ㅁ 연결 해제 - 연결 종료 시 세션 기반 멤버 정리 및 Redis 세션 제거
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event
    ) {
        log.info("▶ onDisconnect S. {}", event.getMessage());
if ( skip ) return;
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        WebSocketSessionAttribute sessionAttribute = getSimpSessionAttributes(accessor);
        // log.info("📌 sessionAttribute:{}", sessionAttribute);

        String closeStatus = event.getCloseStatus() != null ? event.getCloseStatus().toString() : "UNKNOWN";
        log.info("📌 closeStatus: {}", closeStatus);

        String sessionId = sessionAttribute.getSessionId();
        String userId    = sessionAttribute.getUserId();
        String userName  = sessionAttribute.getUserName();
        String userRole  = sessionAttribute.getUserRole();
        String roomId    = sessionAttribute.getRoomId();

        // 1. Redis에서 세션 정보 제거
        if (sessionId != null) {
            log.info("▶▶ Redis에서 세션:{} 제거(webSocketSessionService.unregisterSession) 시작. sessionAttribute:{}", sessionId, sessionAttribute);
            webSocketSessionService.unregisterSession(sessionId);
            log.info("◀◀ Redis에서 세션 제거 완료!");

        } else {
            log.error("❌ Redis 세션 제거 실패 - simpSessionId가 null입니다.");
        }

        // 2. 고객이 연결 해제된 경우 상담원에게 알림 및 Redis에서 제거
        if ("CUSTOMER".equals(userRole) && roomId != null && userId != null) {
            log.info("▶▶ 고객 연결 해제 처리 시작...");
            log.info("  - roomId: {}", roomId);
            log.info("  - userId: {}", userId);
            log.info("  - userName: {}", userName);

            try {
                // Redis에서 고객 정보 제거
                customerAuthService.logout(userId);
                log.info("✅ Redis에서 고객 정보 제거 완료");

                // 채팅방 정보 조회
                log.info("▶▶ 채팅방 정보:{} 조회", roomId);
                ChatRoom room = roomRepository.findRoomById(roomId); // REDIS
                log.info("◀◀ 채팅방 정보 조회 완료! room:{}", room);

                if (room != null && room.getAssignedAgent() != null) {
                    // 상담원이 배정된 경우에만 알림 전송
                    // log.info("  - assignedAgent: {}", room.getAssignedAgent());

                    log.info("▶▶ 고객 연결 해제 알림 전송 시작");
                    ChatMessage disconnectNotice = ChatMessage.builder()
                            .roomId(roomId)
                            .sender("System")
                            .senderRole(UserRole.SYSTEM)
                            .message(userName + " 고객의 연결이 끊어졌습니다.")
                            .type(MessageType.CUSTOMER_DISCONNECTED)
                            .build();
                    messageBroker.publish(disconnectNotice);
                    log.info("◀◀ 고객 연결 해제 알림 전송 완료! disconnectNotice:{}", disconnectNotice);
                } else {
                    log.info("  ℹ️ 상담원이 배정되지 않은 방이거나 BOT 상담 중 - 알림 전송 생략");
                }
            } catch (Exception e) {
                log.error("❌ 고객 연결 해제 처리 실패", e);
            }
        }

        // 3. 채팅방 멤버 제거
        /*
	   예시 로그:
    	sessionId=aroiqtew,
    	closeStatus=CloseStatus[code=1000, reason=null],
    	msghdr:{
    	    simpMessageType=DISCONNECT, stompCommand=DISCONNECT,
    	    simpSessionAttributes={companyId=apt001, userEmail=agent02@aicc.com, userName=상담원-02, userRole=AGENT, userId=agent02},
    	    simpSessionId=aroiqtew
   	    }
         */
        log.info("▶▶ 채팅방 멤버 제거 시작.roomRepository.removeMemberFromAll(sessionId:{})", sessionId);
        roomRepository.removeMemberFromAll(sessionId);
        log.info("◀◀ 채팅방 멤버 제거 종료.");

        log.info("◀ WebSocket 연결 해제 이벤트 종료.");
    }

}
