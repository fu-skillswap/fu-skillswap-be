package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.JwtProperties;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RefreshTokenReplayCryptoServiceTest {

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
        JwtProperties properties = new JwtProperties();
        properties.getJwt().setSecretKey("refresh-replay-secret-key-for-tests");

        RefreshTokenReplayCryptoService service = new RefreshTokenReplayCryptoService(properties);

        TokenResponse input = TokenResponse.builder()
                .accessToken("access-token-value")
                .refreshToken("refresh-token-value")
                .tokenType("Bearer")
                .build();

        String encrypted = service.encrypt(input);

        assertNotEquals("access-token-value", encrypted);
        TokenResponse output = service.decrypt(encrypted);
        assertEquals("Bearer", output.getTokenType());
        assertEquals("access-token-value", output.getAccessToken());
        assertEquals("refresh-token-value", output.getRefreshToken());
    }
}
