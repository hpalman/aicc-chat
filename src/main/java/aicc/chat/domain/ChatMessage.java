package aicc.chat.domain;

import lombok.*;

import java.time.LocalDateTime;

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
    private LocalDateTime timestamp; // 메시지 발행 시간 (서버에서 설정)
}
