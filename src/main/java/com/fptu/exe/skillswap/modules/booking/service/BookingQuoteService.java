package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.constant.BookingQueueConstants;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDeadlinePolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTime;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingQuoteRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingQuoteResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingPreviewPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingPolicyQuery;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Validates the same deterministic candidate identity as booking creation without reserving it. */
@Service
@RequiredArgsConstructor
public class BookingQuoteService {

    private final UserQueryPort userQueryPort;
    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final MentorBookingQueryPort mentorBookingQueryPort;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;
    private final BookingSlotValidator bookingSlotValidator;
    private final MentorBookingPolicyQuery mentorBookingPolicyQuery;
    private final BookingPricingPreviewPort pricingPreviewPort;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional(readOnly = true)
    public BookingQuoteResponse quote(UUID menteeUserId, BookingQuoteRequest request) {
        if (menteeUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || request.slotId() == null || request.serviceId() == null || request.startAt() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "slotId, serviceId và startAt là bắt buộc");
        }
        Instant normalizedStartAt = request.startAt().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

        UserSummaryRecord mentee = userQueryPort.findUserSummaryById(menteeUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng hiện tại"));
        bookingEligibilityPolicy.validateBookerEligibility(mentee);
        if (bookingRepository.countByMenteeUserIdAndStatus(menteeUserId, BookingStatus.PENDING)
                >= BookingQueueConstants.MAX_PENDING_BOOKINGS_PER_MENTEE) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Bạn đang có tối đa 5 yêu cầu đặt lịch đang chờ phản hồi.");
        }

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.findById(request.slotId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy khung giờ mentoring"));
        UUID mentorUserId = slot.getMentorUserId();
        validateMentorAndSlot(menteeUserId, mentorUserId, slot);

        ServiceSlotCandidate serviceCandidate = mentorBookingQueryPort
                .getActiveServiceCandidate(request.serviceId(), mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Service hiện không còn khả dụng"));

        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        Instant slotEndUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc() : BookingTime.toInstant(slot.getEndTime());
        Instant normalizedEndAt = normalizedStartAt.plus(Duration.ofMinutes(serviceCandidate.durationMinutes()));
        LocalDateTime start = BookingTime.fromInstant(normalizedStartAt);
        LocalDateTime end = BookingTime.fromInstant(normalizedEndAt);
        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();

        bookingSlotValidator.validateSelectedRange(slot, serviceCandidate, normalizedStartAt, normalizedEndAt, nowUtc);
        bookingSlotValidator.validateServiceAttachedToSlot(slot.getId(), serviceCandidate.serviceId());
        bookingSlotValidator.validateCandidateSelection(slot, serviceCandidate, menteeUserId, normalizedStartAt, normalizedEndAt);
        if (mentorBookingPolicyQuery != null) {
            mentorBookingPolicyQuery.validateBookingWindow(mentorUserId, start, nowBusiness);
        }

        if (bookingRepository.existsByMenteeUserIdAndSlotIdAndSelectedStartTimeUtcAndSelectedEndTimeUtcAndStatusIn(
                menteeUserId, slot.getId(), normalizedStartAt, normalizedEndAt,
                List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID))) {
            throw new BaseException(ErrorCode.BOOKING_ALREADY_EXISTS);
        }

        Instant pendingExpireAtUtc = BookingDeadlinePolicy.resolvePendingExpiry(nowUtc, normalizedStartAt);
        if (pendingExpireAtUtc == null || !pendingExpireAtUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }
        return new BookingQuoteResponse(
                slot.getId(),
                serviceCandidate.serviceId(),
                serviceCandidate.title(),
                serviceCandidate.durationMinutes(),
                BookingTime.toOffsetDateTime(normalizedStartAt),
                BookingTime.toOffsetDateTime(normalizedEndAt),
                BookingTime.toOffsetDateTime(pendingExpireAtUtc),
                (int) BookingDeadlinePolicy.PAYMENT_WINDOW_MINUTES,
                (int) BookingDeadlinePolicy.PAYMENT_PREPARATION_MINUTES,
                pricingPreviewPort.estimateForCandidate(menteeUserId, serviceCandidate),
                BookingCancellationRefundPolicy.current(),
                true,
                BookingPricingPreviewPort.ESTIMATE_DISCLAIMER
        );
    }

    private void validateMentorAndSlot(UUID menteeUserId, UUID mentorUserId, MentorAvailabilitySlot slot) {
        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        if (mentorUserId == null || mentorUserId.equals(menteeUserId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        UserSummaryRecord mentorUser = userQueryPort.findUserSummaryById(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking"));
        if (!mentorUser.isActive()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        MentorBookingCapability capability = mentorBookingQueryPort.getBookingCapability(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking"));
        if (!capability.isActiveMentor()
                || !capability.available()
                || !bookingEligibilityPolicy.isDiscoverableMentorForBooking(capability)
                || (capability.bookingSuspendedUntil() != null && capability.bookingSuspendedUntil().isAfter(nowBusiness))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện chưa sẵn sàng nhận booking");
        }
        Instant slotStartUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc() : BookingTime.toInstant(slot.getStartTime());
        Instant slotEndUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc() : BookingTime.toInstant(slot.getEndTime());
        if (!slot.isActive() || slotStartUtc == null || slotEndUtc == null
                || !slotEndUtc.isAfter(slotStartUtc) || !slotEndUtc.isAfter(nowUtc)) {
            throw new BaseException(ErrorCode.BOOKING_SLOT_UNAVAILABLE);
        }
    }
}
