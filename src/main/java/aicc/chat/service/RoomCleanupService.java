package aicc.chat.service;

import aicc.chat.domain.ChatRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final RoomRepository roomRepository;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;
    private static final long IDLE_TIMEOUT = 1/*10*/ * 60 * 1000; // 10분 (단위: ms)

    /**
     * 일정 시간 동안 활동이 없는 채팅방을 정리합니다.
     * 매 1분마다 실행됩니다.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupIdleRooms() {
        // log.debug("Starting idle room cleanup task...");
        List<ChatRoom> allRooms = roomRepository.findAllRooms();
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (ChatRoom room : allRooms) {
            long idleTime = now - room.getLastActivityAt();
            if (idleTime > IDLE_TIMEOUT) {
                log.info("Cleaning up idle room: {} (Idle for {} ms)", room.getRoomId(), idleTime);
                roomRepository.deleteRoom(room.getRoomId());
                changed = true;
            }
        }

        if (changed) {
            roomUpdateBroadcaster.broadcastRoomList();
        }
    }
}

