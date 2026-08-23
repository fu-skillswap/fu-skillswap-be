package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarBusyInterval;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnection;
import com.fptu.exe.skillswap.modules.identity.domain.GoogleCalendarConnectionStatus;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarBusyPortTest {

    @Mock
    private GoogleCalendarConnectionRepository connectionRepository;

    @Mock
    private GoogleCalendarApiClient googleCalendarApiClient;

    @Mock
    private GoogleTokenCryptoService googleTokenCryptoService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private GoogleCalendarConnectionService connectionService;

    @BeforeEach
    void setUp() {
        connectionService = new GoogleCalendarConnectionService(
                null,
                connectionRepository,
                googleCalendarApiClient,
                googleTokenCryptoService,
                transactionTemplate,
                null,
                null,
                null
        );
    }

    @Test
    void queryBusyIntervals_noConnection_shouldReturnEmpty() {
        UUID mentorUserId = UUID.randomUUID();
        when(connectionRepository.findByUserId(mentorUserId)).thenReturn(Optional.empty());

        List<GoogleCalendarBusyInterval> intervals = connectionService.queryBusyIntervals(
                mentorUserId,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        assertTrue(intervals.isEmpty());
    }

    @Test
    void queryBusyIntervals_activeConnection_shouldReturnBusyIntervals() {
        UUID mentorUserId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        GoogleCalendarConnection connection = GoogleCalendarConnection.builder()
                .id(connectionId)
                .connectionStatus(GoogleCalendarConnectionStatus.ACTIVE)
                .calendarId("primary")
                .accessTokenCiphertext("encrypted-access")
                .build();

        when(connectionRepository.findByUserId(mentorUserId)).thenReturn(Optional.of(connection));
        when(transactionTemplate.execute(any())).thenReturn("decrypted-token");

        Instant start = Instant.parse("2026-08-23T10:00:00Z");
        Instant end = Instant.parse("2026-08-23T11:00:00Z");
        List<GoogleCalendarBusyInterval> expected = List.of(new GoogleCalendarBusyInterval(start, end));

        when(googleCalendarApiClient.queryFreeBusy(eq("decrypted-token"), eq("primary"), eq(start), eq(end)))
                .thenReturn(expected);

        List<GoogleCalendarBusyInterval> actual = connectionService.queryBusyIntervals(mentorUserId, start, end);

        assertEquals(1, actual.size());
        assertEquals(start, actual.get(0).startTime());
        assertEquals(end, actual.get(0).endTime());
        assertTrue(actual.get(0).overlaps(start.minusSeconds(60), end.plusSeconds(60)));
        assertFalse(actual.get(0).overlaps(end.plusSeconds(60), end.plusSeconds(120)));
    }
}
