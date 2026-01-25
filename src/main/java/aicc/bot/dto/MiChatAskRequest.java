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
    // 대화 요청 설정
    private ChatConfig chat;
    // 메타데이터(회사/세션/사용자 등)
    private MetaConfig meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatConfig {
        // 사용자 입력 메시지
        private String message;
        // 스트리밍 응답 사용 여부
        private boolean stream;
        // 이전 대화 히스토리 사용 여부
        private boolean useHistory;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaConfig {
        // 1차 카테고리
        private String category1;
        // 2차 카테고리
        private String category2;
        // 회사 식별자
        private String companyId;
        // 세션 식별자
        private String sessionId;
        // 사용자 식별자
        private String userId;
        // RAG 시스템 정보
        private String ragSysInfo;
    }
}

