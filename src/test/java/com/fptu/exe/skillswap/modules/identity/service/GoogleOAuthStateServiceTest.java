package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
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
    void calendarState_shouldValidateUserRedirectAndPkceExactlyOnce() throws Exception {
        UUID userId = UUID.randomUUID();
        String verifier = "calendar-verifier-123456789012345678901234567890";
        String challenge = challenge(verifier);
        GoogleAuthorizationContextResponse context = service.issueCalendarConnect(
                userId,
                "https://skillswap.asia/calendar/callback",
                challenge
        );

        assertDoesNotThrow(() -> service.consumeCalendarConnect(
                userId, context.state(), "https://skillswap.asia/calendar/callback", verifier
        ));
        assertThrows(BaseException.class, () -> service.consumeCalendarConnect(
                userId, context.state(), "https://skillswap.asia/calendar/callback", verifier
        ));
    }

    @Test
    void calendarState_shouldBeBurnedWhenAnotherUserConsumesIt() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String verifier = "calendar-verifier-123456789012345678901234567890";
        GoogleAuthorizationContextResponse context = service.issueCalendarConnect(
                ownerId,
                "https://skillswap.asia/calendar/callback",
                challenge(verifier)
        );

        assertThrows(BaseException.class, () -> service.consumeCalendarConnect(
                UUID.randomUUID(), context.state(), "https://skillswap.asia/calendar/callback", verifier
        ));
        assertThrows(BaseException.class, () -> service.consumeCalendarConnect(
                ownerId, context.state(), "https://skillswap.asia/calendar/callback", verifier
        ));
    }

    private String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
        );
    }
}
