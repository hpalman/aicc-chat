package aicc.chat.service.inteface;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;

/**
 * 시스템 설정에 따라 메시지를 라우팅하는 전략 인터페이스
 */
public interface ChatRoutingStrategy {
    
    /**
     * 메시지가 수신되었을 때의 처리 로직
     * 
     * @param roomId 채팅방 ID
     * @param message 수신된 메시지 객체
     */
    void handleMessage(String roomId, ChatMessage message);

    /**
     * 채팅방이 생성되었을 때의 초기화 로직 (예: 봇 인사말 전송 등)
     * 
     * @param room 생성된 채팅방 객체
     */
    default void onRoomCreated(ChatRoom room) {
        // 기본적으로는 아무 작업도 하지 않음
    }
}

