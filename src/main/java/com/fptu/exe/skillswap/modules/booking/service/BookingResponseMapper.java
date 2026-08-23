package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.service.BookingCancellationRefundPolicy;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingDisplayState;
import com.fptu.exe.skillswap.modules.booking.domain.BookingLifecycleStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingNextAction;
import com.fptu.exe.skillswap.modules.booking.domain.BookingPaymentStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMapper;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingDeadlinePolicy.resolvePaymentDeadline;

@Component
@RequiredArgsConstructor
public class BookingResponseMapper {

    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProperties paymentProperties;

    public BookingResponse toBookingResponse(Booking booking) {
        return toBookingResponse(booking, null, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking, Map<UUID, UUID> bookingToConversationMap) {
        return toBookingResponse(booking, bookingToConversationMap, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking,
                                            Map<UUID, UUID> bookingToConversationMap,
                                            Map<UUID, Session> sessionsByBookingId) {
        return toBookingResponse(booking, bookingToConversationMap, sessionsByBookingId, null);
    }

    public BookingResponse toBookingResponse(Booking booking,
                                            Map<UUID, UUID> bookingToConversationMap,
                                            Map<UUID, Session> sessionsByBookingId,
                                            Map<UUID, PaymentOrder> paymentOrdersByBookingId) {
        if (booking == null) {
            return null;
        }

        User mentee = booking.getMentee();
        MentorProfile mentorProfile = booking.getMentorProfile();
        User mentorUser = mentorProfile == null ? null : mentorProfile.getUser();
        MentorService mentorService = booking.getService();
        MentorAvailabilitySlot slot = booking.getSlot();
        PaymentOrder paymentOrder = resolvePaymentOrder(booking, paymentOrdersByBookingId);

        Session session = null;
        if (sessionsByBookingId != null) {
            session = sessionsByBookingId.get(booking.getId());
        }
        if (session == null && sessionService != null) {
            session = sessionService.findByBookingId(booking.getId());
        }

        MeetingPlatform platform = session != null ? session.getMeetingPlatform() : booking.getMeetingPlatform();
        String link = session != null ? session.getMeetingLink() : booking.getMeetingLink();

        LocalDateTime actualStart = session != null ? session.getActualStartTime() : booking.getActualStartTime();
        LocalDateTime actualEnd = session != null ? session.getActualEndTime() : booking.getActualEndTime();

        UUID conversationId = null;
        if (bookingToConversationMap != null && bookingToConversationMap.containsKey(booking.getId())) {
            conversationId = bookingToConversationMap.get(booking.getId());
        } else if (conversationService != null) {
            Conversation conv = conversationService.findByBookingId(booking.getId());
            if (conv == null && mentee != null && mentee.getId() != null && mentorUser != null && mentorUser.getId() != null) {
                conv = conversationService.findDirectByParticipants(mentorUser.getId(), mentee.getId());
            }
            if (conv != null) {
                conversationId = conv.getId();
            }
        }

        UUID currentUserId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            currentUserId = ((UserPrincipal) auth.getPrincipal()).getPublicId();
        }

        boolean isMenteeUser = currentUserId != null && mentee != null && currentUserId.equals(mentee.getId());
        boolean isMentorUser = currentUserId != null && mentorProfile != null && currentUserId.equals(mentorProfile.getUserId());

        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime startTime = selectedStartTime(booking);
        LocalDateTime endTime = selectedEndTime(booking);

        boolean canCancel = false;
        boolean canComplete = false;
        boolean canSubmitFeedback = false;

        if (currentUserId != null) {
            boolean beforeSession = startTime != null && now.isBefore(startTime);
            if (booking.getStatus() == BookingStatus.PENDING) {
                canCancel = isMenteeUser && beforeSession;
            } else if (booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT || isScheduledBookingStatus(booking.getStatus())) {
                canCancel = (isMenteeUser || isMentorUser) && beforeSession;
            }

            if (isMentorUser) {
                canComplete = (booking.getStatus() == BookingStatus.PAID || booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION)
                        && endTime != null && now.isAfter(endTime);
            } else if (isMenteeUser) {
                canComplete = (booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION
                        || booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION)
                        && endTime != null
                        && now.isBefore(endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
            }

            canSubmitFeedback = isMenteeUser
                    && booking.getStatus() == BookingStatus.COMPLETED
                    && BookingStateMapper.toCanonicalCompletionOutcome(booking) == BookingCompletionOutcome.USER_CONFIRMED;
        }

        BookingLifecycleStatus bookingLifecycleStatus = BookingStateMapper.toLifecycleStatus(booking, paymentOrder);
        BookingPaymentStatus bookingPaymentStatus = BookingStateMapper.toPaymentStatus(booking, paymentOrder);
        BookingCompletionOutcome completionOutcome = booking.getCompletionOutcome();
        if (completionOutcome == null) {
            completionOutcome = BookingStateMapper.toCanonicalCompletionOutcome(booking);
        }
        BookingDisplayGuidance displayGuidance = deriveDisplayGuidance(
                booking, isMenteeUser, isMentorUser, now, startTime, endTime, completionOutcome, canSubmitFeedback
        );

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .sessionId(booking.getId())
                .sessionStatus(booking.getStatus())
                .actualSessionId(session == null ? null : session.getId())
                .actualSessionStatus(session == null ? null : session.getStatus())
                .mentorUserId(mentorProfile == null ? null : mentorProfile.getUserId())
                .mentorDisplayName(mentorUser == null ? null : mentorUser.getFullName())
                .mentorAvatarUrl(mentorUser == null ? null : mentorUser.getAvatarUrl())
                .menteeUserId(mentee == null ? null : mentee.getId())
                .menteeDisplayName(mentee == null ? null : mentee.getFullName())
                .menteeAvatarUrl(mentee == null ? null : mentee.getAvatarUrl())
                .availabilitySlotId(slot == null ? null : slot.getId())
                .serviceId(mentorService == null ? null : mentorService.getId())
                .serviceTitle(booking.getServiceTitleSnapshot() != null ? booking.getServiceTitleSnapshot() : (mentorService == null ? null : mentorService.getTitle()))
                .serviceDescriptionSnapshot(booking.getServiceDescriptionSnapshot())
                .serviceExpectedOutcomeSnapshot(booking.getServiceExpectedOutcomeSnapshot())
                .serviceDurationSnapshot(booking.getServiceDurationSnapshot())
                .serviceIsFreeSnapshot(booking.getServiceIsFreeSnapshot())
                .servicePriceScoinSnapshot(booking.getServicePriceScoinSnapshot())
                .maintainPostSessionChatSnapshot(booking.isMaintainPostSessionChatSnapshot())
                .servicePriceWithSurchargeScoin(calculateMenteeVisiblePrice(booking.getServiceIsFreeSnapshot(), booking.getServicePriceScoinSnapshot()))
                .status(booking.getStatus())
                .bookingStatus(bookingLifecycleStatus)
                .paymentStatus(bookingPaymentStatus)
                .settlementStatus(paymentOrder == null ? null : paymentOrder.getSettlementStatus())
                .releasedAt(paymentOrder == null ? null : paymentOrder.getReleasedAt())
                .refundedAt(paymentOrder == null ? null : paymentOrder.getRefundedAt())
                .refundedScoin(paymentOrder == null ? null : paymentOrder.getRefundedScoin())
                .refundReason(paymentOrder == null ? null : paymentOrder.getRefundReason())
                .learningGoalTitle(booking.getLearningGoalTitle())
                .learningGoalDescription(booking.getLearningGoalDescription())
                .mentorResponseNote(booking.getMentorResponseNote())
                .rejectReason(booking.getRejectReason())
                .cancelReason(booking.getCancelReason())
                .meetingPlatform(platform)
                .meetingLink(link)
                .calendarSyncStatus(session == null || session.getCalendarSyncStatus() == null ? null : session.getCalendarSyncStatus().name())
                .calendarSyncErrorCode(session == null ? null : session.getCalendarSyncErrorCode())
                .calendarSyncErrorMessage(session == null ? null : session.getCalendarSyncErrorMessage())
                .googleMeetAutoGenerated(session == null ? null : session.isGoogleMeetAutoGenerated())
                .googleCalendarManaged(session == null ? null : session.isGoogleCalendarManaged())
                .location(booking.getLocation())
                .selectedStartTime(startTime)
                .selectedEndTime(endTime)
                .reviewDeadlineAt(endTime == null ? null : endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS))
                .actualStartTime(actualStart)
                .actualEndTime(actualEnd)
                .acceptedAt(booking.getAcceptedAt())
                .pendingExpireAt(booking.getPendingExpireAt())
                .rejectedAt(booking.getRejectedAt())
                .cancelledAt(booking.getCancelledAt())
                .completedAt(booking.getCompletedAt())
                .finalizedAt(booking.getFinalizedAt())
                .autoClosedAt(booking.getAutoClosedAt())
                .completionOutcome(completionOutcome)
                .issueSubmittedAt(booking.getIssueSubmittedAt())
                .issueType(booking.getIssueType())
                .issueDescription(booking.getIssueDescription())
                .issueRespondedAt(booking.getIssueRespondedAt())
                .issueRespondedByUserId(booking.getIssueRespondedByUserId())
                .issueResponseNote(booking.getIssueResponseNote())
                .issueResolvedAt(booking.getIssueResolvedAt())
                .issueResolvedByUserId(booking.getIssueResolvedByUserId())
                .issueResolutionNote(booking.getIssueResolutionNote())
                .mentorNote(booking.getMentorNote())
                .menteeNote(booking.getMenteeNote())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .conversationId(conversationId)
                .canCancel(canCancel)
                .canComplete(canComplete)
                .canSubmitFeedback(canSubmitFeedback)
                .cancellationRefundPolicy(BookingCancellationRefundPolicy.current())
                .displayState(displayGuidance.state())
                .nextAction(displayGuidance.action())
                .actionDeadlineAt(displayGuidance.deadlineAt())
                .build();
    }

    public BookingDisplayGuidance deriveDisplayGuidance(
            Booking booking,
            boolean isMentee,
            boolean isMentor,
            LocalDateTime now,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BookingCompletionOutcome outcome,
            boolean canSubmitFeedback
    ) {
        if (booking.getStatus() == BookingStatus.UNDER_REVIEW || outcome == BookingCompletionOutcome.UNDER_REVIEW) {
            return new BookingDisplayGuidance(BookingDisplayState.UNDER_REVIEW, BookingNextAction.VIEW_ISSUE, null);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR
                || booking.getStatus() == BookingStatus.REJECTED || booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.NO_SHOW) {
            return new BookingDisplayGuidance(BookingDisplayState.CANCELED_OR_EXPIRED, BookingNextAction.NONE, null);
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            return isMentor
                    ? new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED, BookingNextAction.ACCEPT_OR_REJECT, booking.getPendingExpireAt())
                    : new BookingDisplayGuidance(BookingDisplayState.PENDING_MENTOR_RESPONSE, BookingNextAction.NONE, booking.getPendingExpireAt());
        }
        if (booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            return isMentee
                    ? new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.PAY_NOW, resolvePaymentDeadline(booking))
                    : new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.NONE, resolvePaymentDeadline(booking));
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            return new BookingDisplayGuidance(BookingDisplayState.WAITING_CONFIRMATION,
                    isMentee ? BookingNextAction.CONFIRM_SESSION : BookingNextAction.NONE,
                    endTime == null ? null : endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
        }
        if (booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION) {
            return isMentor
                    ? new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED,
                    BookingNextAction.COMPLETE_SESSION,
                    endTime == null ? null : endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS))
                    : new BookingDisplayGuidance(BookingDisplayState.WAITING_CONFIRMATION,
                    isMentee ? BookingNextAction.CONFIRM_SESSION : BookingNextAction.NONE,
                    endTime == null ? null : endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.AUTO_CLOSED) {
            return canSubmitFeedback
                    ? new BookingDisplayGuidance(BookingDisplayState.FEEDBACK_REQUIRED, BookingNextAction.LEAVE_FEEDBACK, null)
                    : new BookingDisplayGuidance(BookingDisplayState.COMPLETED, BookingNextAction.NONE, null);
        }
        if (endTime != null && !now.isBefore(endTime)) {
            return new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED,
                    isMentor ? BookingNextAction.COMPLETE_SESSION : BookingNextAction.NONE, null);
        }
        if (startTime != null && !now.isBefore(startTime) && (endTime == null || now.isBefore(endTime))) {
            return new BookingDisplayGuidance(BookingDisplayState.IN_SESSION, BookingNextAction.NONE, null);
        }
        return new BookingDisplayGuidance(BookingDisplayState.UPCOMING, BookingNextAction.NONE, null);
    }

    public record BookingDisplayGuidance(BookingDisplayState state, BookingNextAction action, LocalDateTime deadlineAt) {}

    public int calculateMenteeVisiblePrice(Boolean isFree, Integer basePriceScoin) {
        int price = basePriceScoin == null ? 0 : Math.max(0, basePriceScoin);
        if (Boolean.TRUE.equals(isFree) || price == 0) {
            return 0;
        }
        int surchargeBps = paymentProperties == null ? 1000 : paymentProperties.getMenteeSurchargeBps();
        long total = (long) price + ((long) price * surchargeBps) / 10_000L;
        if (total > Integer.MAX_VALUE) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Giá hiển thị cho mentee vượt giới hạn hệ thống");
        }
        return (int) total;
    }

    private PaymentOrder resolvePaymentOrder(Booking booking, Map<UUID, PaymentOrder> paymentOrdersByBookingId) {
        if (booking == null || booking.getId() == null) {
            return null;
        }
        if (paymentOrdersByBookingId != null && paymentOrdersByBookingId.containsKey(booking.getId())) {
            return paymentOrdersByBookingId.get(booking.getId());
        }
        if (paymentOrderRepository == null) {
            return null;
        }
        return paymentOrderRepository.findByTargetTypeAndTargetId(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
    }

    public static LocalDateTime selectedStartTime(Booking booking) {
        if (booking == null) return null;
        return booking.getSelectedStartTime() != null ? booking.getSelectedStartTime()
                : (booking.getSlot() != null ? booking.getSlot().getStartTime() : null);
    }

    public static LocalDateTime selectedEndTime(Booking booking) {
        if (booking == null) return null;
        return booking.getSelectedEndTime() != null ? booking.getSelectedEndTime()
                : (booking.getSlot() != null ? booking.getSlot().getEndTime() : null);
    }

    public static boolean isConfirmedBookingStatus(BookingStatus status) {
        return hasSessionBookingStatus(status);
    }

    /** Booking đã thanh toán và vẫn còn trong phiên học, nên có thể thao tác trước giờ bắt đầu. */
    public static boolean isScheduledBookingStatus(BookingStatus status) {
        return status == BookingStatus.PAID
                || status == BookingStatus.AWAITING_MENTOR_COMPLETION
                || status == BookingStatus.AWAITING_MENTEE_CONFIRMATION;
    }

    /** Booking đã từng có phiên học; dùng khi chỉ cần đọc dữ liệu phiên, không cấp quyền thao tác. */
    public static boolean hasSessionBookingStatus(BookingStatus status) {
        return isScheduledBookingStatus(status)
                || status == BookingStatus.COMPLETED
                || status == BookingStatus.AUTO_CLOSED
                || status == BookingStatus.UNDER_REVIEW;
    }
}
