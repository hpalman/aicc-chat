package aicc.chat.service;

import aicc.chat.domain.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomUpdateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;

    public void broadcastRoomList() {
        // 전체 방 목록을 구독자에게 브로드캐스트
        List<ChatRoom> rooms = roomRepository.findAllRooms();
        if (rooms != null) {
            messagingTemplate.convertAndSend("/topic/rooms", rooms);
        }
    }
}

