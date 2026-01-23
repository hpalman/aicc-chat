package aicc.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.AgentAuthService;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent")
public class AgentLoginController {

    private final AgentAuthService agentAuthService;
    private final TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<UserInfo> login(
            @RequestParam String id,
            @RequestParam String password) {
        return ResponseEntity.ok(agentAuthService.login(id, password));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null) {
            return ResponseEntity.status(401).build();
        }
        
        return ResponseEntity.ok(userInfo);
    }
}

