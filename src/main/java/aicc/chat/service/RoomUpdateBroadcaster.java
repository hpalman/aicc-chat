package aicc.chat.service;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import aicc.chat.domain.ChatRoom;
import aicc.chat.domain.msg.PubMessage;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomUpdateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;

    private final MessageBroker messageBroker;

    // @TODO: 여기서 직접 웹소켓 Endpoint로 보내면 이 서버에 연결된 소켓만 받을 수 있으므로 publish로 변경해야 함.
    public void broadcastRoomList() {
        log.info("▼ broadcastRoomList");
        // 전체 방 목록을 구독자(상담사)에게 브로드캐스트
        List<ChatRoom> rooms = roomRepository.findAllRooms();
        if (rooms != null) {
            //// messagingTemplate.convertAndSend("/topic/rooms", rooms);
        }

        PubMessage pubMessage = new PubMessage("TOPIC_ROOMS", "/topic/rooms", rooms);
        messageBroker.publish(ChannelName.CHAT, pubMessage);
    }

    // /topic/agent-availability 채널을 통해 모든 고객에게 알림:
    @SuppressWarnings("null")
    public void broadcastAgentLogin() {
	 // MessageBroker 또는 별도 서비스에서
    	messagingTemplate.convertAndSend("/topic/agent-availability",
	        Map.of("available", true, "timestamp", System.currentTimeMillis()));
    }
}

