package aicc.chat.websocket.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class WebSocketSessionAttribute {
    private String sessionId;
    private String destination;
    private String command;

    private String userName;
    private String userId;
    private String roomId;
    private String companyId;
    private String userEmail;
    private String userRole; // CUSTOMER, AGENT, BOT, SYSTEM
}
