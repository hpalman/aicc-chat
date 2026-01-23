package aicc.bot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MiChat API 전용 요청 구조체
 * ChatBotServiceImpl과 동일한 요청 구조를 생성하기 위해 null 필드는 JSON에서 제외
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MiChatAskRequest {
    private ChatConfig chat;
    private MetaConfig meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatConfig {
        private String message;
        private boolean stream;
        private boolean useHistory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaConfig {
        private String category1;
        private String category2;
        private String companyId;
        private String sessionId;
        private String userId;
        private String ragSysInfo;
    }
}

