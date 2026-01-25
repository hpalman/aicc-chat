package aicc.chat.domain.persistence;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 계정 도메인 모델
 * 고객/상담원 계정 정보를 저장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    private Long id;
    private String userId;
    private String userName;
    private String password;
    private String role; // CUSTOMER, AGENT
    private String email;
    private String companyId;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
