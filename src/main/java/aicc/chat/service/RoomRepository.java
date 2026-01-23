package aicc.chat.service;

import aicc.chat.domain.ChatRoom;

public interface RoomRepository {
    ChatRoom createRoom(String name);
    ChatRoom createRoom(String roomId, String name); // ID 지정 생성 추가
    ChatRoom findRoomById(String roomId);
    void addMember(String roomId, String memberId);
    void removeMember(String roomId, String memberId);
    void removeMemberFromAll(String memberId);
    java.util.List<ChatRoom> findAllRooms();
    
    // 라우팅 모드 및 상태 관리 추가
    void setRoutingMode(String roomId, String mode);
    String getRoutingMode(String roomId);
    void setAssignedAgent(String roomId, String agentName);
    String getAssignedAgent(String roomId);
    boolean assignAgent(String roomId, String agentName); // 원자적 배정 추가
    
    void updateLastActivity(String roomId);
    void deleteRoom(String roomId);
}
