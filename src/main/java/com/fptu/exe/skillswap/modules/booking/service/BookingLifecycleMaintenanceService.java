package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionAttendanceRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.service.MentorViolationService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.data.domain.Pageable;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns background expiry and post-session transitions, separate from interactive booking commands. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class BookingLifecycleMaintenanceService {

    private static final long PAYMENT_WINDOW_MINUTES = BookingDeadlinePolicy.PAYMENT_WINDOW_MINUTES;
    private static final String PAYMENT_DEADLINE_TEXT = "60 phút hoặc ít nhất 1 giờ trước giờ bắt đầu, tùy thời điểm nào đến trước";
    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.PAID
    );

    private final BookingRepository bookingRepository;
    private final PaymentOrderService paymentOrderService;
    private final SettlementService settlementService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingEventService bookingEventService;
    private final com.fptu.exe.skillswap.modules.identity.port.UserQueryPort userQueryPort;
    private final com.fptu.exe.skillswap.modules.mentor.port.MentorDisciplinePort mentorDisciplinePort;
    private AvailabilityTemplateService availabilityTemplateService;
    private MentorViolationService mentorViolationService;
    private SessionFinalizationService sessionFinalizationService;
    private SessionAttendanceRepository sessionAttendanceRepository;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    public BookingLifecycleMaintenanceService(
            BookingRepository bookingRepository,
            PaymentOrderService paymentOrderService,
            SettlementService settlementService,
            ApplicationEventPublisher eventPublisher,
            BookingEventService bookingEventService
    ) {
        this(bookingRepository, paymentOrderService, settlementService, eventPublisher, bookingEventService, null, null);
    }

    public BookingLifecycleMaintenanceService(
            BookingRepository bookingRepository,
            com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository mentorProfileRepository,
            PaymentOrderService paymentOrderService,
            SettlementService settlementService,
            ApplicationEventPublisher eventPublisher,
            BookingEventService bookingEventService,
            com.fptu.exe.skillswap.modules.identity.repository.UserRepository userRepository
    ) {
        this(bookingRepository, paymentOrderService, settlementService, eventPublisher, bookingEventService,
                userRepository != null ? new com.fptu.exe.skillswap.modules.identity.service.UserQueryPortImpl(userRepository, null) : null,
                mentorProfileRepository != null ? new com.fptu.exe.skillswap.modules.mentor.service.MentorQueryPortImpl(mentorProfileRepository, null) : null);
    }

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

    @Autowired
    void setSessionFinalizationService(SessionFinalizationService sessionFinalizationService) {
        this.sessionFinalizationService = sessionFinalizationService;
    }

    @Autowired(required = false)
    void setSessionAttendanceRepository(SessionAttendanceRepository sessionAttendanceRepository) {
        this.sessionAttendanceRepository = sessionAttendanceRepository;
    }

    @Transactional
    public void rejectAllPendingBookingsForMentor(UUID mentorUserId, String reason) {
        if (mentorUserId == null) {
            return;
        }
        List<Booking> pendingBookings = bookingRepository.findByMentorProfileUserIdAndStatus(mentorUserId, BookingStatus.PENDING);
        if (pendingBookings.isEmpty()) {
            return;
        }
        Instant nowUtc = timeProvider.instant();
        for (Booking booking : pendingBookings) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SYSTEM_REJECT, nowUtc);
            booking.setRejectedAtUtc(nowUtc);
            booking.setRejectedAt(BookingTime.fromInstant(nowUtc));
            booking.setRejectReason(reason);
            if (booking.getSlot() != null) {
                booking.getSlot().setBooked(false);
                markTemplateDue(booking.getSlot());
            }
        }
        bookingRepository.saveAll(pendingBookings);
        for (Booking booking : pendingBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentee().getId(),
                    NotificationType.BOOKING_AUTO_REJECTED,
                    "Yêu cầu đặt lịch không còn hiệu lực",
                    buildAutoRejectedMessage(reason),
                    "BOOKING",
                    booking.getId()
            ));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(),
                    booking.getMentee().getId(),
                    booking.getMentorProfile().getUserId(),
                    booking.getStatus(),
                    "Yêu cầu đặt lịch không còn hiệu lực.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()
            ));
        }
    }

    @Transactional
    public int expireStalePendingBookings() {
        Instant nowUtc = timeProvider.instant();
        List<Booking> staleBookings = bookingRepository.findByStatusAndPendingExpireAtUtcLessThanEqualOrderByPendingExpireAtUtcAsc(
                BookingStatus.PENDING, nowUtc);
        if (staleBookings.isEmpty()) {
            return 0;
        }
        for (Booking booking : staleBookings) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.EXPIRE_PENDING, nowUtc);
            booking.setRejectedAtUtc(nowUtc);
            booking.setRejectedAt(BookingTime.fromInstant(nowUtc));
            booking.setRejectReason("Yêu cầu đặt lịch đã tự động hết hạn vì mentor chưa phản hồi đúng hạn.");
            if (booking.getSlot() != null) {
                booking.getSlot().setBooked(false);
                markTemplateDue(booking.getSlot());
            }
        }
        bookingRepository.saveAll(staleBookings);
        for (Booking booking : staleBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentee().getId(), NotificationType.BOOKING_REQUEST_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì mentor chưa phản hồi đúng hạn. Bạn có thể chọn khung giờ khác để đặt lịch lại.",
                    "BOOKING", booking.getId()));
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentorProfile().getUserId(), NotificationType.BOOKING_REQUEST_EXPIRED,
                    "Yêu cầu đặt lịch đã được giải phóng",
                    "Một yêu cầu booking chưa được phản hồi đã hết hạn. Khung giờ hiện có thể nhận yêu cầu khác.",
                    "BOOKING", booking.getId()));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(), booking.getMentee().getId(), booking.getMentorProfile().getUserId(),
                    booking.getStatus(), "Yêu cầu đặt lịch đã hết hạn.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()));
        }
        return staleBookings.size();
    }

    private void markTemplateDue(MentorAvailabilitySlot slot) {
        if (availabilityTemplateService != null && slot.getId() != null) {
            availabilityTemplateService.markSlotDue(slot.getId());
        }
    }

    @Transactional
    public int expireAwaitingPaymentBookings() {
        Instant nowUtc = timeProvider.instant();
        Instant acceptedCutoff = nowUtc.minus(Duration.ofMinutes(PAYMENT_WINDOW_MINUTES));
        Instant startCutoff = nowUtc.plus(Duration.ofMinutes(BookingDeadlinePolicy.PAYMENT_PREPARATION_MINUTES));
        List<UUID> candidateIds = bookingRepository.findAwaitingPaymentExpiryCandidatesUtc(
                BookingStatus.ACCEPTED_AWAITING_PAYMENT, acceptedCutoff, startCutoff)
                .stream().map(Booking::getId).toList();
        List<Booking> expiredBookings = new ArrayList<>();
        for (UUID bookingId : candidateIds) {
            Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                    || !isPaymentDeadlineReached(booking, nowUtc)) {
                continue;
            }
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.EXPIRE_PAYMENT, nowUtc);
            booking.setRejectedAtUtc(nowUtc);
            booking.setRejectedAt(BookingTime.fromInstant(nowUtc));
            booking.setRejectReason("Yêu cầu đặt lịch đã hết hạn do mentee chưa hoàn tất thanh toán trong vòng "
                    + PAYMENT_DEADLINE_TEXT + ".");
            Instant slotStartUtc = booking.getSlot() != null ? (booking.getSlot().getStartTimeUtc() != null ? booking.getSlot().getStartTimeUtc() : BookingTime.toInstant(booking.getSlot().getStartTime())) : null;
            if (slotStartUtc != null && nowUtc.isBefore(slotStartUtc)) {
                refreshSlotBookedFlag(booking.getSlot());
            }
            paymentOrderService.expireAwaitingPayment(booking);
            expiredBookings.add(booking);
        }
        if (expiredBookings.isEmpty()) {
            return 0;
        }
        bookingRepository.saveAll(expiredBookings);
        for (Booking booking : expiredBookings) {
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentee().getId(), NotificationType.BOOKING_PAYMENT_EXPIRED,
                    "Yêu cầu đặt lịch đã hết hạn thanh toán",
                    "Yêu cầu đặt lịch của bạn đã tự động hết hạn vì chưa hoàn tất thanh toán trong vòng "
                            + PAYMENT_DEADLINE_TEXT + ".",
                    "BOOKING", booking.getId()));
            eventPublisher.publishEvent(new NotificationEvent(
                    booking.getMentorProfile().getUserId(), NotificationType.BOOKING_PAYMENT_EXPIRED,
                    "Khung giờ đã được giải phóng",
                    "Mentee chưa hoàn tất thanh toán đúng hạn. Khung giờ mentoring hiện có thể nhận yêu cầu khác.",
                    "BOOKING", booking.getId()));
            eventPublisher.publishEvent(new com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent(
                    booking.getId(), booking.getMentee().getId(), booking.getMentorProfile().getUserId(),
                    booking.getStatus(), "Yêu cầu đặt lịch đã hết hạn thanh toán.",
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : timeProvider.nowBusiness()));
        }
        return expiredBookings.size();
    }

    @Transactional
    public int processPostSessionLifecycle() {
        Instant nowUtc = timeProvider.instant();
        int changed = 0;
        List<Booking> candidates = new ArrayList<>();
        candidates.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(BookingStatus.PAID, nowUtc));
        candidates.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(BookingStatus.AWAITING_MENTOR_COMPLETION, nowUtc));
        for (Booking candidate : candidates) {
            changed += processPostSessionCandidate(candidate.getId(), nowUtc) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndSelectedEndTimeUtcBeforeOrderBySelectedEndTimeUtcAsc(
                BookingStatus.AWAITING_MENTEE_CONFIRMATION, nowUtc.minus(Duration.ofHours(PostSessionPolicy.AUTO_CLOSE_WARNING_HOURS)))) {
            changed += processPostSessionCandidate(candidate.getId(), nowUtc) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndIssueSubmittedAtUtcBeforeOrderByIssueSubmittedAtUtcAsc(
                BookingStatus.UNDER_REVIEW, nowUtc.minus(Duration.ofHours(12)))) {
            changed += processIssueDeadline(candidate.getId(), nowUtc) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndIssueSubmittedAtUtcBeforeAndAdminSlaWarningSentAtIsNullAndIssueResolvedAtIsNullOrderByIssueSubmittedAtUtcAsc(
                BookingStatus.UNDER_REVIEW, nowUtc.minus(Duration.ofHours(48)))) {
            changed += processAdminDisputeSlaWarning(candidate.getId(), nowUtc) ? 1 : 0;
        }
        return changed;
    }

    private boolean processPostSessionCandidate(UUID bookingId, Instant nowUtc) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null) return false;

        BookingStatus oldStatus = booking.getStatus();
        Instant endUtc = BookingTime.resolveSelectedEndUtc(booking);

        if (oldStatus == BookingStatus.PAID
                && endUtc != null && !nowUtc.isBefore(endUtc)) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SESSION_ENDED, nowUtc);
            booking.setPostSessionPromptedAtUtc(nowUtc);
            booking.setPostSessionPromptedAt(BookingTime.fromInstant(nowUtc));
            recordEvent(booking, BookingEventType.POST_SESSION_STARTED, oldStatus, BookingEventActorType.SYSTEM);
            notifyPostSessionPrompt(booking);
            return true;
        }
        if (oldStatus == BookingStatus.AWAITING_MENTOR_COMPLETION) {
            if (endUtc == null) return false;
            if (!nowUtc.isBefore(endUtc.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))) {
                if (booking.getMentorCompletionOverdueAtUtc() == null && booking.getMentorCompletionOverdueAt() == null) {
                    booking.setMentorCompletionOverdueAtUtc(nowUtc);
                    booking.setMentorCompletionOverdueAt(BookingTime.fromInstant(nowUtc));
                    recordMentorViolation(booking, MentorViolationType.COMPLETION_OVERDUE,
                            "Mentor không xác nhận hoàn tất trong 24 giờ sau buổi học.");
                }
                recordEvent(booking, BookingEventType.MENTOR_COMPLETION_OVERDUE, oldStatus, BookingEventActorType.SYSTEM);
                BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_CLOSE, nowUtc);
                booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
                sessionFinalizationService.finalizeDeliveredSession(booking, nowUtc);
                settlementService.releaseForBooking(booking);
                recordEvent(booking, BookingEventType.AUTO_CLOSED, oldStatus, BookingEventActorType.SYSTEM);
                notifyMentor(booking, "Booking đã tự động hoàn tất",
                        "Bạn không xác nhận hoàn tất trong 24 giờ. Booking đã tự đóng và hệ thống ghi nhận điểm vi phạm nội bộ.");
                notifyMentee(booking, "Booking đã tự động hoàn tất",
                        "Không có báo cáo vấn đề trong 24 giờ nên booking đã tự đóng và tiền được xử lý theo chính sách.");
                return true;
            }
            if (booking.getMentorCompletionReminder30mAtUtc() == null && booking.getMentorCompletionReminder30mAt() == null && !nowUtc.isBefore(endUtc.plus(Duration.ofMinutes(30)))) {
                booking.setMentorCompletionReminder30mAtUtc(nowUtc);
                booking.setMentorCompletionReminder30mAt(BookingTime.fromInstant(nowUtc));
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Buổi mentoring đã kết thúc. Vui lòng xác nhận hoàn tất trong thời hạn cho phép.");
                return true;
            }
            if (booking.getMentorCompletionReminder1hAtUtc() == null && booking.getMentorCompletionReminder1hAt() == null && !nowUtc.isBefore(endUtc.plus(Duration.ofHours(3)))) {
                booking.setMentorCompletionReminder1hAtUtc(nowUtc);
                booking.setMentorCompletionReminder1hAt(BookingTime.fromInstant(nowUtc));
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Bạn vẫn chưa xác nhận hoàn tất buổi mentoring.");
                return true;
            }
            if (booking.getMenteeCompletionPromptedAtUtc() == null && booking.getMenteeCompletionPromptedAt() == null && !nowUtc.isBefore(endUtc.plus(Duration.ofHours(2)))) {
                booking.setMenteeCompletionPromptedAtUtc(nowUtc);
                booking.setMenteeCompletionPromptedAt(BookingTime.fromInstant(nowUtc));
                notifyMentee(booking, "Chưa nhận được xác nhận từ mentor", "Bạn có thể tiếp tục chờ hoặc báo vấn đề nếu buổi mentoring không diễn ra.");
                return true;
            }
            return false;
        }
        if (oldStatus == BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            if (endUtc == null) return false;
            if (!nowUtc.isBefore(endUtc.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))) {
                BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_CLOSE, nowUtc);
                booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
                sessionFinalizationService.finalizeDeliveredSession(booking, nowUtc);
                settlementService.releaseForBooking(booking);
                recordEvent(booking, BookingEventType.AUTO_CLOSED, oldStatus, BookingEventActorType.SYSTEM);
                return true;
            }
            if (booking.getAutoCloseWarningSentAtUtc() == null && booking.getAutoCloseWarningSentAt() == null && !nowUtc.isBefore(endUtc.plus(Duration.ofHours(PostSessionPolicy.AUTO_CLOSE_WARNING_HOURS)))) {
                booking.setAutoCloseWarningSentAtUtc(nowUtc);
                booking.setAutoCloseWarningSentAt(BookingTime.fromInstant(nowUtc));
                notifyMentee(booking, "Buổi mentoring sắp tự đóng", "Bạn còn một giờ để xác nhận hoặc báo vấn đề.");
                notifyMentor(booking, "Buổi mentoring sắp tự đóng", "Nếu không có issue, settlement sẽ được release khi booking tự đóng.");
                return true;
            }
        }
        return false;
    }

    private boolean processIssueDeadline(UUID bookingId, Instant nowUtc) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.UNDER_REVIEW
                || (booking.getIssueSubmittedAtUtc() == null && booking.getIssueSubmittedAt() == null)
                || (booking.getIssueType() != BookingIssueType.MENTOR_NO_SHOW && booking.getIssueType() != BookingIssueType.MENTEE_NO_SHOW)
                || booking.getIssueRespondedAtUtc() != null || booking.getIssueRespondedAt() != null) return false;

        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());

        if (!nowUtc.isBefore(submittedUtc.plus(Duration.ofHours(24)))) {
            if (!hasOneSidedAttendanceSupportingIssue(booking)) {
                if (!"SYSTEM_ATTENDANCE_REQUIRES_ADMIN_REVIEW".equals(booking.getIssueResolutionNote())) {
                    booking.setIssueResolutionNote("SYSTEM_ATTENDANCE_REQUIRES_ADMIN_REVIEW");
                    return true;
                }
                return false;
            }
            BookingStatus old = booking.getStatus();
            if (booking.getIssueResolvedAtUtc() == null && booking.getIssueResolvedAt() == null) {
                booking.setIssueResolvedAtUtc(nowUtc);
                booking.setIssueResolvedAt(BookingTime.fromInstant(nowUtc));
                booking.setIssueResolutionNote("SYSTEM_AUTO_RESOLUTION_NO_COUNTERPARTY_RESPONSE");
            }
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) {
                BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_RESOLVE_MENTOR_NO_SHOW, nowUtc);
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
                sessionFinalizationService.markSessionNotDelivered(booking);
                settlementService.refundForMentorNoShow(booking);
                recordMentorViolation(booking, MentorViolationType.MENTOR_NO_SHOW,
                        "Hệ thống xác nhận mentor no-show do không phản hồi báo cáo đúng hạn.");
            } else {
                BookingTransitionExecutor.apply(booking, BookingTransitionCommand.AUTO_RESOLVE_MENTEE_NO_SHOW, nowUtc);
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
                sessionFinalizationService.markSessionNotDelivered(booking);
                settlementService.releaseForBooking(booking);
            }
            recordEvent(booking, BookingEventType.ISSUE_RESOLVED, old, BookingEventActorType.SYSTEM);
            return true;
        }
        if (booking.getIssueEscalationSentAtUtc() == null && booking.getIssueEscalationSentAt() == null && !nowUtc.isBefore(submittedUtc.plus(Duration.ofHours(12)))) {
            booking.setIssueEscalationSentAtUtc(nowUtc);
            booking.setIssueEscalationSentAt(BookingTime.fromInstant(nowUtc));
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) notifyMentor(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            else notifyMentee(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            return true;
        }
        return false;
    }

    /**
     * Check-in is supporting evidence only. An unattended or contradictory case must remain
     * with admin instead of automatically moving money based solely on an unanswered claim.
     */
    private boolean hasOneSidedAttendanceSupportingIssue(Booking booking) {
        // Compatibility for isolated legacy unit tests that construct this service without Spring.
        if (sessionAttendanceRepository == null) {
            return true;
        }
        List<SessionAttendance> attendances = sessionAttendanceRepository.findByBookingId(booking.getId());
        boolean mentorCheckedIn = attendances.stream()
                .anyMatch(item -> item.getParticipantRole() == SessionParticipantRole.MENTOR);
        boolean menteeCheckedIn = attendances.stream()
                .anyMatch(item -> item.getParticipantRole() == SessionParticipantRole.MENTEE);
        return booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW
                ? menteeCheckedIn && !mentorCheckedIn
                : mentorCheckedIn && !menteeCheckedIn;
    }

    private boolean processAdminDisputeSlaWarning(UUID bookingId, Instant nowUtc) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.UNDER_REVIEW
                || (booking.getIssueSubmittedAtUtc() == null && booking.getIssueSubmittedAt() == null)
                || (booking.getAdminSlaWarningSentAtUtc() != null || booking.getAdminSlaWarningSentAt() != null)
                || (booking.getIssueResolvedAtUtc() != null || booking.getIssueResolvedAt() != null)) {
            return false;
        }
        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        if (nowUtc.isBefore(submittedUtc.plus(Duration.ofHours(48)))) {
            return false;
        }
        booking.setAdminSlaWarningSentAtUtc(nowUtc);
        booking.setAdminSlaWarningSentAt(BookingTime.fromInstant(nowUtc));
        bookingRepository.save(booking);
        recordEvent(booking, BookingEventType.ADMIN_SLA_WARNING_SENT, booking.getStatus(), BookingEventActorType.SYSTEM);
        notifyAdminsDisputeSlaBreach(booking);
        return true;
    }

    private void notifyAdminsDisputeSlaBreach(Booking booking) {
        if (userQueryPort == null) {
            return;
        }
        List<User> admins = new ArrayList<>();
        admins.addAll(userQueryPort.findUsersByRole(RoleCode.ADMIN, Pageable.unpaged()).getContent());
        admins.addAll(userQueryPort.findUsersByRole(RoleCode.SYSTEM_ADMIN, Pageable.unpaged()).getContent());
        java.util.Map<UUID, User> activeAdmins = admins.stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        for (User admin : activeAdmins.values()) {
            eventPublisher.publishEvent(new NotificationEvent(
                    admin.getId(),
                    NotificationType.ADMIN_DISPUTE_SLA_BREACH,
                    "Cảnh báo SLA Khiếu nại Booking",
                    "Booking #" + booking.getId() + " có khiếu nại chưa được Admin xử lý sau 48 giờ.",
                    "BOOKING",
                    booking.getId()
            ));
        }
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) return;
        Instant startUtc = slot.getStartTimeUtc() != null ? slot.getStartTimeUtc()
                : (slot.getStartTime() == null ? null : BookingTime.toInstant(slot.getStartTime()));
        Instant endUtc = slot.getEndTimeUtc() != null ? slot.getEndTimeUtc()
                : (slot.getEndTime() == null ? null : BookingTime.toInstant(slot.getEndTime()));
        slot.setBooked(startUtc != null && endUtc != null
                && bookingRepository.existsOverlappingBySlotIdAndStatusInUtc(slot.getId(), SLOT_LOCKING_STATUSES, startUtc, endUtc));
        if (availabilityTemplateService != null) {
            availabilityTemplateService.markSlotDue(slot.getId());
        }
    }

    private boolean isPaymentDeadlineReached(Booking booking, Instant nowUtc) {
        return BookingDeadlinePolicy.isPaymentDeadlineReachedUtc(booking, nowUtc);
    }

    private void recordMentorViolation(Booking booking, MentorViolationType type, String reason) {
        if (mentorViolationService == null || booking == null || booking.getMentorProfile() == null) return;
        mentorViolationService.record(booking.getMentorProfile().getUserId(), booking.getId(), type, reason);
    }

    private void recordEvent(Booking booking, BookingEventType type, BookingStatus oldStatus, BookingEventActorType actor) {
        if (bookingEventService != null) bookingEventService.record(booking, type, oldStatus, actor, null, null);
    }

    private void notifyPostSessionPrompt(Booking booking) {
        notifyMentor(booking, "Buổi mentoring đã kết thúc", "Vui lòng xác nhận hoàn tất trong 24 giờ.");
        notifyMentee(booking, "Buổi mentoring đã kết thúc", "Bạn có thể báo vấn đề nếu buổi mentoring không diễn ra như mong đợi.");
    }

    private void notifyMentor(Booking booking, String title, String message) {
        if (booking.getMentorProfile() != null) eventPublisher.publishEvent(new NotificationEvent(
                booking.getMentorProfile().getUserId(), NotificationType.SESSION_COMPLETED, title, message, "BOOKING", booking.getId()));
    }

    private void notifyMentee(Booking booking, String title, String message) {
        if (booking.getMentee() != null) eventPublisher.publishEvent(new NotificationEvent(
                booking.getMentee().getId(), NotificationType.SESSION_COMPLETED, title, message, "BOOKING", booking.getId()));
    }

    private String buildAutoRejectedMessage(String reason) {
        return reason == null || reason.isBlank()
                ? "Yêu cầu đặt lịch của bạn đã bị từ chối do thay đổi về trạng thái nhận lịch của mentor."
                : "Yêu cầu đặt lịch của bạn đã bị từ chối: " + reason.trim();
    }
}
