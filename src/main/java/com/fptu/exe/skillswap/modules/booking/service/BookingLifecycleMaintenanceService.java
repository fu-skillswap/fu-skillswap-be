package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns background expiry and post-session transitions, separate from interactive booking commands. */
@Service
@RequiredArgsConstructor
public class BookingLifecycleMaintenanceService {

    private static final long PAYMENT_WINDOW_MINUTES = BookingDeadlinePolicy.PAYMENT_WINDOW_MINUTES;
    private static final String PAYMENT_DEADLINE_TEXT = "6 giờ hoặc ít nhất 1 giờ trước giờ bắt đầu, tùy thời điểm nào đến trước";
    private static final List<BookingStatus> SLOT_LOCKING_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT,
            BookingStatus.ACCEPTED,
            BookingStatus.PAID
    );

    private final BookingRepository bookingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final PaymentOrderService paymentOrderService;
    private final SettlementService settlementService;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingEventService bookingEventService;
    private final GroupSessionCommerceService groupSessionCommerceService;
    private GroupSessionExperienceService groupSessionExperienceService;
    private AvailabilityTemplateService availabilityTemplateService;

    @Autowired(required = false)
    void setGroupSessionExperienceService(GroupSessionExperienceService groupSessionExperienceService) {
        this.groupSessionExperienceService = groupSessionExperienceService;
    }

    @Autowired(required = false)
    void setAvailabilityTemplateService(AvailabilityTemplateService availabilityTemplateService) {
        this.availabilityTemplateService = availabilityTemplateService;
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
        for (Booking booking : pendingBookings) {
            booking.setStatus(BookingStatus.REJECTED);
            booking.setRejectedAt(DateTimeUtil.now());
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
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()
            ));
        }
    }

    @Transactional
    public int expireStalePendingBookings() {
        LocalDateTime now = DateTimeUtil.now();
        List<Booking> staleBookings = bookingRepository.findPendingExpiryCandidatesForUpdate(
                BookingStatus.PENDING, now);
        if (staleBookings.isEmpty()) {
            return 0;
        }
        for (Booking booking : staleBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setRejectedAt(now);
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
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()));
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
        LocalDateTime now = DateTimeUtil.now();
        List<UUID> candidateIds = bookingRepository.findAwaitingPaymentExpiryCandidates(
                BookingStatus.ACCEPTED_AWAITING_PAYMENT, now.minusMinutes(PAYMENT_WINDOW_MINUTES),
                now.plusMinutes(BookingDeadlinePolicy.PAYMENT_PREPARATION_MINUTES), now)
                .stream().map(Booking::getId).toList();
        List<Booking> expiredBookings = new ArrayList<>();
        for (UUID bookingId : candidateIds) {
            Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.ACCEPTED_AWAITING_PAYMENT
                    || !isPaymentDeadlineReached(booking, now)) {
                continue;
            }
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setRejectedAt(now);
            booking.setRejectReason("Yêu cầu đặt lịch đã hết hạn do mentee chưa hoàn tất thanh toán trong vòng "
                    + PAYMENT_DEADLINE_TEXT + ".");
            if (booking.getGroupSession() != null) {
                groupSessionCommerceService.releaseSeatForTerminalTransition(booking, BookingStatus.ACCEPTED_AWAITING_PAYMENT);
            } else if (booking.getSlot() != null && booking.getSlot().getStartTime() != null
                    && now.isBefore(booking.getSlot().getStartTime())) {
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
                    booking.getUpdatedAt() != null ? booking.getUpdatedAt() : DateTimeUtil.now()));
        }
        return expiredBookings.size();
    }

    @Transactional
    public int processPostSessionLifecycle() {
        LocalDateTime now = DateTimeUtil.now();
        int changed = 0;
        List<Booking> candidates = new ArrayList<>();
        candidates.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(BookingStatus.PAID, now));
        candidates.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(BookingStatus.ACCEPTED, now));
        candidates.addAll(bookingRepository.findTop100ByStatusAndSelectedEndTimeBeforeOrderBySelectedEndTimeAsc(BookingStatus.AWAITING_MENTOR_COMPLETION, now));
        for (Booking candidate : candidates) {
            changed += processPostSessionCandidate(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndCompletedAtBeforeOrderByCompletedAtAsc(
                BookingStatus.AWAITING_MENTEE_CONFIRMATION, now.minusHours(4))) {
            changed += processPostSessionCandidate(candidate.getId(), now) ? 1 : 0;
        }
        for (Booking candidate : bookingRepository.findTop100ByStatusAndIssueSubmittedAtBeforeOrderByIssueSubmittedAtAsc(
                BookingStatus.UNDER_REVIEW, now.minusHours(12))) {
            changed += processIssueDeadline(candidate.getId(), now) ? 1 : 0;
        }
        return changed;
    }

    private boolean processPostSessionCandidate(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null) return false;
        if (booking.getGroupSession() != null) {
            return groupSessionExperienceService != null && groupSessionExperienceService.autoCloseIfDue(bookingId, now);
        }
        BookingStatus oldStatus = booking.getStatus();
        if ((oldStatus == BookingStatus.PAID || oldStatus == BookingStatus.ACCEPTED)
                && selectedEndTime(booking) != null && !now.isBefore(selectedEndTime(booking))) {
            booking.setStatus(BookingStatus.AWAITING_MENTOR_COMPLETION);
            booking.setPostSessionPromptedAt(now);
            recordEvent(booking, BookingEventType.POST_SESSION_STARTED, oldStatus, BookingEventActorType.SYSTEM);
            notifyPostSessionPrompt(booking);
            return true;
        }
        if (oldStatus == BookingStatus.AWAITING_MENTOR_COMPLETION) {
            LocalDateTime end = selectedEndTime(booking);
            if (end == null) return false;
            if (booking.getMentorCompletionReminder30mAt() == null && !now.isBefore(end.plusMinutes(30))) {
                booking.setMentorCompletionReminder30mAt(now);
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Buổi mentoring đã kết thúc. Vui lòng xác nhận hoàn tất trong thời hạn cho phép.");
                return true;
            }
            if (booking.getMentorCompletionReminder1hAt() == null && !now.isBefore(end.plusHours(1))) {
                booking.setMentorCompletionReminder1hAt(now);
                notifyMentor(booking, "Nhắc xác nhận hoàn tất", "Bạn vẫn chưa xác nhận hoàn tất buổi mentoring.");
                return true;
            }
            if (booking.getMenteeCompletionPromptedAt() == null && !now.isBefore(end.plusHours(2))) {
                booking.setMenteeCompletionPromptedAt(now);
                notifyMentee(booking, "Chưa nhận được xác nhận từ mentor", "Bạn có thể tiếp tục chờ hoặc báo vấn đề nếu buổi mentoring không diễn ra.");
                return true;
            }
            if (booking.getMentorCompletionOverdueAt() == null && !now.isBefore(end.plusHours(24))) {
                booking.setMentorCompletionOverdueAt(now);
                incrementMentorCompletionOverdue(booking);
                recordEvent(booking, BookingEventType.MENTOR_COMPLETION_OVERDUE, oldStatus, BookingEventActorType.SYSTEM);
                notifyMentor(booking, "Booking quá hạn xác nhận", "Booking đã được đưa vào hàng chờ vận hành vì bạn chưa xác nhận hoàn tất.");
                return true;
            }
            return false;
        }
        if (oldStatus == BookingStatus.AWAITING_MENTEE_CONFIRMATION && booking.getCompletedAt() != null) {
            if (booking.getAutoCloseWarningSentAt() == null && !now.isBefore(booking.getCompletedAt().plusHours(3))) {
                booking.setAutoCloseWarningSentAt(now);
                notifyMentee(booking, "Buổi mentoring sắp tự đóng", "Bạn còn một giờ để xác nhận hoặc báo vấn đề.");
                notifyMentor(booking, "Buổi mentoring sắp tự đóng", "Nếu không có issue, settlement sẽ được release khi booking tự đóng.");
                return true;
            }
            if (!now.isBefore(booking.getCompletedAt().plusHours(4))) {
                booking.setStatus(BookingStatus.COMPLETED);
                booking.setAutoClosedAt(now);
                booking.setFinalizedAt(now);
                booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
                settlementService.releaseForBooking(booking);
                recordEvent(booking, BookingEventType.AUTO_CLOSED, oldStatus, BookingEventActorType.SYSTEM);
                return true;
            }
        }
        return false;
    }

    private boolean processIssueDeadline(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.UNDER_REVIEW || booking.getIssueSubmittedAt() == null
                || (booking.getIssueType() != BookingIssueType.MENTOR_NO_SHOW && booking.getIssueType() != BookingIssueType.MENTEE_NO_SHOW)
                || booking.getIssueRespondedAt() != null) return false;
        if (booking.getIssueEscalationSentAt() == null && !now.isBefore(booking.getIssueSubmittedAt().plusHours(12))) {
            booking.setIssueEscalationSentAt(now);
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) notifyMentor(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            else notifyMentee(booking, "Cần phản hồi issue booking", "Bạn có 12 giờ còn lại để phản hồi báo cáo no-show.");
            return true;
        }
        if (!now.isBefore(booking.getIssueSubmittedAt().plusHours(24))) {
            BookingStatus old = booking.getStatus();
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            if (booking.getIssueType() == BookingIssueType.MENTOR_NO_SHOW) {
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
                settlementService.refundForMentorNoShow(booking);
                incrementMentorNoShow(booking);
                if (booking.getGroupSession() != null && groupSessionExperienceService != null) {
                    groupSessionExperienceService.revokeSeat(booking, true);
                }
            } else {
                booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
                settlementService.releaseForBooking(booking);
            }
            booking.setIssueResolvedAt(now);
            booking.setIssueResolutionNote("SYSTEM_AUTO_RESOLUTION_NO_COUNTERPARTY_RESPONSE");
            recordEvent(booking, BookingEventType.ISSUE_RESOLVED, old, BookingEventActorType.SYSTEM);
            return true;
        }
        return false;
    }

    private void refreshSlotBookedFlag(MentorAvailabilitySlot slot) {
        if (slot == null || slot.getId() == null) return;
        slot.setBooked(bookingRepository.existsOverlappingBySlotIdAndStatusIn(
                slot.getId(), SLOT_LOCKING_STATUSES, slot.getStartTime(), slot.getEndTime()));
        if (availabilityTemplateService != null) {
            availabilityTemplateService.markSlotDue(slot.getId());
        }
    }

    private boolean isPaymentDeadlineReached(Booking booking, LocalDateTime now) {
        if (booking.getGroupSession() != null) {
            LocalDateTime deadline = groupSessionCommerceService.resolvePaymentDeadline(booking);
            return deadline != null && !deadline.isAfter(now);
        }
        LocalDateTime startDeadline = booking.getSelectedStartTime() == null && booking.getSlot() != null
                ? booking.getSlot().getStartTime() : booking.getSelectedStartTime();
        LocalDateTime deadline = BookingDeadlinePolicy.resolvePaymentDeadline(booking.getAcceptedAt(), startDeadline);
        return deadline != null && !deadline.isAfter(now);
    }

    private void incrementMentorNoShow(Booking booking) { incrementMentorCounter(booking, true); }
    private void incrementMentorCompletionOverdue(Booking booking) { incrementMentorCounter(booking, false); }

    private void incrementMentorCounter(Booking booking, boolean noShow) {
        if (booking.getMentorProfile() == null) return;
        mentorProfileRepository.findByIdForUpdate(booking.getMentorProfile().getUserId()).ifPresent(profile -> {
            if (noShow) profile.setMentorNoShowCount(defaultInteger(profile.getMentorNoShowCount()) + 1);
            else profile.setMentorCompletionOverdueCount(defaultInteger(profile.getMentorCompletionOverdueCount()) + 1);
        });
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

    private int defaultInteger(Integer value) { return value == null ? 0 : value; }
    private LocalDateTime selectedEndTime(Booking booking) { return booking.getSelectedEndTime(); }
    private String buildAutoRejectedMessage(String reason) {
        return reason == null || reason.isBlank()
                ? "Yêu cầu đặt lịch của bạn đã bị từ chối do thay đổi về trạng thái nhận lịch của mentor."
                : "Yêu cầu đặt lịch của bạn đã bị từ chối: " + reason.trim();
    }
}
