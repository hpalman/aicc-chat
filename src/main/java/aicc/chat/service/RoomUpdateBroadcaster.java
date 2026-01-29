package aicc.chat.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import aicc.chat.domain.ChatRoom;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomUpdateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;

    public void broadcastRoomList() {
        log.info("▼ broadcastRoomList");
        // 전체 방 목록을 구독자에게 브로드캐스트
        List<ChatRoom> rooms = roomRepository.findAllRooms();
        if (rooms != null) {
            messagingTemplate.convertAndSend("/topic/rooms", rooms);
        }
    }
}

