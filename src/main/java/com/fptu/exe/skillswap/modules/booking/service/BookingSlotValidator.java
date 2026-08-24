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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingSlotValidator {

    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.PAID
    );

    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final BookingRepository bookingRepository;
    private final MentorBookingPolicyService mentorBookingPolicyService;

    private AvailabilityTemplateService availabilityTemplateService;

    public BookingSlotValidator(AvailabilitySlotServiceRepository availabilitySlotServiceRepository,
                                BookingRepository bookingRepository) {
        this(availabilitySlotServiceRepository, bookingRepository, null);
    }

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    public void validateSelectedRange(
            MentorAvailabilitySlot slot,
            MentorService mentorService,
            Instant selectedStartAt,
            Instant selectedEndAt,
            Instant nowUtc
    ) {
        if (selectedStartAt == null || selectedEndAt == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "startAt và endAt là bắt buộc");
        }
        if (!selectedEndAt.isAfter(selectedStartAt)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "endAt phải sau startAt");
        }
        if (slot == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring không hợp lệ");
        }
        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        Instant slotEndUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc() : BookingTime.toInstant(slot.getEndTime());
        if (slotStartUtc == null || slotEndUtc == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khung giờ mentoring không hợp lệ");
        }
        if (availabilityTemplateService != null && !availabilityTemplateService.isGeneratedSlotEligible(slot)) {
            throw new BaseException(ErrorCode.AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE);
        }
        if (selectedStartAt.isBefore(slotStartUtc) || selectedEndAt.isAfter(slotEndUtc)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng thời gian đã chọn phải nằm hoàn toàn trong khung giờ của mentor");
        }
        if (!selectedStartAt.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Khoảng thời gian đã chọn đã bắt đầu hoặc đã trôi qua");
        }
        long durationMinutes = Duration.between(selectedStartAt, selectedEndAt).toMinutes();
        if (mentorService == null || mentorService.getDurationMinutes() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring không hợp lệ");
        }
        if (durationMinutes != mentorService.getDurationMinutes()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng thời gian đã chọn phải đúng bằng thời lượng của service");
        }
        if (mentorBookingPolicyService != null
                && slot.getMentorProfile() != null
                && slot.getMentorProfile().getUserId() != null) {
            mentorBookingPolicyService.validateBookingWindow(
                    slot.getMentorProfile().getUserId(),
                    BookingTime.fromInstant(selectedStartAt),
                    BookingTime.fromInstant(nowUtc)
            );
        }
    }

    public void validateSelectedRange(
            MentorAvailabilitySlot slot,
            MentorService mentorService,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime,
            LocalDateTime now
    ) {
        Instant selectedStartAt = BookingTime.toInstant(selectedStartTime);
        Instant selectedEndAt = BookingTime.toInstant(selectedEndTime);
        Instant nowUtc = BookingTime.toInstant(now);
        validateSelectedRange(slot, mentorService, selectedStartAt, selectedEndAt, nowUtc);
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
            Instant selectedStartAt,
            Instant selectedEndAt
    ) {
        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        long offsetMinutes = Duration.between(slotStartUtc, selectedStartAt).toMinutes();
        if (offsetMinutes < 0 || offsetMinutes % mentorService.getDurationMinutes() != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedStartTime phải khớp với candidate segment hợp lệ của service trong slot");
        }

        if (bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartAt,
                selectedEndAt
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Segment đã chọn đã có booking được mentor chấp nhận trùng thời gian.");
        }

        long pendingCount = bookingRepository.countBySlotIdAndExactSegmentAndStatusUtc(
                slot.getId(),
                selectedStartAt,
                selectedEndAt,
                BookingStatus.PENDING
        );
        if (pendingCount >= BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Segment đã chọn đã đạt tối đa 3 yêu cầu chờ xác nhận.");
        }

        if (bookingRepository.hasOverlappingBookingByStatusesUtc(
                menteeUserId,
                SLOT_LOCKING_STATUSES,
                selectedStartAt,
                selectedEndAt
        )) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Bạn đã có lịch học khác đã được chấp nhận trùng với khung giờ đã chọn.");
        }
    }

    public void validateCandidateSelection(
            MentorAvailabilitySlot slot,
            MentorService mentorService,
            UUID menteeUserId,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime
    ) {
        validateCandidateSelection(
                slot,
                mentorService,
                menteeUserId,
                BookingTime.toInstant(selectedStartTime),
                BookingTime.toInstant(selectedEndTime)
        );
    }
}
