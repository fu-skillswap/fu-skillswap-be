package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.*;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityPort;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorPublicAvailabilityPreviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingAvailabilityPortImpl implements BookingAvailabilityPort {

    private final MentorAvailabilitySlotRepository slotRepository;
    private final AvailabilitySlotServiceRepository slotServiceRepository;
    private final BookingRepository bookingRepository;
    private final MentorAvailabilityService mentorAvailabilityService;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    private final MentorQueryPort mentorQueryPort;

    @Override
    @Transactional
    public void deleteSlotsByMentorUserId(UUID mentorUserId) {
        if (mentorUserId != null) {
            slotRepository.deleteByMentorUserId(mentorUserId);
        }
    }

    @Override
    @Transactional
    public void unpublishSlotsForService(UUID serviceId) {
        if (serviceId != null) {
            slotServiceRepository.deleteByMentorServiceId(serviceId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPublishedSlots(UUID mentorUserId) {
        if (mentorUserId == null) return false;
        return slotRepository.existsByMentorUserIdAndStatus(mentorUserId, SlotStatus.AVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAvailableSlotsForMentor(UUID mentorUserId) {
        if (mentorUserId == null) return 0;
        return slotRepository.countByMentorUserIdAndStatus(mentorUserId, SlotStatus.AVAILABLE);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingFutureBookingsForService(UUID serviceId) {
        if (serviceId == null) return false;
        LocalDateTime now = DateTimeUtil.now();
        return bookingRepository.findByServiceIdAndStatus(serviceId, BookingStatus.PENDING).stream()
                .anyMatch(booking -> booking.getSelectedStartTime() != null && booking.getSelectedStartTime().isAfter(now));
    }

    @Override
    @Transactional
    public void rejectPendingBookingsForService(UUID serviceId, String reason) {
        if (serviceId == null) return;
        LocalDateTime now = DateTimeUtil.now();
        List<Booking> affectedPending = bookingRepository.findByServiceIdAndStatus(serviceId, BookingStatus.PENDING).stream()
                .filter(booking -> booking.getSelectedStartTime() != null && booking.getSelectedStartTime().isAfter(now))
                .toList();

        for (Booking booking : affectedPending) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SYSTEM_REJECT, DateTimeUtil.instantNow());
            bookingRepository.save(booking);
        }
    }

    @Override
    @Transactional
    public void unbindFutureSlotsForService(UUID serviceId) {
        if (serviceId == null) return;
        LocalDateTime now = DateTimeUtil.now();
        List<AvailabilitySlotService> futureBindings = slotServiceRepository
                .findFutureActiveBindingsByServiceIdForUpdate(serviceId, now);
        Set<UUID> changedSlotIds = futureBindings.stream()
                .map(binding -> binding.getSlot().getId())
                .collect(java.util.stream.Collectors.toSet());
        if (!futureBindings.isEmpty()) {
            slotServiceRepository.deleteAll(futureBindings);
            slotRepository.bumpVersions(changedSlotIds, DateTimeUtil.instantNow());
        }
    }

    @Override
    @Transactional
    public List<MentorAvailabilitySlotResponse> getAvailableSlots(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        MentorProfile profile = mentorQueryPort != null ? mentorQueryPort.findMentorProfileByUserId(mentorUserId).orElse(null) : null;
        if (profile == null) return List.of();
        return mentorAvailabilityService.getAvailableSlots(profile, fromDate, toDate);
    }

    @Override
    @Transactional(readOnly = true)
    public MentorPublicAvailabilityPreviewResponse getPublicAvailabilityPreview(UUID mentorUserId, LocalDate fromDate, LocalDate toDate) {
        MentorProfile profile = mentorQueryPort != null ? mentorQueryPort.findMentorProfileByUserId(mentorUserId).orElse(null) : null;
        if (profile == null) return new MentorPublicAvailabilityPreviewResponse("Asia/Ho_Chi_Minh", false, null, List.of());
        return mentorAvailabilityService.getPublicAvailabilityPreview(profile, fromDate, toDate);
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceSlotCandidatesResponse getSlotCandidates(UUID mentorUserId, UUID slotId, UUID serviceId) {
        MentorProfile profile = mentorQueryPort != null ? mentorQueryPort.findMentorProfileByUserId(mentorUserId).orElse(null) : null;
        if (profile == null) return null;
        return mentorAvailabilityService.getSlotCandidates(profile, slotId, serviceId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canMenteeRequestBooking(UUID menteeUserId, UUID mentorUserId) {
        MentorProfile profile = mentorQueryPort != null ? mentorQueryPort.findMentorProfileByUserId(mentorUserId).orElse(null) : null;
        if (profile == null) return false;
        return bookingEligibilityPolicy.canMenteeRequestBooking(menteeUserId, profile);
    }
}
