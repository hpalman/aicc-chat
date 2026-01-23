package aicc.chat.handler;

import org.springframework.stereotype.Component;

// @Component
public class JwtTokenProvider {
/*
    private final String secretKey = "your-very-long-secret-key-minimum-256-bits-for-HS512"; // application.yml에서 관리 권장
    private final long accessTokenValidity = 1000 * 60 * 60; // 1시간
    private final Key key;

    public JwtTokenProvider() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // 토큰 생성
    public String createToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidity);

        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    // 토큰 검증 및 Claims 추출
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    // Subject (userId) 추출
    public String getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getSubject();
    }

    // 토큰 유효성 검사
    public boolean isTokenValid(String token, String userId) {
        final String realUserId = getUserIdFromToken(token);
        return (userId.equals(realUserId) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return validateToken(token).getExpiration().before(new Date());
    }
*/}
