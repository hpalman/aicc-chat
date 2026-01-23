package aicc.chat.config.mode;

import aicc.chat.domain.ChatRoom;
import aicc.chat.service.MessageBroker;
import aicc.chat.service.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(name = "app.system-mode", havingValue = "IN_MEMORY")
@RequiredArgsConstructor
public class InMemoryConfig {

    private final SimpMessagingTemplate messagingTemplate;

    @Bean
    public RoomRepository roomRepository() {
        return new RoomRepository() {
            private final Map<String, ChatRoom> rooms = new ConcurrentHashMap<>();

            @Override
            public ChatRoom createRoom(String name) {
                return createRoom(UUID.randomUUID().toString(), name);
            }

            @Override
            public ChatRoom createRoom(String roomId, String name) {
                long now = System.currentTimeMillis();
                ChatRoom room = ChatRoom.builder()
                        .roomId(roomId)
                        .roomName(name)
                        .members(ConcurrentHashMap.newKeySet())
                        .status("BOT")
                        .createdAt(now)
                        .lastActivityAt(now)
                        .build();
                rooms.put(roomId, room);
                return room;
            }

            @Override
            public ChatRoom findRoomById(String roomId) {
                ChatRoom room = rooms.get(roomId);
                if (room != null) {
                    String status = roomModes.get(roomId);
                    String assignedAgent = assignedAgents.get(roomId);
                    return ChatRoom.builder()
                            .roomId(room.getRoomId())
                            .roomName(room.getRoomName())
                            .members(room.getMembers())
                            .status(status == null ? "BOT" : status)
                            .assignedAgent(assignedAgent)
                            .createdAt(room.getCreatedAt())
                            .lastActivityAt(room.getLastActivityAt())
                            .build();
                }
                return null;
            }

            @Override
            public void addMember(String roomId, String memberId) {
                if(rooms.containsKey(roomId)) {
                    rooms.get(roomId).getMembers().add(memberId);
                }
            }

            @Override
            public void removeMember(String roomId, String memberId) {
                if(rooms.containsKey(roomId)) {
                    rooms.get(roomId).getMembers().remove(memberId);
                }
            }

            @Override
            public void removeMemberFromAll(String memberId) {
                rooms.values().forEach(room -> room.getMembers().remove(memberId));
            }

            @Override
            public List<ChatRoom> findAllRooms() {
                return rooms.keySet().stream()
                        .map(this::findRoomById)
                        .collect(Collectors.toList());
            }

            private final Map<String, String> roomModes = new ConcurrentHashMap<>();
            private final Map<String, String> assignedAgents = new ConcurrentHashMap<>();

            @Override
            public void setRoutingMode(String roomId, String mode) {
                roomModes.put(roomId, mode);
            }

            @Override
            public String getRoutingMode(String roomId) {
                return roomModes.get(roomId);
            }

            @Override
            public void setAssignedAgent(String roomId, String agentName) {
                assignedAgents.put(roomId, agentName);
            }

            @Override
            public String getAssignedAgent(String roomId) {
                return assignedAgents.get(roomId);
            }

            @Override
            public synchronized boolean assignAgent(String roomId, String agentName) {
                if (assignedAgents.containsKey(roomId)) {
                    return false;
                }
                assignedAgents.put(roomId, agentName);
                roomModes.put(roomId, "AGENT");
                updateLastActivity(roomId);
                return true;
            }

            @Override
            public void updateLastActivity(String roomId) {
                ChatRoom room = rooms.get(roomId);
                if (room != null) {
                    ChatRoom updated = ChatRoom.builder()
                            .roomId(room.getRoomId())
                            .roomName(room.getRoomName())
                            .members(room.getMembers())
                            .status(room.getStatus())
                            .assignedAgent(room.getAssignedAgent())
                            .lastActivityAt(System.currentTimeMillis())
                            .build();
                    rooms.put(roomId, updated);
                }
            }

            @Override
            public void deleteRoom(String roomId) {
                rooms.remove(roomId);
                roomModes.remove(roomId);
                assignedAgents.remove(roomId);
            }
        };
    }

    @Bean
    public MessageBroker messageBroker() {
        return message -> messagingTemplate.convertAndSend("/topic/room/" + message.getRoomId(), message);
    }
}