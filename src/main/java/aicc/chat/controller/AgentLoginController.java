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
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        log.info("▶ 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 시작");
        ResponseEntity<UserInfo> ret;
        UserInfo userInfo = agentAuthService.login(id, password);
        if (userInfo == null) {
            log.warn("userInfo == null");
            ret = ResponseEntity.status(401).build();
        } else {
            ret = ResponseEntity.ok(userInfo);
        }
        log.info("◀ 상담원 로그인 요청을 인증 서비스로 전달하고 토큰/프로필 반환:login 완료 ");
        return ret;
    }

    @GetMapping("/me")
    // Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환
    public ResponseEntity<UserInfo> getCurrentAgent(@RequestHeader(value = "Authorization", required = false) String token) {
        log.info("▶ Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 시작");
        ResponseEntity<UserInfo> ret;
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("token == null || !token.startsWith(\"Bearer \"))");
            log.info("◀ Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료 ");
            return ResponseEntity.status(401).build();
        }

        String actualToken = token.substring(7);
        UserInfo userInfo = tokenService.validateToken(actualToken);
        if (userInfo == null) {
            log.warn("userInfo == null");
            log.info("◀ Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료 ");
            return ResponseEntity.status(401).build();
        }

        // 하트비트 - 온라인 상태 유지
        agentAuthService.heartbeat(userInfo.getUserId());

        log.info("◀ Authorization 헤더의 토큰을 검증해 현재 상담원 정보 반환:getCurrentAgent 완료 ");
        return ResponseEntity.ok(userInfo);
    }
}

