package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        String secret = jwtProperties.getJwt().getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thiếu cấu hình JWT_SECRET_KEY");
        }
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("JWT_SECRET_KEY must be a valid Base64-encoded secret", e);
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "JWT_SECRET_KEY phải là chuỗi Base64 hợp lệ", e);
        }
    }

    public String generateAccessToken(UUID userId, String email, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getJwt().getExpiration());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("email", email);
        claims.put("roles", roles);
        claims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuer(jwtProperties.getJwt().getIssuer())
                .setAudience(jwtProperties.getJwt().getAudience())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String hashToken(String token) {
        if (token == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Thuật toán băm SHA-256 không khả dụng", e);
        }
    }

    public boolean validateAccessToken(String token) {
        try {
            validateAccessClaims(parseClaims(token));
            return true;
        } catch (MalformedJwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.info("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("JWT parsing error: {}", ex.getMessage());
        }
        return false;
    }

    public Claims getClaimsFromToken(String token) {
        Jws<Claims> claimsJws = parseClaims(token);
        validateAccessClaims(claimsJws);
        return claimsJws.getBody();
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
    }

    private void validateAccessClaims(Jws<Claims> claimsJws) {
        if (!SignatureAlgorithm.HS256.getValue().equalsIgnoreCase(claimsJws.getHeader().getAlgorithm())) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Thuật toán JWT không hợp lệ. Hệ thống chỉ chấp nhận HS256.");
        }

        Claims claims = claimsJws.getBody();
        String expectedIssuer = jwtProperties.getJwt().getIssuer();
        String expectedAudience = jwtProperties.getJwt().getAudience();

        if (StringUtils.hasText(expectedIssuer) && !expectedIssuer.equals(claims.getIssuer())) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Issuer của JWT không hợp lệ");
        }

        if (StringUtils.hasText(expectedAudience) && !expectedAudience.equals(claims.getAudience())) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Audience của JWT không hợp lệ");
        }

        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Token JWT không đúng mục đích sử dụng");
        }
    }
}
