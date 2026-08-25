package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorViolationService;
import com.fptu.exe.skillswap.modules.identity.event.GoogleCalendarCancelBookingRequestedEvent;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.isScheduledBookingStatus;
import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.selectedStartTime;

@Service
@RequiredArgsConstructor
public class BookingCancellationService {

    private static final long MENTEE_FREE_CANCEL_DEADLINE_MINUTES = 4 * 60;
    private static final long MENTOR_SAFE_CANCEL_DEADLINE_MINUTES = 6 * 60;

    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
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
    private MentorViolationService mentorViolationService;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
    }

    @Autowired(required = false)
    void setMentorViolationService(MentorViolationService mentorViolationService) {
        this.mentorViolationService = mentorViolationService;
    }

    @Transactional
    public BookingResponse cancelBookingByMentor(UUID mentorUserId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        // Canonical order: Booking -> MentorProfile -> Slot -> payment locks.
        Booking booking = getBookingForCancellation(bookingId);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR && booking.getCancelledAt() != null) {
            return bookingResponseMapper.toBookingResponse(booking);
        }

        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        long minutesUntilStart = minutesUntilStart(booking, nowUtc);
        if (!BookingActionPolicy.canCancelByMentor(booking.getStatus(), minutesUntilStart > 0)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor chỉ có thể hủy booking đã được chấp nhận");
        }

        MentorProfile lockedMentorProfile = mentorProfileRepository.findByIdForUpdate(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        MentorAvailabilitySlot slot = booking.getSlot() == null ? null
                : mentorAvailabilitySlotRepository.findByIdForUpdate(booking.getSlot().getId()).orElse(null);

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.CANCEL_BY_MENTOR, nowUtc);
        booking.setCancelReason(requiredCancelReason(request));

        refreshSlotBookedFlag(slot);

        if (entityManager != null) {
            entityManager.refresh(lockedMentorProfile);
        }
        lockedMentorProfile.setTotalMentorCancelledBookings(defaultInteger(lockedMentorProfile.getTotalMentorCancelledBookings()) + 1);
        touchMentorActivity(lockedMentorProfile, nowBusiness);
        mentorProfileRepository.save(lockedMentorProfile);

        Booking savedBooking = bookingRepository.save(booking);
        if (mentorViolationService != null && minutesUntilStart < MENTOR_SAFE_CANCEL_DEADLINE_MINUTES) {
            mentorViolationService.record(mentorUserId, savedBooking.getId(), MentorViolationType.LATE_CANCELLATION,
                    "Mentor hủy booking khi còn " + minutesUntilStart + " phút trước giờ bắt đầu.");
        }

        if (sessionService != null) {
            sessionService.cancelForBooking(bookingId);
        }
        eventPublisher.publishEvent(new GoogleCalendarCancelBookingRequestedEvent(bookingId, savedBooking.getStatus()));
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
                .createdAt(nowBusiness)
                .build());

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentor đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : nowBusiness
        ));
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse cancelBookingByMentee(UUID menteeId, UUID bookingId, CancelBookingRequest request) {
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        // Canonical order: Booking -> Slot -> payment locks.
        Booking booking = getBookingForCancellation(bookingId);
        if (menteeId == null || booking.getMentee() == null || !menteeId.equals(booking.getMentee().getId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền hủy booking này");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE && booking.getCancelledAt() != null) {
            return bookingResponseMapper.toBookingResponse(booking);
        }
        Instant nowUtc = timeProvider.instant();
        LocalDateTime nowBusiness = timeProvider.nowBusiness();
        BookingStatus currentStatus = booking.getStatus();
        long minutesUntilStart = minutesUntilStart(booking, nowUtc);
        if (!BookingActionPolicy.canCancelByMentee(currentStatus, minutesUntilStart > 0)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể hủy booking đang chờ phản hồi hoặc đã được chấp nhận");
        }
        MentorAvailabilitySlot slot = booking.getSlot() == null ? null
                : mentorAvailabilitySlotRepository.findByIdForUpdate(booking.getSlot().getId()).orElse(null);

        boolean lateCancellation = currentStatus == BookingStatus.PAID
                && minutesUntilStart < MENTEE_FREE_CANCEL_DEADLINE_MINUTES;

        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.CANCEL_BY_MENTEE, nowUtc);
        booking.setCancelReason(requiredCancelReason(request));

        if (slot != null && currentStatus != BookingStatus.PENDING) {
            refreshSlotBookedFlag(slot);
        }

        Booking savedBooking = bookingRepository.save(booking);

        if (sessionService != null) {
            sessionService.cancelForBooking(bookingId);
        }
        eventPublisher.publishEvent(new GoogleCalendarCancelBookingRequestedEvent(bookingId, savedBooking.getStatus()));
        if (paymentOrderService != null && (currentStatus == BookingStatus.ACCEPTED_AWAITING_PAYMENT || currentStatus == BookingStatus.PAID)) {
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
                .createdAt(nowBusiness)
                .build());

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentee đã hủy lịch học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : nowBusiness
        ));
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private Booking getBookingForCancellation(UUID bookingId) {
        return bookingRepository.findByIdForCancellation(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
    }

    private boolean isMentorOfBooking(Booking booking, UUID mentorUserId) {
        return booking.getMentorProfile() != null
                && mentorUserId != null
                && mentorUserId.equals(booking.getMentorProfile().getUserId());
    }

    private long minutesUntilStart(Booking booking, Instant nowUtc) {
        Instant startUtc = booking.getSelectedStartTimeUtc() != null ? booking.getSelectedStartTimeUtc()
                : (booking.getSelectedStartTime() != null ? BookingTime.toInstant(booking.getSelectedStartTime())
                : (booking.getSlot() != null && booking.getSlot().getStartTimeUtc() != null ? booking.getSlot().getStartTimeUtc()
                : (booking.getSlot() != null && booking.getSlot().getStartTime() != null ? BookingTime.toInstant(booking.getSlot().getStartTime()) : null)));
        if (startUtc == null) {
            return 0;
        }
        return Duration.between(nowUtc, startUtc).toMinutes();
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) {
            return;
        }
        Instant startUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc()
                : (slot.getStartTime() == null ? null : BookingTime.toInstant(slot.getStartTime()));
        Instant endUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc()
                : (slot.getEndTime() == null ? null : BookingTime.toInstant(slot.getEndTime()));
        boolean hasConfirmedOverlap = startUtc != null && endUtc != null
                && bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(
                slot.getId(),
                SLOT_LOCKING_STATUSES,
                startUtc,
                endUtc
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
        profile.setLastActiveAt(activityAt != null ? activityAt : timeProvider.nowBusiness());
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
