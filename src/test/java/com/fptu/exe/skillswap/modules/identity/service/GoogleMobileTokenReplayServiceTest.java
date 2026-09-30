package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.repository.GoogleMobileTokenReplayRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleMobileTokenReplayServiceTest {

    @Mock
    private GoogleMobileTokenReplayRepository replayRepository;

    @Test
    void claim_shouldPersistSubjectIssuedAtAndExpiry() {
        GoogleMobileTokenReplayService service = new GoogleMobileTokenReplayService(replayRepository);
        when(replayRepository.claim(eq("google-sub"), eq(1_700_000_000L), eq(Instant.ofEpochSecond(1_700_003_600L))))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.claim("google-sub", 1_700_000_000L, 1_700_003_600L));

        verify(replayRepository).claim(
                "google-sub",
                1_700_000_000L,
                Instant.ofEpochSecond(1_700_003_600L)
        );
    }

    @Test
    void claim_shouldRejectAlreadyClaimedToken() {
        GoogleMobileTokenReplayService service = new GoogleMobileTokenReplayService(replayRepository);
        when(replayRepository.claim("google-sub", 1_700_000_000L, Instant.ofEpochSecond(1_700_003_600L)))
                .thenReturn(false);

        BaseException exception = assertThrows(
                BaseException.class,
                () -> service.claim("google-sub", 1_700_000_000L, 1_700_003_600L)
        );

        assertEquals(ErrorCode.OAUTH_VERIFICATION_FAILED, exception.getErrorCode());
    }

    @Test
    void claim_shouldRejectMissingOrInvalidClaims() {
        GoogleMobileTokenReplayService service = new GoogleMobileTokenReplayService(replayRepository);

        assertThrows(BaseException.class, () -> service.claim("", 1L, 2L));
        assertThrows(BaseException.class, () -> service.claim("google-sub", null, 2L));
        assertThrows(BaseException.class, () -> service.claim("google-sub", 2L, 2L));
        assertThrows(BaseException.class, () -> service.claim("google-sub", 3L, 2L));
    }
}
