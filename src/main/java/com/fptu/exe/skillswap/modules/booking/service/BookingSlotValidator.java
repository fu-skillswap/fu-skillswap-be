package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingPolicyQuery;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
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
    private final MentorBookingPolicyQuery mentorBookingPolicyQuery;

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
            ServiceSlotCandidate service,
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
            throw new BaseException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }
        long durationMinutes = Duration.between(selectedStartAt, selectedEndAt).toMinutes();
        if (service == null || service.durationMinutes() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Gói mentoring không hợp lệ");
        }
        if (durationMinutes != service.durationMinutes()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Khoảng thời gian đã chọn phải đúng bằng thời lượng của service");
        }
        if (mentorBookingPolicyQuery != null && slot.getMentorUserId() != null) {
            mentorBookingPolicyQuery.validateBookingWindow(
                    slot.getMentorUserId(),
                    BookingTime.fromInstant(selectedStartAt),
                    BookingTime.fromInstant(nowUtc)
            );
        }
    }

    public void validateSelectedRange(
            MentorAvailabilitySlot slot,
            ServiceSlotCandidate service,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime,
            LocalDateTime now
    ) {
        Instant selectedStartAt = BookingTime.toInstant(selectedStartTime);
        Instant selectedEndAt = BookingTime.toInstant(selectedEndTime);
        Instant nowUtc = BookingTime.toInstant(now);
        validateSelectedRange(slot, service, selectedStartAt, selectedEndAt, nowUtc);
    }

    public void validateServiceAttachedToSlot(UUID slotId, UUID serviceId) {
        if (!availabilitySlotServiceRepository.existsBySlotIdAndServiceId(slotId, serviceId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service hiện chưa được gắn vào availability slot đã chọn");
        }
    }

    public void validateCandidateSelection(
            MentorAvailabilitySlot slot,
            ServiceSlotCandidate service,
            UUID menteeUserId,
            Instant selectedStartAt,
            Instant selectedEndAt
    ) {
        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        long offsetMinutes = Duration.between(slotStartUtc, selectedStartAt).toMinutes();
        if (offsetMinutes < 0 || offsetMinutes % service.durationMinutes() != 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "selectedStartTime phải khớp với candidate segment hợp lệ của service trong slot");
        }

        if (bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                selectedStartAt,
                selectedEndAt
        )) {
            throw new BaseException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }

        long pendingCount = bookingRepository.countBySlotIdAndExactSegmentAndStatusUtc(
                slot.getId(),
                selectedStartAt,
                selectedEndAt,
                BookingStatus.PENDING
        );
        if (pendingCount >= BookingQueueConstants.MAX_PENDING_REQUESTS_PER_SLOT) {
            throw new BaseException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
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
            ServiceSlotCandidate service,
            UUID menteeUserId,
            LocalDateTime selectedStartTime,
            LocalDateTime selectedEndTime
    ) {
        validateCandidateSelection(
                slot,
                service,
                menteeUserId,
                BookingTime.toInstant(selectedStartTime),
                BookingTime.toInstant(selectedEndTime)
        );
    }
}
