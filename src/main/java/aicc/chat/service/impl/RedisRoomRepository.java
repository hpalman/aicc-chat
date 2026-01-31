package aicc.chat.service.impl;

// import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import aicc.chat.domain.ChatRoom;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.system-mode", havingValue = "REDIS_ONLY")
public class RedisRoomRepository implements RoomRepository {

    private final StringRedisTemplate redisTemplate;
    // Redis 키 구성: roomId별 멤버/상태/메타데이터, 그리고 전체 roomId 인덱스
    private static final String ROOM_KEY_PREFIX = "chat:room:"; // set of members
    private static final String CHAT_ROOMS_KEY  = "chat:rooms"; // set of roomIds

    @Override
    public ChatRoom createRoom(String name) {
        log.info("▼ createRoom. name:{}",name);
        // [createRoom] name만 받은 경우 내부에서 roomId(UUID) 생성
        // roomId를 서버에서 생성(전체 UUID)
        return createRoom(UUID.randomUUID().toString(), name);
    }
    // private static String get_CHATROOM_roomIdRoomKeyFromRoomId(String )
    @Override
    public ChatRoom createRoom(String roomId, String name) {
    	log.info("▶ createRoom. roomId:{}, name:{}", roomId, name);
        // [createRoom] roomId를 지정해 방 생성 및 메타 키 초기화
        long now = System.currentTimeMillis();
        // roomId를 전체 인덱스(Set)에 등록
        redisTemplate.opsForSet().add(CHAT_ROOMS_KEY, roomId); // chat:rooms, room-c7db3f46
        // 방 이름은 별도 문자열 키로 저장(옵션)
        if (name != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":name", name); // chat:room:{roomId}:name, cust01
        }
        log.info("▶▶▶ {} {}", ROOM_KEY_PREFIX + roomId + ":createdAt", String.valueOf(now) );

        redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":createdAt", String.valueOf(now));
        updateLastActivity(roomId);

        ChatRoom chatRoom =
          ChatRoom.builder()
                .roomId(roomId)
                .roomName(name)
                .members(new HashSet<>())
                .status("BOT")
                .createdAt(now)
                .lastActivityAt(now)
                .build();
        log.info("◀ createRoom E. chatRoom:{}", chatRoom);
        return chatRoom;
    }

    @Override
    public ChatRoom findRoomById(String roomId) {
        log.info("▶ findRoomById. roomId:{}", roomId);

    	// [findRoomById] Redis의 분산 키들을 조회해 ChatRoom으로 합성. roomId에 해당하는 멤버/상태/메타 정보를 조합해 ChatRoom으로 복원
        Set<String> members = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_KEY_PREFIX + roomId + ":mems")) // members
                .orElse(Collections.emptySet());

        ValueOperations<String, String> ofv = redisTemplate.opsForValue();

        String assignedAgent   = ofv.get(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        String createdAtStr    = ofv.get(ROOM_KEY_PREFIX + roomId + ":createdAt");
        String lastActivityStr = ofv.get(ROOM_KEY_PREFIX + roomId + ":lastActivity");
        String status          = ofv.get(ROOM_KEY_PREFIX + roomId + ":mode");
        String name            = ofv.get(ROOM_KEY_PREFIX + roomId + ":name");

        long createdAt = createdAtStr != null ? Long.parseLong(createdAtStr) : 0;
        long lastActivityAt = lastActivityStr != null ? Long.parseLong(lastActivityStr) : 0;

        ChatRoom chatRoom = ChatRoom.builder()
                .roomId(roomId)
                .roomName(name == null ? roomId : name)
                .members(members)
                .status(status == null ? "BOT" : status)
                .assignedAgent(assignedAgent)
                .createdAt(createdAt)
                .lastActivityAt(lastActivityAt)
                .build();
        log.info("◀ findRoomById E. chatRoom:{}", chatRoom);
        return chatRoom;
    }

    @Override
    public void addMember(String roomId, String memberId) {
        log.info("▼ addMember. roomId:{}, memberId:{}", roomId, memberId);

        // [addMember] 방 멤버 Set에 추가 + roomId 인덱스 유지
        // 멤버는 Set으로 관리(중복 방지)
        redisTemplate.opsForSet().add(ROOM_KEY_PREFIX + roomId + ":mems", memberId); // members chat:room:room-a3a3a779, cust01
        redisTemplate.opsForSet().add(CHAT_ROOMS_KEY, roomId); // chat:rooms, room-a3a3a779
    }

    @Override
    public void removeMember(String roomId, String memberId) {
        log.info("▼ removeMember. roomId:{}, memberId:{}", roomId, memberId);

        // [removeMember] 방 멤버 Set에서 제거
        redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId + ":mems", memberId); // members "chat:room:"
    }

    @Override
    public void removeMemberFromAll(String memberId) {
        log.info("▼ removeMemberFromAll. memberId:{}", memberId);

        // [removeMemberFromAll] 인덱스의 모든 roomId를 순회하며 해당 멤버 제거
        // 멤버가 들어간 모든 방에서 제거 (Set 전체 순회)
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(CHAT_ROOMS_KEY)) // chat:rooms
                .orElse(Collections.emptySet());
        for (String roomId : roomIds) {
            redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId + ":mems", memberId); // members chat:room:
        }
    }

    @Override
    public List<ChatRoom> findAllRooms() {
        // [findAllRooms] roomId 인덱스를 읽어 ChatRoom 목록 구성
    	
        // 인덱스 Set에 있는 roomId들을 조회해 목록 구성
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(CHAT_ROOMS_KEY))
                .orElse(Collections.emptySet());

        log.info("▼ findAllRooms. List<ChatRoom> size:{}", roomIds.size());
        
        List<ChatRoom> chatRooms =
        		roomIds.stream()
                .map(this::findRoomById)
                .collect(Collectors.toList());

        return chatRooms;
    }

    @Override
    public void setRoutingMode(String roomId, String mode) {
        log.info("▼ setRoutingMode. roomId:{}, mode:{}", roomId, mode);

        // [setRoutingMode] 방의 라우팅 상태 저장
        // 방 상태(BOT/WAITING/AGENT/CLOSED 등) 저장
        if (roomId != null && mode != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":mode", mode);
        }
    }

    @Override
    public String getRoutingMode(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId);

        // [getRoutingMode] 방의 라우팅 상태 조회
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":mode") : null;
    }

    @Override
    public void setAssignedAgent(String roomId, String agentName) {
        log.info("▶▶▶ roomId:{},agentName:{}", roomId, agentName);
        // [setAssignedAgent] 방에 배정된 상담원 저장 또는 삭제
        if (roomId != null) {
            if (agentName != null) {
                // 상담원 배정
                redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":assignedAgent", agentName);
            } else {
                // agentName이 null이면 키 삭제 (상담원 배정 해제)
                redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
            }
        }
    }

    @Override
    public String getAssignedAgent(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId );

        // [getAssignedAgent] 방에 배정된 상담원 조회
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":assignedAgent") : null;
    }

    @Override
    public boolean assignAgent(String roomId, String agentName) {
        log.info("▶▶▶ roomId:{},agentName:{}", roomId, agentName );

        // [assignAgent] 이미 배정된 경우 실패, 최초 배정만 성공
        if (roomId == null || agentName == null) return false;
        // NX(setIfAbsent)로 최초 배정만 허용(원자적)
        Boolean success = redisTemplate.opsForValue().setIfAbsent(ROOM_KEY_PREFIX + roomId + ":assignedAgent", agentName);
        if (Boolean.TRUE.equals(success)) {
            // 배정 성공 시 모드도 AGENT로 변경
            setRoutingMode(roomId, "AGENT");
            updateLastActivity(roomId);
            return true;
        }
        return false;
    }

    @Override
    public void updateLastActivity(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId );

        // [updateLastActivity] 방의 마지막 활동 시간 갱신
        // 마지막 활동 시간 갱신(밀리초)
        if (roomId != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":lastActivity", String.valueOf(System.currentTimeMillis()));
        }
    }

    @Override
    public void deleteRoom(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId );
        Boolean b;
        // [deleteRoom] roomId 인덱스와 관련 메타 키 삭제
        // 방 관련 키 일괄 삭제(인덱스 + 멤버/메타)
        redisTemplate.opsForSet().remove(CHAT_ROOMS_KEY, roomId); // chat:rooms
        b = redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":mems"); // members chat:room: roomId
        if ( !b ) { log.error("delete failed."); }
        b = redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":name");
        if ( !b ) { log.error("delete failed."); }
        b = redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":mode");
        if ( !b ) { log.error("delete failed."); }
        b = redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        if ( !b ) { log.error("delete failed."); }
        b = redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":lastActivity");
        if ( !b ) { log.error("delete failed."); }
    }
}
