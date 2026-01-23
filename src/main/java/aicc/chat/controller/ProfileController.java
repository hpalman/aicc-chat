package aicc.chat.controller;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProfileController {

    private final TokenService tokenService;

    @GetMapping("/me")
    public ResponseEntity<UserInfo> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        
        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getToken() == null || userInfo.getToken().isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        
        return ResponseEntity.ok(userInfo);
    }
}

