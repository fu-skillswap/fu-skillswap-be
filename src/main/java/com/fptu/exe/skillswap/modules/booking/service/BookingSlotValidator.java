package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingSlotValidator {

    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.ACCEPTED,
            BookingStatus.PAID
    );

    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final BookingRepository bookingRepository;
    private final MentorBookingPolicyService mentorBookingPolicyService;

    public BookingSlotValidator(AvailabilitySlotServiceRepository availabilitySlotServiceRepository,
                                BookingRepository bookingRepository) {
        this(availabilitySlotServiceRepository, bookingRepository, null);
    }

    public void validateSelectedRange(
            MentorAvailabilitySlot slot,
            MentorService mentorService,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime,
            LocalDateTime now
    ) {
        if (selectedStartTime == null || selectedEndTime == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedStartTime và selectedEndTime là bắt buộc");
        }
        if (!selectedEndTime.isAfter(selectedStartTime)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedEndTime phải sau selectedStartTime");
        }
        if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring không hợp lệ");
        }
        if (selectedStartTime.isBefore(slot.getStartTime()) || selectedEndTime.isAfter(slot.getEndTime())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng thời gian đã chọn phải nằm hoàn toàn trong khung giờ của mentor");
        }
        if (!selectedStartTime.isAfter(now)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khoảng thời gian đã chọn đã bắt đầu hoặc đã trôi qua");
        }
        long durationMinutes = Duration.between(selectedStartTime, selectedEndTime).toMinutes();
        if (mentorService == null || mentorService.getDurationMinutes() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring không hợp lệ");
        }
        if (durationMinutes != mentorService.getDurationMinutes()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng thời gian đã chọn phải đúng bằng thời lượng của service");
        }
        if (mentorBookingPolicyService != null
                && slot.getMentorProfile() != null
                && slot.getMentorProfile().getUserId() != null) {
            mentorBookingPolicyService.validateBookingWindow(slot.getMentorProfile().getUserId(), selectedStartTime, now);
        }
    }

    public void validateServiceAttachedToSlot(UUID slotId, UUID serviceId) {
        if (!availabilitySlotServiceRepository.existsBySlotIdAndServiceId(slotId, serviceId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service hiện chưa được gắn vào availability slot đã chọn");
        }
    }

    public void validateCandidateSelection(
            MentorAvailabilitySlot slot,
            MentorService mentorService,
            UUID menteeUserId,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime
    ) {
        long offsetMinutes = Duration.between(slot.getStartTime(), selectedStartTime).toMinutes();
        if (offsetMinutes < 0 || offsetMinutes % mentorService.getDurationMinutes() != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedStartTime phải khớp với candidate segment hợp lệ của service trong slot");
        }

        if (bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartTime,
                selectedEndTime
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Segment đã chọn đã có booking được mentor chấp nhận trùng thời gian.");
        }

        long pendingCount = bookingRepository.countBySlotIdAndExactSegmentAndStatus(
                slot.getId(),
                selectedStartTime,
                selectedEndTime,
                BookingStatus.PENDING
        );
        if (pendingCount >= BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Segment đã chọn đã đạt tối đa 3 yêu cầu chờ xác nhận.");
        }

        if (bookingRepository.hasOverlappingBookingByStatuses(
                menteeUserId,
                SLOT_LOCKING_STATUSES,
                selectedStartTime,
                selectedEndTime
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đã có lịch học khác đã được chấp nhận trùng với khung giờ đã chọn.");
        }
    }
}
