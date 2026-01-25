package aicc.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatBotRequest {
    
   // 사용자 입력 메시지
   private String message;
   // 스트리밍 응답 여부 (문자열 플래그)
   private String stream;
   // 대화 히스토리 사용 여부 (문자열 플래그)
   private String useHistory;

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
}
