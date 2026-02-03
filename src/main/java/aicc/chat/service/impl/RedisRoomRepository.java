package aicc.chat.service.impl;

// import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import aicc.chat.consts.Constants;
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
    private static final String CHAT_ROOMS_KEY      = "chat:rooms"; // set of roomIds

    // @Override
    // public ChatRoom createRoom(String name) {
    //     log.info("▼ createRoom. name:{}",name);
    //     // [createRoom] name만 받은 경우 내부에서 roomId(UUID) 생성
    //     // roomId를 서버에서 생성(전체 UUID)
    //     return createRoom(UUID.randomUUID().toString(), name);
    // }
    // private static String get_CHATROOM_roomIdRoomKeyFromRoomId(String )


    @Override
    public ChatRoom createRoom(String roomId, String name, String creatorId) {
    	log.info("▶ createRoom. roomId:{}, name:{}", roomId, name);
        // [createRoom] roomId를 지정해 방 생성 및 메타 키 초기화
        long now = System.currentTimeMillis();
        // roomId를 전체 인덱스(Set)에 등록
//// 현재 불필요.        redisTemplate.opsForSet().add(CHAT_ROOMS_KEY, roomId); // chat:rooms, room-c7db3f46

        // Hash 구조로 메타데이터 저장
        String roomInfoKey = Constants.ROOM_INFO_KEY_PREFIX + roomId; // chat:room-info:{roomId}"
Map<String, String> roomInfo = new HashMap<>();
roomInfo.put("name"        , name); // 방이름
roomInfo.put("creatorId"   , creatorId); // 방생성자ID(고객ID)
roomInfo.put("createdAt"   , String.valueOf(now));
roomInfo.put("lastActivity", String.valueOf(now));
roomInfo.put("routingMode" , "BOT");
redisTemplate.opsForHash().putAll(roomInfoKey, roomInfo);

        //// redisTemplate.opsForHash().put(roomKey, "name", name); // 방이름
        //// redisTemplate.opsForHash().put(roomKey, "creatorId"   , creatorId); // 방생성자ID(고객ID)
        //// redisTemplate.opsForHash().put(roomKey, "createdAt"   , String.valueOf(now));
        //// redisTemplate.opsForHash().put(roomKey, "lastActivity", String.valueOf(now));
        //// redisTemplate.opsForHash().put(roomKey, "routingMode" , "BOT");
        //// log.info("▶▶▶ {} {}", roomKey + ":createdAt", String.valueOf(now));

        ChatRoom chatRoom = ChatRoom.builder()
                .creatorId(creatorId)
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
        log.info("▼ findRoomById. roomId:{}", roomId);

    	// [findRoomById] Redis의 분산 키들을 조회해 ChatRoom으로 합성. roomId에 해당하는 멤버/상태/메타 정보를 조합해 ChatRoom으로 복원
        // 변경: room:{roomId}:mems → room:mems:{roomId}
        Set<String> members =
           Optional.ofNullable(redisTemplate.opsForSet().members(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId)) // "chat:room-member:"
                .orElse(Collections.emptySet());

        // Hash에서 메타데이터 조회
        // String roomKey = Constants.ROOM_INFO_KEY_PREFIX + roomId; // "chat:room-info:"
        String roomInfoKey = Constants.ROOM_INFO_KEY_PREFIX + roomId; // "chat:room-info:{roomId}"

        Map<Object, Object> map = redisTemplate.opsForHash().entries(roomInfoKey);
        String assignedAgent   = (String) map.get("assignedAgent");
        String name            = (String) map.get("name"         );
        String createdAtStr    = (String) map.get("createdAt"    );
        String creatorId       = (String) map.get("creatorId"    );
        String lastActivityStr = (String) map.get("lastActivity" );
        String status          = (String) map.get("routingMode"  );

        long createdAt      = createdAtStr    != null ? Long.parseLong(createdAtStr)    : 0;
        long lastActivityAt = lastActivityStr != null ? Long.parseLong(lastActivityStr) : 0;

        ChatRoom chatRoom = ChatRoom.builder()
                .creatorId(creatorId)
                .roomId(roomId)
                .roomName(name == null ? roomId : name)
                .members(members)
                .status(status == null ? "BOT" : status)
                .assignedAgent(assignedAgent)
                .createdAt(createdAt)
                .lastActivityAt(lastActivityAt)
                .build();
        log.info("▲ findRoomById E. roomId:{} > chatRoom:{}", roomId, chatRoom);
        return chatRoom;
    }

    @Override
    public void addMember(String roomId, String memberId) {
        log.info("▼ addMember S. roomId:{}, memberId:{}", roomId, memberId);

        // [addMember] 방 멤버 Set에 추가 + roomId 인덱스 유지
        // 멤버는 Set으로 관리(중복 방지)
        // 변경: room:{roomId}:mems → room:mems:{roomId}
        redisTemplate.opsForSet().add(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId, memberId); // chat:room-member:
        // 현재 불필요. redisTemplate.opsForSet().add(CHAT_ROOMS_KEY, roomId); // "chat:rooms"

        log.info("▲ addMember E.");
    }

    @Override
    public void removeMember(String roomId, String memberId) {
        log.info("▼ removeMember. roomId:{}, memberId:{}", roomId, memberId);

        // [removeMember] 방 멤버 Set에서 제거
        // 변경: room:{roomId}:mems → room:mems:{roomId}
        redisTemplate.opsForSet().remove(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId, memberId);
    }

    @Override
    public void removeMemberFromAll(String memberId) {
        log.info("▼ removeMemberFromAll. memberId:{}", memberId);

        // [removeMemberFromAll] 인덱스의 모든 roomId를 순회하며 해당 멤버 제거
        // 멤버가 들어간 모든 방에서 제거 (Set 전체 순회)
        // 변경: room:{roomId}:mems → room:mems:{roomId}
        // Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(CHAT_ROOMS_KEY))
        //         .orElse(Collections.emptySet());
        Set<String> roomIds = _getRoomInfoList();

        for (String roomId : roomIds) {
            redisTemplate.opsForSet().remove(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId, memberId);
        }
    }


    /**
     * chat:*
     *   room-info:*
     *     # {roomId} 목록을 구함
     * @return
     */
    private Set<String> _getRoomInfoList() {
        // 인덱스 Set에 있는 roomId들을 조회해 목록 구성
        // Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(CHAT_ROOMS_KEY))
        //         .orElse(Collections.emptySet());
        // @TODO 운여환경에서는 다른 방식. 즉 SCAN 방식으로 바꾸라고 AI가 그런다.
        Set<String> keys = redisTemplate.keys(Constants.ROOM_INFO_KEY_PREFIX + "*"); // chat:room-info: ex. "user:*"

        // 접두사 제거 후 room-... 부분만 추출
        return keys.stream()
        		.map(k -> k.replace(Constants.ROOM_INFO_KEY_PREFIX, "")) // "chat:room-info:"
        		.collect(Collectors.toSet());        
    }

    @Override
    public List<ChatRoom> findAllRooms() {
        // [findAllRooms] roomId 인덱스를 읽어 ChatRoom 목록 구성
        // routingMode가 CLOSED가 아닌 경우만 반환

        // 인덱스 Set에 있는 roomId들을 조회해 목록 구성
        // Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(CHAT_ROOMS_KEY))
        //         .orElse(Collections.emptySet());

        Set<String> roomIds = _getRoomInfoList();
        log.info("▼ findAllRooms. Total roomIds size:{}", roomIds.size());

        List<ChatRoom> chatRooms =
        		roomIds.stream()
                .map(this::findRoomById)
                .filter(room -> room != null && !"CLOSED".equals(room.getStatus()))
                .collect(Collectors.toList());

        log.info("▼ findAllRooms. Filtered List<ChatRoom> size:{} (excluding CLOSED)", chatRooms.size());

        return chatRooms;
    }

    @Override
    public void setRoutingMode(String roomId, String routingMode) {
        log.info("▼ setRoutingMode. roomId:{}, routingMode:{}", roomId, routingMode);

        // [setRoutingMode] 방의 라우팅 상태 저장. 방 상태(BOT/WAITING/AGENT/CLOSED 등) 저장
        if (roomId != null && routingMode != null) {
            redisTemplate.opsForHash().put(Constants.ROOM_INFO_KEY_PREFIX + roomId, "routingMode", routingMode); // chat:room-info:{roomId} mode
        }
    }

    @Override
    public String getRoutingMode(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId);

        // [getRoutingMode] 방의 라우팅 상태 조회
        return roomId != null ? (String) redisTemplate.opsForHash().get(Constants.ROOM_INFO_KEY_PREFIX + roomId, "routingMode") : null;
    }

    @Override
    public void setAssignedAgent(String roomId, String agentName) {
        log.info("▶▶▶ roomId:{},agentName:{}", roomId, agentName);
        // [setAssignedAgent] 방에 배정된 상담원 저장 또는 삭제
        if (roomId != null) {
            String roomKey = Constants.ROOM_INFO_KEY_PREFIX + roomId;
            if (agentName != null) {
                // 상담원 배정
                redisTemplate.opsForHash().put(roomKey, "assignedAgent", agentName);
            } else {
                // agentName이 null이면 필드 삭제 (상담원 배정 해제)
                redisTemplate.opsForHash().delete(roomKey, "assignedAgent");
            }
        }
    }

    @Override
    public String getAssignedAgent(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId );

        // [getAssignedAgent] 방에 배정된 상담원 조회
        return roomId != null ? (String) redisTemplate.opsForHash().get(Constants.ROOM_INFO_KEY_PREFIX + roomId, "assignedAgent") : null;
    }

    @Override
    public boolean assignAgent(String roomId, String agentName) {
        log.info("▶▶▶ roomId:{},agentName:{}", roomId, agentName );

        // [assignAgent] 이미 배정된 경우 실패, 최초 배정만 성공
        if (roomId == null || agentName == null) return false;
        // Hash의 putIfAbsent로 최초 배정만 허용(원자적)
        String roomKey = Constants.ROOM_INFO_KEY_PREFIX + roomId;
        Boolean success = redisTemplate.opsForHash().putIfAbsent(roomKey, "assignedAgent", agentName);
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
        log.info("▼ updateLastActivity S. roomId:{}", roomId );

        // 방의 마지막 활동 시간 갱신(밀리초)
        redisTemplate.opsForHash().put(Constants.ROOM_INFO_KEY_PREFIX + roomId, "lastActivity", String.valueOf(System.currentTimeMillis())); // "chat:room-info:{roomId}"

        log.info("▲ updateLastActivity E.");
    }

    @Override
    public void deleteRoom(String roomId) {
        log.info("▶▶▶ roomId:{}", roomId );
        Boolean b;
        // [deleteRoom] roomId 인덱스와 관련 메타 키 삭제
        // 방 관련 키 일괄 삭제(인덱스 + 멤버/메타)
        redisTemplate.opsForSet().remove(CHAT_ROOMS_KEY, roomId); // chat:rooms

        // 변경: room:{roomId}:mems → room:mems:{roomId}
        b = redisTemplate.delete(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId);
        if ( !b ) { log.error("delete room:mems:{} failed.", roomId); }

        // Hash 키 삭제 (name, routingMode, assignedAgent, lastActivity, createdAt 등 모든 필드 포함)
        b = redisTemplate.delete(Constants.ROOM_INFO_KEY_PREFIX + roomId);
        if ( !b ) { log.error("delete chat:room:{} hash failed.", roomId); }
    }

    @Override
    // chat:rooms의 Set에 Member로서 roomid가 존재하는지 검사
    public boolean existRoomsMember(String roomId) {
        // log.info("▼ addMember. roomId:{}, memberId:{}", roomId, memberId);
        //
        // // [addMember] 방 멤버 Set에 추가 + roomId 인덱스 유지
        // // 멤버는 Set으로 관리(중복 방지)
        // // 변경: room:{roomId}:mems → room:mems:{roomId}
        // redisTemplate.opsForSet().add(Constants.ROOM_MEMBERS_KEY_PREFIX + roomId, memberId);
        //redisTemplate.opsForSet().roomId..add(CHAT_ROOMS_KEY, roomId);
        return false;
    }

    @Override
    // user-customers:{userId} 존재하는 지 검사
    public boolean existCustomer(String userId) {
        String customerKey = Constants.USER_CUSTOMER_KEY + ":" + userId; // "chat:user-customer"
        Boolean exists = redisTemplate.hasKey(customerKey);
        if ( exists ) { // 이미 존재함
            return true;
        }

    	return false;
    }

}
