package aicc.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.CustomerAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer")
public class CustomerLoginController {

    private final CustomerAuthService customerAuthService;

    @PostMapping("/{companyId}/login")
    // 회사별 고객 로그인 처리
    public ResponseEntity<UserInfo> login(
            @PathVariable String companyId,
            @RequestParam String id,
            @RequestParam String password) {
        log.info("▶ 회사별 고객 로그인 처리:login 시작");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = customerAuthService.login(id, password, companyId);
        if (userInfo == null) {
            ret = ResponseEntity.status(401).build();
        } else {
            ret = ResponseEntity.ok(userInfo);
        }
        log.info("◀ 회사별 고객 로그인 처리:login 완료 ");
        return ret;
    }

    @PostMapping("/login")
    // 기본 회사(default)로 고객 로그인 처리
    public ResponseEntity<UserInfo> loginDefault(
            @RequestParam String id,
            @RequestParam String password) {
        ResponseEntity<UserInfo> ret;
        log.info("▶ 기본 회사(default)로 고객 로그인 처리:loginDefault 시작");
        ret = login("default", id, password);
        log.info("◀ 기본 회사(default)로 고객 로그인 처리:loginDefault 완료 ");
        return ret;
    }
}

