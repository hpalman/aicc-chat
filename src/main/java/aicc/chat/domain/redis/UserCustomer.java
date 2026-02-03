package aicc.chat.domain.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// chat:user-customer의 개별 정보
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCustomer {
    private String assignedAgent  ; //  = (String) userCustomer.get("assignedAgent");
    private String roomId         ; //  = (String) userCustomer.get("roomId"       );
    private String name           ; //  = (String) userCustomer.get("name"         );
    private String createdAtStr   ; //  = (String) userCustomer.get("createdAt"    );
    private String creatorId      ; //  = (String) userCustomer.get("creatorId"    );
    private String lastActivityStr; //  = (String) userCustomer.get("lastActivity" );
    private String status         ; //  = (String) userCustomer.get("routingMode"  );

    private String companyId;
    private String loginTime;
    private String userName;
    //private String roomId;
    private String email;
}
