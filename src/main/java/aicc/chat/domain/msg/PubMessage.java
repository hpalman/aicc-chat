package aicc.chat.domain.msg;

import java.util.List;

import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 *  서버에서 PUBLISH 할 때 사용
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PubMessage {
    private String        msgCode;
    private String        targetTopic; // 2026.02.05 허) 발행 토픽명
    private ChatMessage   chatMessage; // 어떤 것도 담자 잉!
    private List<ChatRoom> rooms;

    public PubMessage(String msgCode, ChatMessage chatMessage) {
        super();
        this.msgCode     = msgCode;
        this.chatMessage = chatMessage;
    }
    public PubMessage(String msgCode, String targetTopic, List<ChatRoom> rooms) {
        super();
        this.msgCode     = msgCode;
        this.targetTopic = targetTopic;
        this.rooms       = rooms;
    }
}
