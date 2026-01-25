package aicc.chat.service;

import aicc.chat.domain.ChatRoom;

public interface RoomRepository {
    // 이름으로 새 방 생성(내부에서 roomId 생성)
    ChatRoom createRoom(String name);
    // 지정한 roomId로 방 생성
    ChatRoom createRoom(String roomId, String name); // ID 지정 생성 추가
    // roomId로 방 조회
    ChatRoom findRoomById(String roomId);
    // 방에 멤버 추가
    void addMember(String roomId, String memberId);
    // 방에서 멤버 제거
    void removeMember(String roomId, String memberId);
    // 모든 방에서 특정 멤버 제거
    void removeMemberFromAll(String memberId);
    // 전체 방 목록 조회
    java.util.List<ChatRoom> findAllRooms();
    
    // 라우팅 모드 및 상태 관리 추가
    // 방 라우팅 모드 설정
    void setRoutingMode(String roomId, String mode);
    // 방 라우팅 모드 조회
    String getRoutingMode(String roomId);
    // 방에 배정된 상담원 설정
    void setAssignedAgent(String roomId, String agentName);
    // 방에 배정된 상담원 조회
    String getAssignedAgent(String roomId);
    // 원자적으로 상담원 배정 시도
    boolean assignAgent(String roomId, String agentName); // 원자적 배정 추가
    
    // 방의 마지막 활동 시간 갱신
    void updateLastActivity(String roomId);
    // 방 삭제
    void deleteRoom(String roomId);
}
