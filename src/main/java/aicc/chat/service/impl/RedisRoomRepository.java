package aicc.chat.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import aicc.chat.domain.ChatRoom;
import aicc.chat.service.RoomRepository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.system-mode}'.equals('REDIS_ONLY') || '${app.system-mode}'.equals('REDIS_RABBIT')")
public class RedisRoomRepository implements RoomRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String ROOM_KEY_PREFIX = "chat:room:"; // set of members
    private static final String ROOM_INDEX_KEY = "chat:rooms";   // set of roomIds

    @Override
    public ChatRoom createRoom(String name) {
        return createRoom(UUID.randomUUID().toString(), name);
    }

    @Override
    public ChatRoom createRoom(String roomId, String name) {
        long now = System.currentTimeMillis();
        // register room id
        redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
        // optional: store name as string key
        if (name != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":name", name);
        }
        redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":createdAt", String.valueOf(now));
        updateLastActivity(roomId);
        return ChatRoom.builder()
                .roomId(roomId)
                .roomName(name)
                .members(new HashSet<>())
                .status("BOT")
                .createdAt(now)
                .lastActivityAt(now)
                .build();
    }

    @Override
    public ChatRoom findRoomById(String roomId) {
        Set<String> members = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_KEY_PREFIX + roomId))
                .orElse(Collections.emptySet());
        String name = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":name");
        String status = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":mode");
        String assignedAgent = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        String createdAtStr = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":createdAt");
        String lastActivityStr = redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":lastActivity");
        long createdAt = createdAtStr != null ? Long.parseLong(createdAtStr) : 0;
        long lastActivityAt = lastActivityStr != null ? Long.parseLong(lastActivityStr) : 0;
        
        return ChatRoom.builder()
                .roomId(roomId)
                .roomName(name == null ? roomId : name)
                .members(members)
                .status(status == null ? "BOT" : status)
                .assignedAgent(assignedAgent)
                .createdAt(createdAt)
                .lastActivityAt(lastActivityAt)
                .build();
    }

    @Override
    public void addMember(String roomId, String memberId) {
        redisTemplate.opsForSet().add(ROOM_KEY_PREFIX + roomId, memberId);
        redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
    }

    @Override
    public void removeMember(String roomId, String memberId) {
        redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId, memberId);
    }

    @Override
    public void removeMemberFromAll(String memberId) {
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_INDEX_KEY))
                .orElse(Collections.emptySet());
        for (String roomId : roomIds) {
            redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId, memberId);
        }
    }

    @Override
    public List<ChatRoom> findAllRooms() {
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_INDEX_KEY))
                .orElse(Collections.emptySet());
        return roomIds.stream()
                .map(this::findRoomById)
                .collect(Collectors.toList());
    }

    @Override
    public void setRoutingMode(String roomId, String mode) {
        if (roomId != null && mode != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":mode", mode);
        }
    }

    @Override
    public String getRoutingMode(String roomId) {
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":mode") : null;
    }

    @Override
    public void setAssignedAgent(String roomId, String agentName) {
        if (roomId != null && agentName != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":assignedAgent", agentName);
        }
    }

    @Override
    public String getAssignedAgent(String roomId) {
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":assignedAgent") : null;
    }

    @Override
    public boolean assignAgent(String roomId, String agentName) {
        if (roomId == null || agentName == null) return false;
        // NX (setIfAbsent)를 사용하여 이미 배정된 경우 실패하도록 처리 (원자적)
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
        if (roomId != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":lastActivity", String.valueOf(System.currentTimeMillis()));
        }
    }

    @Override
    public void deleteRoom(String roomId) {
        redisTemplate.opsForSet().remove(ROOM_INDEX_KEY, roomId);
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId);
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":name");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":mode");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":lastActivity");
    }
}
