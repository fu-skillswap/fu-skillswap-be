package com.fptu.exe.skillswap.modules.booking.port;

import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorPublicAvailabilityPreviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingAvailabilityPort {
    void deleteSlotsByMentorUserId(UUID mentorUserId);
    void unpublishSlotsForService(UUID serviceId);
    boolean hasPublishedSlots(UUID mentorUserId);
    long countAvailableSlotsForMentor(UUID mentorUserId);
    boolean hasPendingFutureBookingsForService(UUID serviceId);
    void rejectPendingBookingsForService(UUID serviceId, String reason);
    void unbindFutureSlotsForService(UUID serviceId);

    List<MentorAvailabilitySlotResponse> getAvailableSlots(UUID mentorUserId, LocalDate fromDate, LocalDate toDate);
    MentorPublicAvailabilityPreviewResponse getPublicAvailabilityPreview(UUID mentorUserId, LocalDate fromDate, LocalDate toDate);
    ServiceSlotCandidatesResponse getSlotCandidates(UUID mentorUserId, UUID slotId, UUID serviceId);
    boolean canMenteeRequestBooking(UUID menteeUserId, UUID mentorUserId);
}
