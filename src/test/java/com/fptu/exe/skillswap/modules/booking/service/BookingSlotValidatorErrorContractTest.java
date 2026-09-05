package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class BookingSlotValidatorErrorContractTest {

    @Test
    void rejectsElapsedSelectedSlotWithStableBusinessCode() {
        BookingSlotValidator validator = new BookingSlotValidator(
                mock(AvailabilitySlotServiceRepository.class), mock(BookingRepository.class));
        Instant now = Instant.parse("2026-09-01T03:00:00Z");
        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .startTimeUtc(now.minusSeconds(3600))
                .endTimeUtc(now.plusSeconds(3600))
                .build();
        ServiceSlotCandidate service = new ServiceSlotCandidate(
                UUID.randomUUID(), UUID.randomUUID(), "Java", "", "", 60,
                20_000, false, true, "ONLINE", "ONE_TO_ONE", false);

        BaseException exception = assertThrows(BaseException.class, () -> validator.validateSelectedRange(
                slot, service, now.minusSeconds(60), now.plusSeconds(3540), now));

        assertEquals(ErrorCode.BOOKING_SLOT_UNAVAILABLE, exception.getErrorCode());
        assertEquals("Khung giờ này không còn khả dụng", exception.getMessage());
    }
}
