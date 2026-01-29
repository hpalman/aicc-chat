package aicc.chat.websocket;

import java.util.Map;

import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.chat.websocket.domain.WebSocketSessionAttribute;

public class WebSocketAttributes {

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
	// WebSocketSessionAttribute attr = WebSocketAttributes.getSimpSessionAttributes(...)
	public static WebSocketSessionAttribute getSimpSessionAttributes(StompHeaderAccessor accessor) {
        ObjectMapper mapper = new ObjectMapper();
        MessageHeaders headers = accessor.getMessageHeaders();
        Map<?,?> map = (Map<?,?>) headers.get("simpSessionAttributes");
        if ( map == null ) {
            // 2. keySet() + get()
            for (String key : headers.keySet()) {
                Object value = headers.get(key);
                if ( value instanceof GenericMessage) {
                	GenericMessage<?> genericMessage = (GenericMessage<?>) value;

            	    // Payload 확인
            	    //Object payload = genericMessage.getPayload();
            	    //System.out.println("Payload: " + payload);

            	    // Headers 확인
            	    Map<String, Object> headers2 = genericMessage.getHeaders();
            	    map = (Map<?,?>) headers2.get("simpSessionAttributes");
                }
                //System.out.println("Header: [" + key + "] = " + value);
            }

            accessor.getMessageHeaders().forEach((key, value) -> {
                // log.info("onConnect Header [" + key + "] = " + value);
            });

            // 특정 Native Header 출력
            //List<String> userIds = accessor.getNativeHeader("userId");
        }
        WebSocketSessionAttribute sessionAttribute = mapper.convertValue(map, WebSocketSessionAttribute.class);
        /*
        StompHeaderAccessor [headers={simpMessageType=CONNECT_ACK,
                simpConnectMessage=GenericMessage [payload=byte[0], headers={simpMessageType=CONNECT, stompCommand=CONNECT,
                nativeHeaders={accept-version=[1.1,1.0], heart-beat=[10000,10000]},
                simpSessionAttributes={userName=홍길철, userId=cust01, roomId=room-e2e2007b, companyId=apt001, userEmail=cust01@example.com, userRole=CUSTOMER},
                simpHeartbeat=[J@499017b8, simpSessionId=eatejeae}], simpSessionId=eatejeae}]
 		*/
        sessionAttribute.setSessionId( accessor.getSessionId() );
        sessionAttribute.setDestination( accessor.getDestination() );

        StompCommand stompCommand = accessor.getCommand();
        sessionAttribute.setCommand ( (stompCommand != null) ? stompCommand.name() : "UNKNOWN" );

        return sessionAttribute;
    }
	
}
