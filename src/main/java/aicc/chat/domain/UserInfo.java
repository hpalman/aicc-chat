package aicc.chat.domain;

import aicc.chat.util.UtilString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private String   userId;
    private String   userName;
    private UserRole role;
    private String   email;
    private String   token;
    private String   roomId; // 고객의 경우 로그인시에 만들어야 함.
    private String   companyId;
    private int      status;

    /**
     * 토큰값이 길어서 짧게 출력하고자 별도로 만듦
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        UtilString.appendNameValue(sb, "userId"   , userId);
        UtilString.appendNameValue(sb, "userName" , userName);

        if ( role != null) {
            if ( sb.length() > 0) {
                sb.append(",");
            }
            sb.append( String.format("%s=%s", "role", role) );
        }

        UtilString.appendNameValue(sb, "email"    , email);
        UtilString.appendNameValue(sb, "token"    , UtilString.leftRight(token,15,5));
        UtilString.appendNameValue(sb, "roomId"   , roomId);
        UtilString.appendNameValue(sb, "companyId", companyId);
        UtilString.appendNameValue(sb, "status"   , status);

        return "UserInfo{" + sb.toString() + "}";
    }
}
