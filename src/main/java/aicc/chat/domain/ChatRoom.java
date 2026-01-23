package aicc.chat.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String roomId;
    private String roomName;
    private Set<String> members;
    private String status; // BOT, WAITING, AGENT, CLOSED
    private String assignedAgent; // 현재 상담중인 상담원명
    private long createdAt; // 방 생성 시간
    private long lastActivityAt; // 마지막 활동 시간
    
    // members에서 custId 추출 (첫 번째 member가 고객)
    @JsonProperty("custId")
    public String getCustId() {
        return members != null && !members.isEmpty() ? members.iterator().next() : null;
    }
}
