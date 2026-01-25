package aicc.chat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import aicc.chat.domain.persistence.UserAccount;

/**
 * 사용자 계정 MyBatis Mapper 인터페이스
 */
@Mapper
public interface UserAccountMapper {
    /**
     * 상담원 로그인용 사용자 조회 (활성 계정)
     */
    UserAccount selectAgentByLogin(
            @Param("userId") String userId,
            @Param("password") String password
    );

    /**
     * 고객 로그인용 사용자 조회 (회사별, 활성 계정)
     */
    UserAccount selectCustomerByLogin(
            @Param("userId") String userId,
            @Param("password") String password,
            @Param("companyId") String companyId
    );
}
