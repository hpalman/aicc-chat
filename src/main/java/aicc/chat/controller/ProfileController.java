package aicc.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aicc.chat.domain.UserInfo;
import aicc.chat.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProfileController {

    private final TokenService tokenService;

    @GetMapping("/me")
    // Authorization 헤더의 토큰을 검증해 현재 사용자 정보 반환
    public ResponseEntity<UserInfo> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▶ getCurrentUser S");

        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("token == null");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null || userInfo.getToken() == null || userInfo.getToken().isEmpty()) {
            log.warn("userInfo == null");
            return ResponseEntity.status(401).build();
        }
        log.info("◀ getCurrentUser E");
        return ResponseEntity.ok(userInfo);
    }
}

