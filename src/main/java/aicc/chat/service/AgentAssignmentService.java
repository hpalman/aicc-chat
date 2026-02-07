package aicc.chat.service;

import aicc.chat.consts.Constants;
import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.service.inteface.MessageBroker;
import aicc.chat.service.inteface.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 상담사 자동 배정 서비스
 * 순환 참조를 방지하기 위해 별도 서비스로 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssignmentService {

    private final StringRedisTemplate redisTemplate;
    private final RoomRepository roomRepository;
    private final MessageBroker messageBroker;
    private final RoomUpdateBroadcaster roomUpdateBroadcaster;

    /**
     * 대기 중인 상담사 중 첫 번째 상담사 조회
     * @return 상담사 ID, 없으면 null
     */
    public String findWaitingAgent() {
        Set<String> onlineAgentKeys = redisTemplate.keys(Constants.USER_AGENT_KEY + ":*");
        
        if (onlineAgentKeys != null) {
            for (String key : onlineAgentKeys) {
                String agentId = key.substring((Constants.USER_AGENT_KEY + ":").length());
                
                // Hash에서 agentStatus 조회
                Object agentStatusObj = redisTemplate.opsForHash().get(key, "agentStatus");
                String agentStatus = agentStatusObj != null ? agentStatusObj.toString() : null;
                
                // agentStatus가 WAITING인 경우 반환
                if ("WAITING".equals(agentStatus)) {
                    log.info("▶ Found WAITING agent: {}", agentId);
                    return agentId;
                }
            }
        }
        
        log.info("▶ No WAITING agent found");
        return null;
    }

    /**
     * 대기 중인 상담사를 자동으로 배정
     * @param roomId 채팅방 ID
     * @return 배정 성공 여부
     */
    public boolean autoAssignWaitingAgent(String roomId) {
        log.info("▼ autoAssignWaitingAgent S. roomId:{}", roomId);
        
        // 1. 대기 중인 상담사 조회
        String agentId = findWaitingAgent();
        if (agentId == null) {
            log.info("▶ No WAITING agent available for auto-assignment");
            return false;
        }
        
        // 2. 상담사 userName 조회
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId;
        Object userNameObj = redisTemplate.opsForHash().get(agentKey, "userName");
        String agentName = userNameObj != null ? userNameObj.toString() : agentId;
        
        log.info("▶ Auto-assigning WAITING agent: {} ({}) to room: {}", agentName, agentId, roomId);
        
        // 3. 상담사 배정 시도
        boolean success = roomRepository.assignAgent(roomId, agentId);
        if (success) {
            // 4. 상담사 상태를 WORKING으로 변경
            setAgentStatus(agentId, "WORKING");
            
            // 5. 고객에게 연결 알림 메시지 발송
            ChatMessage customerNotice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message(agentName + " 상담사와 연결되었습니다.")
                    .type(MessageType.TALK)
                    .timestamp(LocalDateTime.now())
                    .build();
            messageBroker.publish(customerNotice);
            
            // 6. 상담사에게 자동 배정 알림 메시지 발송
            ChatMessage agentNotice = ChatMessage.builder()
                    .roomId(roomId)
                    .sender("System")
                    .senderRole(UserRole.SYSTEM)
                    .message("자동으로 고객과의 채팅이 배정되어 상담을 시작합니다.")
                    .type(MessageType.AGENT_AUTO_ASSIGNED)
                    .timestamp(LocalDateTime.now())
                    .build();
            messageBroker.publish(agentNotice);
            
            // 7. 방 목록 브로드캐스트
            roomUpdateBroadcaster.broadcastRoomList();
            
            log.info("▲ autoAssignWaitingAgent E. Successfully assigned agent {} to room {}", agentName, roomId);
        } else {
            log.warn("▲ autoAssignWaitingAgent E. Failed to assign agent {} to room {}", agentName, roomId);
        }
        
        return success;
    }

    /**
     * 상담사 상태 변경
     * @param agentId 상담사 ID
     * @param status 상태 (WAITING, WORKING)
     */
    private void setAgentStatus(String agentId, String status) {
        String agentKey = Constants.USER_AGENT_KEY + ":" + agentId;
        
        Boolean exists = redisTemplate.hasKey(agentKey);
        if (!exists) {
            log.warn("▶ Agent key not found: {}", agentKey);
            return;
        }
        
        Object currentStatusObj = redisTemplate.opsForHash().get(agentKey, "agentStatus");
        String currentStatus = currentStatusObj != null ? currentStatusObj.toString() : null;
        
        if (!status.equals(currentStatus)) {
            redisTemplate.opsForHash().put(agentKey, "agentStatus", status);
            log.info("▶ Agent {} status changed: {} -> {}", agentId, currentStatus, status);
            
            // 상담사 상태 변경 알림 브로드캐스트
            ChatMessage statusMessage = ChatMessage.builder()
                .roomId("SYSTEM_BROADCAST")
                .sender("System")
                .senderRole(UserRole.SYSTEM)
                .message("AGENT_STATUS")
                .type(MessageType.SYSTEM)
                .timestamp(LocalDateTime.now())
                .build();
            messageBroker.publish(statusMessage);
        }
    }
}
