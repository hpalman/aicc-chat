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
    public ResponseEntity<UserInfo> login(
            @PathVariable String companyId,
            @RequestParam String id,
            @RequestParam String password) {
        return ResponseEntity.ok(customerAuthService.login(id, password, companyId));
    }

    @PostMapping("/login")
    public ResponseEntity<UserInfo> loginDefault(
            @RequestParam String id,
            @RequestParam String password) {
        return login("default", id, password);
    }
}

