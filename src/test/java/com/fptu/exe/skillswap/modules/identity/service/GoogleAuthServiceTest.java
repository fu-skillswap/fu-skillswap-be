package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.GoogleApiProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAuthServiceTest {

    @Test
    void verifyPayload_shouldAcceptKnownGoogleIssuerAndVerifiedEmail() {
        GoogleAuthService service = buildService();
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("accounts.google.com");
        payload.setAudience("google-client-id");
        payload.setSubject("google-sub");
        payload.setEmail("user@test.com");
        payload.setEmailVerified(true);
        payload.set("name", "Test User");

        GoogleAuthService.GoogleUserInfo info = service.verifyPayload(payload);

        assertEquals("google-sub", info.getSub());
        assertEquals("user@test.com", info.getEmail());
        assertEquals("true", info.getEmail_verified());
    }

    @Test
    void verifyPayload_shouldRejectUnknownIssuer() {
        GoogleAuthService service = buildService();
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setIssuer("evil.example.com");
        payload.setAudience("google-client-id");
        payload.setSubject("google-sub");
        payload.setEmail("user@test.com");
        payload.setEmailVerified(true);

        BaseException exception = assertThrows(BaseException.class, () -> service.verifyPayload(payload));
        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED, exception.getErrorCode());
    }

    private GoogleAuthService buildService() {
        GoogleApiProperties properties = new GoogleApiProperties();
        properties.setClientId("google-client-id");
        properties.setClientSecret("google-client-secret");
        return new GoogleAuthService(properties);
    }
}
