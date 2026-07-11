package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    @Test
    void generateAccessToken_shouldEmbedIssuerAudienceAndAccessTokenType() {
        JwtProperties properties = buildProperties("skillswap", "skillswap-api");
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        String token = provider.generateAccessToken(UUID.fromString("0190a7c4-0000-7000-8000-000000000001"),
                "user@test.com",
                List.of("MENTEE"));

        Claims claims = provider.getClaimsFromToken(token);
        assertEquals("skillswap", claims.getIssuer());
        assertEquals("skillswap-api", claims.getAudience());
        assertEquals("ACCESS", claims.get("tokenType", String.class));
    }

    @Test
    void validateAccessToken_shouldRejectWrongIssuerOrAudience() {
        JwtProperties expected = buildProperties("skillswap", "skillswap-api");
        JwtProperties other = buildProperties("other-issuer", "other-audience");
        JwtTokenProvider expectedProvider = new JwtTokenProvider(expected);
        JwtTokenProvider otherProvider = new JwtTokenProvider(other);

        String tamperedToken = otherProvider.generateAccessToken(
                UUID.fromString("0190a7c4-0000-7000-8000-000000000002"),
                "user@test.com",
                List.of("MENTEE")
        );

        assertFalse(expectedProvider.validateAccessToken(tamperedToken));
    }

    private JwtProperties buildProperties(String issuer, String audience) {
        JwtProperties properties = new JwtProperties();
        properties.getJwt().setSecretKey(base64Secret());
        properties.getJwt().setExpiration(900_000L);
        properties.getJwt().setIssuer(issuer);
        properties.getJwt().setAudience(audience);
        return properties;
    }

    private String base64Secret() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
