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
    
   private String message;
   private String stream;
   private String useHistory;

   private String category1;
   private String category2;
   private String companyId;
   private String sessionId;
   private String userId;
}
