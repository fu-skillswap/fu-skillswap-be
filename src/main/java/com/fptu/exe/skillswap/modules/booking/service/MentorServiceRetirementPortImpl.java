package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.port.MentorServiceRetirementPort;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MentorServiceRetirementPortImpl implements MentorServiceRetirementPort {

    private static final String DEACTIVATION_REASON = "MENTOR_SERVICE_DEACTIVATED";

    private final BookingRepository bookingRepository;
    private final BookingDecisionService bookingDecisionService;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Override
    @Transactional
    public void retireFutureOffers(UUID mentorUserId, UUID serviceId, boolean rejectPendingBookings) {
        if (mentorUserId == null || serviceId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể ngừng dịch vụ mentoring không hợp lệ");
        }

        LocalDateTime now = DateTimeUtil.now();
        List<Booking> pendingBookings = bookingRepository.findByServiceIdAndStatus(serviceId, BookingStatus.PENDING)
                .stream()
                .filter(booking -> booking.getSelectedStartTime() != null && booking.getSelectedStartTime().isAfter(now))
                .toList();

        if (!pendingBookings.isEmpty() && !rejectPendingBookings) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "SERVICE_HAS_PENDING_BOOKINGS");
        }
        for (Booking booking : pendingBookings) {
            bookingDecisionService.rejectBooking(
                    mentorUserId,
                    booking.getId(),
                    new RejectBookingRequest(DEACTIVATION_REASON, null)
            );
        }

        List<AvailabilitySlotService> futureBindings = availabilitySlotServiceRepository
                .findFutureActiveBindingsByServiceIdForUpdate(serviceId, now);
        if (!futureBindings.isEmpty()) {
            Set<UUID> changedSlotIds = futureBindings.stream()
                    .map(binding -> binding.getSlot().getId())
                    .collect(java.util.stream.Collectors.toSet());
            availabilitySlotServiceRepository.deleteAll(futureBindings);
            mentorAvailabilitySlotRepository.bumpVersions(changedSlotIds, DateTimeUtil.instantNow());
        }
    }
}
