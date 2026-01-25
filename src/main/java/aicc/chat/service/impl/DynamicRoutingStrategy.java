package aicc.chat.service.impl;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.UserRole;
import aicc.chat.service.ChatRoutingStrategy;
import aicc.chat.service.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 방의 상태(모드)에 따라 봇 또는 상담원 전략으로 동적 위임하는 전략 구현체
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicRoutingStrategy implements ChatRoutingStrategy {

    private final RoomRepository roomRepository;
    private final MiChatRoutingStrategy miChatRoutingStrategy;
    private final AgentRoutingStrategy agentRoutingStrategy;
    private final aicc.chat.service.RoomUpdateBroadcaster roomUpdateBroadcaster;

    public static final String MODE_BOT = "BOT";
    public static final String MODE_WAITING = "WAITING";
    public static final String MODE_AGENT = "AGENT";
    public static final String MODE_CLOSED = "CLOSED";

    @Override
    // 방 상태에 따라 상담원/봇 라우팅으로 위임
    public void handleMessage(String roomId, ChatMessage message) {
        // 활동 시간 업데이트
        roomRepository.updateLastActivity(roomId);

        // 퇴장 메시지인 경우 방 상태를 CLOSED로 변경
        if (aicc.chat.domain.MessageType.LEAVE.equals(message.getType())) {
            log.info("Room {} is being closed due to LEAVE message", roomId);
            roomRepository.setRoutingMode(roomId, MODE_CLOSED);
            roomUpdateBroadcaster.broadcastRoomList();
        }

        String mode = roomRepository.getRoutingMode(roomId);
        if (mode == null) {
            mode = MODE_BOT;
        }

        log.debug("Dynamic routing for room: {}, current mode: {}", roomId, mode);

        // 1. 상담원(AGENT 역할)이 보낸 메시지인 경우
        if (UserRole.AGENT.equals(message.getSenderRole())) {
            // 상담원 메시지는 전파만 담당하는 agentRoutingStrategy로 처리 (이미 배정된 상태여야 함)
            agentRoutingStrategy.handleMessage(roomId, message);
            return;
        }

        // 2. 고객 또는 기타 메시지 처리
        if (MODE_AGENT.equalsIgnoreCase(mode)) {
            agentRoutingStrategy.handleMessage(roomId, message);
        } else if (MODE_WAITING.equalsIgnoreCase(mode)) {
            // 대기 중일 때는 상담원 브로커에도 전달하고 (상담원 화면용), 봇은 응답하지 않음
            agentRoutingStrategy.handleMessage(roomId, message);
        } else {
            miChatRoutingStrategy.handleMessage(roomId, message);
        }
    }

    @Override
    // 방 생성 시 초기 모드 설정 및 봇 초기화 호출
    public void onRoomCreated(ChatRoom room) {
        // 방 생성 시 초기 모드는 BOT
        roomRepository.setRoutingMode(room.getRoomId(), MODE_BOT);
        miChatRoutingStrategy.onRoomCreated(room);
    }
}

