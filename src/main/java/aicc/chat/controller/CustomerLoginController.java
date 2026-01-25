package aicc.chat.controller;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.CustomerAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        UserInfo userInfo = customerAuthService.login(id, password, companyId);
        if (userInfo == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/login")
    // 기본 회사(default)로 고객 로그인 처리
    public ResponseEntity<UserInfo> loginDefault(
            @RequestParam String id,
            @RequestParam String password) {
        return login("default", id, password);
    }
}

