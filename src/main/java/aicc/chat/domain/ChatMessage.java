package aicc.chat.domain;

import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private String roomId;
    private String sender;
    private UserRole senderRole; // CUSTOMER, AGENT, BOT, SYSTEM
    private String message;
    private MessageType type;
    private String companyId;
}
