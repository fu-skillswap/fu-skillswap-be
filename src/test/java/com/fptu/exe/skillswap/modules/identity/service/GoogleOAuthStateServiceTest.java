package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleOAuthStateServiceTest {

    private final GoogleOAuthStateService service = new GoogleOAuthStateService(new CacheProperties());

    @Test
    void consume_shouldValidateStateRedirectAndPkceExactlyOnce() throws Exception {
        String verifier = "a-production-pkce-verifier-with-sufficient-entropy-1234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
        GoogleAuthorizationContextResponse context = service.issueLogin("https://skillswap.asia/auth/callback", challenge);

        assertDoesNotThrow(() -> service.consumeLogin(context.state(), "https://skillswap.asia/auth/callback", verifier));
        assertThrows(BaseException.class,
                () -> service.consumeLogin(context.state(), "https://skillswap.asia/auth/callback", verifier));
    }

    @Test
    void consume_shouldBurnStateWhenPkceIsWrong() throws Exception {
        String verifier = "correct-verifier-123456789012345678901234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
        GoogleAuthorizationContextResponse context = service.issueLogin("https://skillswap.asia/auth/callback", challenge);

        assertThrows(BaseException.class,
                () -> service.consumeLogin(context.state(), "https://skillswap.asia/auth/callback", "wrong-verifier"));
        assertThrows(BaseException.class,
                () -> service.consumeLogin(context.state(), "https://skillswap.asia/auth/callback", verifier));
    }

    @Test
    void calendarState_shouldNotBeUsableForLoginAndMustBeBurned() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String verifier = "calendar-verifier-123456789012345678901234567890";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
        GoogleAuthorizationContextResponse context = service.issueCalendarConnect(
                ownerId,
                "https://skillswap.asia/calendar/callback",
                challenge
        );

        assertThrows(BaseException.class, () -> service.consumeLogin(
                context.state(),
                "https://skillswap.asia/calendar/callback",
                verifier
        ));
        assertThrows(BaseException.class, () -> service.consumeCalendarConnect(
                ownerId,
                context.state(),
                "https://skillswap.asia/calendar/callback",
                verifier
        ));
    }
}
