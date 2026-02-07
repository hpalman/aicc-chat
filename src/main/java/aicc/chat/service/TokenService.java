package aicc.chat.service;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.chat.domain.UserInfo;
import aicc.chat.domain.UserRole;
import aicc.chat.util.UtilString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final ObjectMapper objectMapper;

    public String generateToken(UserInfo userInfo) {
        log.info("▼ generateToken S.");
        // UserInfo를 Base64 JSON 토큰으로 생성
        try {
            String json = objectMapper.writeValueAsString(userInfo);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            log.error("▲ generateToken E.", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Bearer 헤드가 붙은 토큰의 실 토큰이 유효한지 확인
     * @param bearerToken값 "Bearer "로 시작함
     * @return
     */
    public boolean isValidBearerToken(String bearerToken) {
        if ( bearerToken != null && bearerToken.length() > 0 && bearerToken.startsWith("Bearer ")) {
            return true;
        }
        log.warn("§ Not Valid bearerToken:{}", bearerToken);
        return false;
    }

    /**
     * Bearer 헤드가 붙은 토큰의 실 토큰이 유효한지 확인하여 토큰에서 UserInfo 분석하여 반환
     * @param token
     * @return
     */
    public UserInfo parseToken(String bearerToken) {
        String actualToken = bearerToken.substring(7);
        if ( log.isDebugEnabled() )
            log.debug("▼ parseToken S. bearerToken:{}, actualToken:{}",
                    UtilString.emitToken(bearerToken), UtilString.emitToken(actualToken));
        UserInfo userInfo = _parseToken(actualToken);
        if ( log.isDebugEnabled() )
            log.debug("▲ parseToken E. userInfo:{}", userInfo);
        return userInfo;
    }

    private UserInfo _parseToken(String token) {
        // log.info("▶ validateToken S. token:{}", token);
        // JWT 또는 Base64 토큰을 파싱해 UserInfo로 복원
        if (token == null || token.isEmpty()) {
            log.warn("§ validateToken E.");
            return null;
        }
        try {
            // 1. JWT 토큰 처리 (ey... 로 시작)
            if (token.startsWith("ey") && token.contains(".")) {
                log.info("§ _parseToken. [TokenService] JWT token detected");
                return parseJwtToken(token);
            }

            log.info("§ [TokenService] Non-JWT token detected (Base64)");
            // 2. 기존의 단순 Base64 JSON 토큰 처리
            byte[] decodedBytes = Base64.getDecoder().decode(token);
            String json = new String(decodedBytes);
            UserInfo userInfo = objectMapper.readValue(json, UserInfo.class);
            userInfo.setToken(token);

            return userInfo;
        } catch (Exception e) {
            log.error("§ _parseToken error.", e);
            return null;
        }
    }

    private UserInfo parseJwtToken(String token) {
        log.info("▼ parseJwtToken. token:{}", UtilString.emitToken(token));
        // JWT payload에서 사용자 정보를 추출해 UserInfo로 변환
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;

            // Payload는 두 번째 파트
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, Map.class);

            // 전체 payload 로깅
            log.info("▶ [TokenService] Full JWT payload: {}", payload);

            String userId = (String) payload.get("sub");
            String authorities = (String) payload.get("auth");

            log.info("▶ [TokenService] Parsing JWT - sub: {}, auth: {}", userId, authorities);

            // principal 맵에서 이름과 로그인 ID 추출 시도
            String userName = userId;
            String loginId = userId; // 기본값은 userId
            Map<String, Object> principal = (Map<String, Object>) payload.get("principal");

            if (principal != null) {
                log.info("▶ [TokenService] Principal object: {}", principal);

                if (principal.get("mbrNm") != null) {
                    userName = (String) principal.get("mbrNm");
                }

                // 로그인 ID 추출 시도 (여러 가능한 키 확인)
                if (principal.get("lgnId") != null) {
                    loginId = (String) principal.get("lgnId");
                    log.info("▶ [TokenService] Found lgnId: {}", loginId);
                } else if (principal.get("lgn_id") != null) {
                    loginId = (String) principal.get("lgn_id");
                    log.info("▶ [TokenService] Found lgn_id: {}", loginId);
                } else if (principal.get("loginId") != null) {
                    loginId = (String) principal.get("loginId");
                    log.info("▶ [TokenService] Found loginId: {}", loginId);
                } else if (principal.get("username") != null) {
                    loginId = (String) principal.get("username");
                    log.info("▶ [TokenService] Found username: {}", loginId);
                } else {
                    log.warn("▶ [TokenService] Could not find login ID in principal, using sub: {}", userId);
                }
            } else {
                log.warn("▶ [TokenService] Principal object is null");
            }

            log.info("▶ [TokenService] Final extracted - loginId: {}, userName: {}", loginId, userName);

            // 권한에 따라 역할 구분 (상담사 권한 키워드 체크)
            UserRole role = UserRole.CUSTOMER;
            if (authorities != null) {
                String upperAuth = authorities.toUpperCase();
                if (upperAuth.contains("ROLE_ADM") ||
                    upperAuth.contains("ROLE_ADMIN") ||
                    upperAuth.contains("ROLE_AGENT") ||
                    upperAuth.contains("ROLE_CONSULTANT") ||
                    upperAuth.contains("ROLE_MBR")) {
                    role = UserRole.AGENT;
                }
            }

            log.info("▲ parseJwtToken E.");
            return UserInfo.builder()
                    .userId(loginId) // 로그인 ID를 userId로 사용
                    .userName(userName)
                    .role(role)
                    .companyId("SYSTEM") // 기본값
                    .token(token)
                    .build();
        } catch (Exception e) {
            log.error("▲ parseJwtToken E. Failed to parse JWT token", e);
            return null;
        }
    }
}

