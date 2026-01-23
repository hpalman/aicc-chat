package aicc.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private String userId;
    private String userName;
    private UserRole role;
    private String email;
    private String token;
    private String roomId;
    private String companyId;
}

