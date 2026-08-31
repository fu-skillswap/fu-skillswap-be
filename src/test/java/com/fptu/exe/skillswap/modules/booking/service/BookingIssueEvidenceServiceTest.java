package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingIssueEvidenceUploadIntentRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueEvidenceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueEvidenceUploadIntentRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingIssueEvidenceServiceTest {
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingIssueEvidenceUploadIntentRepository intentRepository;
    @Mock private BookingIssueEvidenceRepository evidenceRepository;
    @Mock private StorageGateway storageGateway;
    private BookingIssueEvidenceService service;
    private Booking booking;
    private UUID menteeId;

    @BeforeEach
    void setUp() {
        BookingIssueEvidenceProperties properties = new BookingIssueEvidenceProperties();
        service = new BookingIssueEvidenceService(bookingRepository, intentRepository, evidenceRepository, storageGateway,
                properties, TimeProvider.fixed(Instant.parse("2026-08-25T08:00:00Z"), ZoneOffset.UTC));
        menteeId = UUID.randomUUID();
        User mentee = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.when(mentee.getId()).thenReturn(menteeId);
        booking = Booking.builder().id(UUID.randomUUID())
                .mentee(mentee)
                .mentorUserId(UUID.randomUUID())
                .selectedEndTimeUtc(Instant.parse("2026-08-25T07:30:00Z")).build();
    }

    @Test
    void reporterMustAttachAtLeastOneConfirmedEvidence() {
        BaseException exception = assertThrows(BaseException.class,
                () -> service.attachReporterEvidence(booking, menteeId, List.of(), Instant.parse("2026-08-25T08:00:00Z")));
        assertEquals(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, exception.getErrorCode());
        verify(evidenceRepository, never()).findAllByIdInForUpdate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createIntentRejectsExecutableBeforeStorageIsTouched() {
        org.mockito.Mockito.when(bookingRepository.findById(booking.getId())).thenReturn(java.util.Optional.of(booking));
        BaseException exception = assertThrows(BaseException.class, () -> service.createUploadIntent(menteeId, booking.getId(),
                new BookingIssueEvidenceUploadIntentRequest("proof.exe", "application/octet-stream", 10L)));
        assertEquals(ErrorCode.BOOKING_ISSUE_EVIDENCE_INVALID, exception.getErrorCode());
        verify(intentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonParticipantCannotReadEvidence() {
        org.mockito.Mockito.when(bookingRepository.findById(booking.getId())).thenReturn(java.util.Optional.of(booking));
        BaseException exception = assertThrows(BaseException.class,
                () -> service.getForParticipant(UUID.randomUUID(), booking.getId()));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }
}
