package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleOAuthStateServiceTest {

    private final GoogleOAuthStateService service = new GoogleOAuthStateService();

    @Test
    void consume_shouldValidateStateRedirectAndPkceExactlyOnce() throws Exception {
        String verifier = "a-production-pkce-verifier-with-sufficient-entropy-1234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
        GoogleAuthorizationContextResponse context = service.issue("https://skillswap.asia/auth/callback", challenge);

        assertDoesNotThrow(() -> service.consume(context.state(), "https://skillswap.asia/auth/callback", verifier));
        assertThrows(BaseException.class,
                () -> service.consume(context.state(), "https://skillswap.asia/auth/callback", verifier));
    }

    @Test
    void consume_shouldBurnStateWhenPkceIsWrong() throws Exception {
        String verifier = "correct-verifier-123456789012345678901234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
        GoogleAuthorizationContextResponse context = service.issue("https://skillswap.asia/auth/callback", challenge);

        assertThrows(BaseException.class,
                () -> service.consume(context.state(), "https://skillswap.asia/auth/callback", "wrong-verifier"));
        assertThrows(BaseException.class,
                () -> service.consume(context.state(), "https://skillswap.asia/auth/callback", verifier));
    }
}
