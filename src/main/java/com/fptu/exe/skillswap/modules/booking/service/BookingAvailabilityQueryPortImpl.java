package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityQueryPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingAvailabilityQueryPortImpl implements BookingAvailabilityQueryPort {

    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final BookingRepository bookingRepository;

    @Override
    public List<UUID> findMentorUserIdsWithActiveSlotsInFuture(Collection<UUID> mentorUserIds, Instant fromInstant) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mentorAvailabilitySlotRepository.findMentorUserIdsWithActiveSlotsInFuture(mentorUserIds, fromInstant);
    }

    @Override
    public boolean isSlotOwnedByMentor(UUID slotId, UUID mentorUserId) {
        if (slotId == null || mentorUserId == null) {
            return false;
        }
        return mentorAvailabilitySlotRepository.findById(slotId)
                .map(slot -> slot.getMentorUserId() != null && mentorUserId.equals(slot.getMentorUserId()))
                .orElse(false);
    }

    @Override
    public boolean hasPaidFutureBookingsForMentor(UUID mentorUserId, Instant afterUtc) {
        if (mentorUserId == null || afterUtc == null) {
            return false;
        }
        return bookingRepository.existsByMentorUserIdAndStatusAndSelectedStartTimeUtcAfter(
                mentorUserId, BookingStatus.PAID, afterUtc);
    }
}
