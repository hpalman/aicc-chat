package aicc.chat.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import aicc.chat.domain.ChatRoom;
import aicc.chat.service.RoomRepository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.system-mode", havingValue = "REDIS_ONLY")
public class RedisRoomRepository implements RoomRepository {

    private final StringRedisTemplate redisTemplate;
    // Redis 키 구성: roomId별 멤버/상태/메타데이터, 그리고 전체 roomId 인덱스
    private static final String ROOM_KEY_PREFIX = "chat:room:"; // set of members
    private static final String ROOM_INDEX_KEY = "chat:rooms";   // set of roomIds

    @Override
    public ChatRoom createRoom(String name) {
        // [createRoom] name만 받은 경우 내부에서 roomId(UUID) 생성
        // roomId를 서버에서 생성(전체 UUID)
        return createRoom(UUID.randomUUID().toString(), name);
    }

    @Override
    public ChatRoom createRoom(String roomId, String name) {
        // [createRoom] roomId를 지정해 방 생성 및 메타 키 초기화
        long now = System.currentTimeMillis();
        // roomId를 전체 인덱스(Set)에 등록
        redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
        // 방 이름은 별도 문자열 키로 저장(옵션)
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
        // [findRoomById] Redis의 분산 키들을 조회해 ChatRoom으로 합성
        // roomId에 해당하는 멤버/상태/메타 정보를 조합해 ChatRoom으로 복원
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
        // [addMember] 방 멤버 Set에 추가 + roomId 인덱스 유지
        // 멤버는 Set으로 관리(중복 방지)
        redisTemplate.opsForSet().add(ROOM_KEY_PREFIX + roomId, memberId);
        redisTemplate.opsForSet().add(ROOM_INDEX_KEY, roomId);
    }

    @Override
    public void removeMember(String roomId, String memberId) {
        // [removeMember] 방 멤버 Set에서 제거
        redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId, memberId);
    }

    @Override
    public void removeMemberFromAll(String memberId) {
        // [removeMemberFromAll] 인덱스의 모든 roomId를 순회하며 해당 멤버 제거
        // 멤버가 들어간 모든 방에서 제거 (Set 전체 순회)
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_INDEX_KEY))
                .orElse(Collections.emptySet());
        for (String roomId : roomIds) {
            redisTemplate.opsForSet().remove(ROOM_KEY_PREFIX + roomId, memberId);
        }
    }

    @Override
    public List<ChatRoom> findAllRooms() {
        // [findAllRooms] roomId 인덱스를 읽어 ChatRoom 목록 구성
        // 인덱스 Set에 있는 roomId들을 조회해 목록 구성
        Set<String> roomIds = Optional.ofNullable(redisTemplate.opsForSet().members(ROOM_INDEX_KEY))
                .orElse(Collections.emptySet());
        return roomIds.stream()
                .map(this::findRoomById)
                .collect(Collectors.toList());
    }

    @Override
    public void setRoutingMode(String roomId, String mode) {
        // [setRoutingMode] 방의 라우팅 상태 저장
        // 방 상태(BOT/WAITING/AGENT/CLOSED 등) 저장
        if (roomId != null && mode != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":mode", mode);
        }
    }

    @Override
    public String getRoutingMode(String roomId) {
        // [getRoutingMode] 방의 라우팅 상태 조회
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":mode") : null;
    }

    @Override
    public void setAssignedAgent(String roomId, String agentName) {
        // [setAssignedAgent] 방에 배정된 상담원 저장
        // 배정 상담원 저장
        if (roomId != null && agentName != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":assignedAgent", agentName);
        }
    }

    @Override
    public String getAssignedAgent(String roomId) {
        // [getAssignedAgent] 방에 배정된 상담원 조회
        return roomId != null ? redisTemplate.opsForValue().get(ROOM_KEY_PREFIX + roomId + ":assignedAgent") : null;
    }

    @Override
    public boolean assignAgent(String roomId, String agentName) {
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
        // [updateLastActivity] 방의 마지막 활동 시간 갱신
        // 마지막 활동 시간 갱신(밀리초)
        if (roomId != null) {
            redisTemplate.opsForValue().set(ROOM_KEY_PREFIX + roomId + ":lastActivity", String.valueOf(System.currentTimeMillis()));
        }
    }

    @Override
    public void deleteRoom(String roomId) {
        // [deleteRoom] roomId 인덱스와 관련 메타 키 삭제
        // 방 관련 키 일괄 삭제(인덱스 + 멤버/메타)
        redisTemplate.opsForSet().remove(ROOM_INDEX_KEY, roomId);
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId);
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":name");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":mode");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":assignedAgent");
        redisTemplate.delete(ROOM_KEY_PREFIX + roomId + ":lastActivity");
    }
}
