package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.isConfirmedBookingStatus;
import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.selectedStartTime;

@Service
@RequiredArgsConstructor
public class BookingCancellationService {

    private static final long MENTEE_FREE_CANCEL_DEADLINE_MINUTES = 4 * 60;
    private static final long MENTOR_SAFE_CANCEL_DEADLINE_MINUTES = 6 * 60;
    private static final long MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES = 3 * 60;
    private static final BigDecimal MENTOR_LATE_CANCEL_PENALTY = BigDecimal.valueOf(0.5);
    private static final int MENTOR_LATE_CANCEL_SUSPENSION_DAYS = 3;

    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.ACCEPTED,
            BookingStatus.PAID
    );

    private final BookingRepository bookingRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final EntityManager entityManager;
    private final SessionService sessionService;
    private final PaymentOrderService paymentOrderService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingResponseMapper bookingResponseMapper;
    private AvailabilityTemplateService availabilityTemplateService;

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    @Transactional
    public BookingResponse cancelBookingByMentor(UUID mentorUserId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId).orElse(null);
        if (slotId != null) {
            mentorAvailabilitySlotRepository.findByIdForUpdate(slotId);
        }

        Booking booking = getBookingForCancellation(bookingId);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }

        if (booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT && !isConfirmedBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor chỉ có thể hủy booking đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        long minutesUntilStart = minutesUntilStart(booking, now);
        if (minutesUntilStart <= 0) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking đã bắt đầu, không thể hủy bằng luồng này");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTOR);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        refreshSlotBookedFlag(slot);

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            if (entityManager != null) {
                entityManager.refresh(lockedProfile);
            }
            applyMentorCancellationPenalty(lockedProfile, minutesUntilStart, now);
            lockedProfile.setTotalMentorCancelledBookings(defaultInteger(lockedProfile.getTotalMentorCancelledBookings()) + 1);
            touchMentorActivity(lockedProfile, now);
            mentorProfileRepository.save(lockedProfile);
        }

        Booking savedBooking = bookingRepository.save(booking);

        if (sessionService != null) {
            sessionService.cancelForBooking(bookingId);
        }
        if (paymentOrderService != null) {
            paymentOrderService.handleMentorCancellation(savedBooking);
        }

        eventPublisher.publishEvent(new NotificationEvent(
                savedBooking.getMentee().getId(),
                NotificationType.BOOKING_CANCELLED_BY_MENTOR,
                "Mentor đã hủy lịch",
                savedBooking.getMentorProfile().getUser().getFullName() + " đã hủy lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_CANCELLED_BY_MENTOR_EMAIL)
                .recipientEmail(savedBooking.getMentee().getEmail())
                .recipientName(savedBooking.getMentee().getFullName())
                .actorName(savedBooking.getMentorProfile().getUser().getFullName())
                .bookingStartTime(savedBooking.getSelectedStartTime())
                .bookingEndTime(savedBooking.getSelectedEndTime())
                .learningGoalTitle(savedBooking.getLearningGoalTitle())
                .learningGoalDescription(savedBooking.getLearningGoalDescription())
                .serviceTitle(savedBooking.getServiceTitleSnapshot())
                .serviceDurationMinutes(savedBooking.getServiceDurationSnapshot())
                .serviceFree(savedBooking.getServiceIsFreeSnapshot())
                .servicePriceScoin(savedBooking.getServicePriceScoinSnapshot())
                .serviceExpectedOutcome(savedBooking.getServiceExpectedOutcomeSnapshot())
                .mentorResponseNote(savedBooking.getMentorResponseNote())
                .reason(savedBooking.getCancelReason())
                .createdAt(DateTimeUtil.now())
                .build());

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentor đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse cancelBookingByMentee(UUID menteeId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        UUID slotId = bookingRepository.findSlotIdByBookingId(bookingId).orElse(null);
        if (slotId != null) {
            mentorAvailabilitySlotRepository.findByIdForUpdate(slotId);
        }

        Booking booking = getBookingForCancellation(bookingId);
        if (menteeId == null || booking.getMentee() == null || !menteeId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                && !isConfirmedBookingStatus(booking.getStatus())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể hủy booking đang chờ phản hồi hoặc đã được chấp nhận");
        }

        LocalDateTime now = DateTimeUtil.now();
        BookingStatus currentStatus = booking.getStatus();
        long minutesUntilStart = isConfirmedBookingStatus(currentStatus)
                ? minutesUntilStart(booking, now)
                : Long.MAX_VALUE;
        boolean lateCancellation = isConfirmedBookingStatus(currentStatus)
                && minutesUntilStart < MENTEE_FREE_CANCEL_DEADLINE_MINUTES;

        booking.setStatus(BookingStatus.CANCELLED_BY_MENTEE);
        booking.setCancelledAt(now);
        booking.setCancelReason(requiredCancelReason(request));

        MentorAvailabilitySlot slot = booking.getSlot();
        if (slot != null && currentStatus != BookingStatus.PENDING) {
            refreshSlotBookedFlag(slot);
        }

        Booking savedBooking = bookingRepository.save(booking);

        if (sessionService != null) {
            sessionService.cancelForBooking(bookingId);
        }
        if (paymentOrderService != null && (currentStatus == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isConfirmedBookingStatus(currentStatus))) {
            paymentOrderService.handleMenteeCancellation(savedBooking, lateCancellation);
        }

        eventPublisher.publishEvent(new NotificationEvent(
                savedBooking.getMentorProfile().getUserId(),
                NotificationType.BOOKING_CANCELLED_BY_MENTEE,
                "Mentee đã hủy lịch",
                savedBooking.getMentee().getFullName() + " đã hủy lịch mentoring.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder()
                .bookingId(savedBooking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_CANCELLED_BY_MENTEE_EMAIL)
                .recipientEmail(savedBooking.getMentorProfile().getUser().getEmail())
                .recipientName(savedBooking.getMentorProfile().getUser().getFullName())
                .actorName(savedBooking.getMentee().getFullName())
                .bookingStartTime(savedBooking.getSelectedStartTime())
                .bookingEndTime(savedBooking.getSelectedEndTime())
                .learningGoalTitle(savedBooking.getLearningGoalTitle())
                .learningGoalDescription(savedBooking.getLearningGoalDescription())
                .serviceTitle(savedBooking.getServiceTitleSnapshot())
                .serviceDurationMinutes(savedBooking.getServiceDurationSnapshot())
                .serviceFree(savedBooking.getServiceIsFreeSnapshot())
                .servicePriceScoin(savedBooking.getServicePriceScoinSnapshot())
                .serviceExpectedOutcome(savedBooking.getServiceExpectedOutcomeSnapshot())
                .mentorResponseNote(savedBooking.getMentorResponseNote())
                .reason(savedBooking.getCancelReason())
                .createdAt(DateTimeUtil.now())
                .build());

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentee đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private Booking getBookingForCancellation(UUID bookingId) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        return bookingRepository.findByIdForCancellation(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
    }

    private boolean isMentorOfBooking(Booking booking, UUID mentorUserId) {
        return booking != null && booking.getMentorProfile() != null
                && mentorUserId != null && mentorUserId.equals(booking.getMentorProfile().getUserId());
    }

    private long minutesUntilStart(Booking booking, LocalDateTime now) {
        LocalDateTime startTime = selectedStartTime(booking);
        if (startTime == null) {
            return 0;
        }
        return Duration.between(now, startTime).toMinutes();
    }

    private void applyMentorCancellationPenalty(MentorProfile mentorProfile, long minutesUntilStart, LocalDateTime now) {
        if (minutesUntilStart < MENTOR_SUSPENSION_CANCEL_DEADLINE_MINUTES) {
            mentorProfile.setBookingSuspendedUntil(now.plusDays(MENTOR_LATE_CANCEL_SUSPENSION_DAYS));
        }
        if (minutesUntilStart < MENTOR_SAFE_CANCEL_DEADLINE_MINUTES) {
            BigDecimal currentPenalty = mentorProfile.getLateCancellationPenaltyPoints() == null
                    ? BigDecimal.ZERO
                    : mentorProfile.getLateCancellationPenaltyPoints();
            mentorProfile.setLateCancellationPenaltyPoints(currentPenalty.add(MENTOR_LATE_CANCEL_PENALTY));
        }
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        boolean hasConfirmedOverlap = bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                slot.getStartTime(),
                slot.getEndTime()
        );
        slot.setBooked(hasConfirmedOverlap);
        if (availabilityTemplateService != null) {
            availabilityTemplateService.markSlotDue(slot.getId());
        }
    }

    private void touchMentorActivity(MentorProfile profile, LocalDateTime activityAt) {
        if (profile == null) {
            return;
        }
        profile.setLastActiveAt(activityAt != null ? activityAt : DateTimeUtil.now());
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String requiredCancelReason(CancelBookingRequest request) {
        String reason = trimToNull(request == null ? null : request.cancelReason());
        if (reason == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Vui lòng nhập lý do hủy");
        }
        return reason;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
