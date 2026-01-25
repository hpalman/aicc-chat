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
    // 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환
    public ResponseEntity<UserInfo> login(
            @RequestParam String id,
            @RequestParam String password) {
        UserInfo userInfo = agentAuthService.login(id, password);
        if (userInfo == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/me")
    // Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환
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

