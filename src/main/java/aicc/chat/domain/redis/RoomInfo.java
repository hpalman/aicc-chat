package aicc.chat.domain.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// chat:room-info:{roomId} 값 처리용
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfo {

    private String assignedAgent  ;// = (String) map.get("assignedAgent");
    private String name           ;// = (String) map.get("name"         );
    private String createdAt      ;// = (String) map.get("createdAt"    );
    private String creatorId      ;// = (String) map.get("creatorId"    );
    private String lastActivity   ;// = (String) map.get("lastActivity" );
    private String status         ;// = (String) map.get("routingMode"  );

    private String routingMode    ;

}
