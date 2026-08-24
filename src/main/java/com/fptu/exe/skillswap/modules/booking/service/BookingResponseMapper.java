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
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendanceSummary;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.SessionAttendanceResponse;
import com.fptu.exe.skillswap.modules.booking.repository.SessionAttendanceRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingDeadlinePolicy.resolvePaymentDeadline;

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;

@Component
public class BookingResponseMapper {

    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProperties paymentProperties;
    private final TimeProvider timeProvider;
    private final SessionAttendanceRepository sessionAttendanceRepository;

    @Autowired
    public BookingResponseMapper(SessionService sessionService,
                                 ConversationService conversationService,
                                 PaymentOrderRepository paymentOrderRepository,
                                 PaymentProperties paymentProperties,
                                 @Autowired(required = false) TimeProvider timeProvider,
                                 SessionAttendanceRepository sessionAttendanceRepository) {
        this.sessionService = sessionService;
        this.conversationService = conversationService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentProperties = paymentProperties;
        this.timeProvider = timeProvider != null ? timeProvider : TimeProvider.from(Clock.systemUTC());
        this.sessionAttendanceRepository = sessionAttendanceRepository;
    }

    public BookingResponseMapper(SessionService sessionService,
                                 ConversationService conversationService,
                                 PaymentOrderRepository paymentOrderRepository,
                                 PaymentProperties paymentProperties,
                                 TimeProvider timeProvider) {
        this(sessionService, conversationService, paymentOrderRepository, paymentProperties, timeProvider, null);
    }

    public BookingResponseMapper(SessionService sessionService,
                                 ConversationService conversationService,
                                 PaymentOrderRepository paymentOrderRepository,
                                 PaymentProperties paymentProperties) {
        this(sessionService, conversationService, paymentOrderRepository, paymentProperties, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking) {
        return toBookingResponse(booking, null, null, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking, Map<UUID, UUID> bookingToConversationMap) {
        return toBookingResponse(booking, bookingToConversationMap, null, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking,
                                            Map<UUID, UUID> bookingToConversationMap,
                                            Map<UUID, Session> sessionsByBookingId) {
        return toBookingResponse(booking, bookingToConversationMap, sessionsByBookingId, null, null);
    }

    public BookingResponse toBookingResponse(Booking booking,
                                            Map<UUID, UUID> bookingToConversationMap,
                                            Map<UUID, Session> sessionsByBookingId,
                                            Map<UUID, PaymentOrder> paymentOrdersByBookingId) {
        return toBookingResponse(booking, bookingToConversationMap, sessionsByBookingId, paymentOrdersByBookingId, null);
    }

    public BookingResponse toBookingResponse(Booking booking,
                                            Map<UUID, UUID> bookingToConversationMap,
                                            Map<UUID, Session> sessionsByBookingId,
                                            Map<UUID, PaymentOrder> paymentOrdersByBookingId,
                                            Map<UUID, List<SessionAttendance>> attendancesBySessionId) {
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

        List<SessionAttendance> attendances = resolveAttendances(session, attendancesBySessionId);

        MeetingPlatform platform = session != null ? session.getMeetingPlatform() : booking.getMeetingPlatform();
        String link = session != null ? session.getMeetingLink() : booking.getMeetingLink();

        Instant actualStartUtc = session != null && session.getActualStartTimeUtc() != null
                ? session.getActualStartTimeUtc() : BookingTime.toInstant(session != null ? session.getActualStartTime() : booking.getActualStartTime());
        Instant actualEndUtc = session != null && session.getActualEndTimeUtc() != null
                ? session.getActualEndTimeUtc() : BookingTime.toInstant(session != null ? session.getActualEndTime() : booking.getActualEndTime());

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

        LocalDateTime now = timeProvider.nowBusiness();
        Instant nowUtc = timeProvider.instant();
        LocalDateTime startTime = selectedStartTime(booking);
        LocalDateTime endTime = selectedEndTime(booking);
        Instant startUtc = BookingTime.resolveSelectedStartUtc(booking);
        Instant endUtc = BookingTime.resolveSelectedEndUtc(booking);

        boolean canCancel = false;
        boolean canComplete = false;
        boolean canPay = false;
        boolean canAccept = false;
        boolean canReject = false;
        boolean canCompleteByMentor = false;
        boolean canConfirmByMentee = false;
        boolean canJoin = false;
        boolean canReportIssue = false;
        boolean canRespondIssue = false;
        boolean canSubmitFeedback = false;
        boolean currentUserCheckedIn = false;
        boolean canCheckIn = false;

        if (currentUserId != null) {
            boolean beforeSession = startTime != null && now.isBefore(startTime);
            LocalDateTime paymentDeadline = resolvePaymentDeadline(booking);
            boolean beforePendingDeadline = booking.getPendingExpireAt() == null || now.isBefore(booking.getPendingExpireAt());
            boolean beforePaymentDeadline = paymentDeadline == null || now.isBefore(paymentDeadline);
            boolean hasMeetingAccess = (link != null && !link.isBlank())
                    || (booking.getLocation() != null && !booking.getLocation().isBlank());

            canCancel = isMenteeUser
                    ? BookingActionPolicy.canCancelByMentee(booking.getStatus(), beforeSession)
                    : isMentorUser && BookingActionPolicy.canCancelByMentor(booking.getStatus(), beforeSession);
            canPay = isMenteeUser && BookingActionPolicy.canPay(booking.getStatus(), beforePaymentDeadline);
            canAccept = isMentorUser && BookingActionPolicy.canAcceptOrReject(booking.getStatus(), beforePendingDeadline);
            canReject = canAccept;
            canCompleteByMentor = isMentorUser
                    && BookingActionPolicy.canMentorComplete(booking.getStatus(), now, endTime);
            canConfirmByMentee = isMenteeUser
                    && BookingActionPolicy.canMenteeConfirm(booking.getStatus(), now, endTime);
            canComplete = canCompleteByMentor || canConfirmByMentee;
            canJoin = (isMenteeUser || isMentorUser)
                    && BookingActionPolicy.canJoin(booking.getStatus(), now, startTime, endTime, hasMeetingAccess);
            canReportIssue = (isMenteeUser || isMentorUser)
                    && BookingActionPolicy.canReportIssue(booking.getStatus(), now, endTime);
            canRespondIssue = (isMenteeUser || isMentorUser)
                    && booking.getStatus() == BookingStatus.UNDER_REVIEW
                    && booking.getIssueSubmittedAt() != null
                    && booking.getIssueRespondedAt() == null
                    && !currentUserId.equals(booking.getIssueSubmittedByUserId())
                    && now.isBefore(booking.getIssueSubmittedAt().plusHours(24));

            canSubmitFeedback = isMenteeUser
                    && booking.getStatus() == BookingStatus.COMPLETED
                    && BookingStateMapper.toCanonicalCompletionOutcome(booking) == BookingCompletionOutcome.USER_CONFIRMED;

            SessionParticipantRole currentRole = isMentorUser ? SessionParticipantRole.MENTOR
                    : isMenteeUser ? SessionParticipantRole.MENTEE : null;
            currentUserCheckedIn = currentRole != null && attendances.stream()
                    .anyMatch(item -> item.getParticipantRole() == currentRole);
            canCheckIn = !currentUserCheckedIn && currentRole != null && session != null
                    && SessionAttendancePolicy.canCheckIn(booking.getStatus(), session.getStatus(), nowUtc, startUtc, endUtc);
        }

        SessionAttendanceResponse attendanceResponse = toAttendanceResponse(
                attendances, currentUserCheckedIn, canCheckIn, startUtc, endUtc
        );

        BookingLifecycleStatus bookingLifecycleStatus = BookingStateMapper.toLifecycleStatus(booking, paymentOrder);
        BookingPaymentStatus bookingPaymentStatus = BookingStateMapper.toPaymentStatus(booking, paymentOrder);
        BookingCompletionOutcome completionOutcome = booking.getCompletionOutcome();
        if (completionOutcome == null) {
            completionOutcome = BookingStateMapper.toCanonicalCompletionOutcome(booking);
        }
        BookingDisplayGuidance displayGuidance = deriveDisplayGuidance(
                booking, isMenteeUser, isMentorUser, now, startTime, endTime,
                completionOutcome, canSubmitFeedback, canJoin
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
                .releasedAt(BookingTime.toOffsetDateTime(paymentOrder == null ? null : paymentOrder.getReleasedAt()))
                .refundedAt(BookingTime.toOffsetDateTime(paymentOrder == null ? null : paymentOrder.getRefundedAt()))
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
                .selectedStartTime(BookingTime.toOffsetDateTime(startTime))
                .selectedEndTime(BookingTime.toOffsetDateTime(endTime))
                .reviewDeadlineAt(BookingTime.toOffsetDateTime(endTime == null ? null : endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))
                .actualStartTime(BookingTime.toOffsetDateTime(actualStartUtc))
                .actualEndTime(BookingTime.toOffsetDateTime(actualEndUtc))
                .attendance(attendanceResponse)
                .acceptedAt(BookingTime.toOffsetDateTime(booking.getAcceptedAt()))
                .pendingExpireAt(BookingTime.toOffsetDateTime(booking.getPendingExpireAt()))
                .rejectedAt(BookingTime.toOffsetDateTime(booking.getRejectedAt()))
                .cancelledAt(BookingTime.toOffsetDateTime(booking.getCancelledAt()))
                .completedAt(BookingTime.toOffsetDateTime(booking.getCompletedAt()))
                .finalizedAt(BookingTime.toOffsetDateTime(booking.getFinalizedAt()))
                .autoClosedAt(BookingTime.toOffsetDateTime(booking.getAutoClosedAt()))
                .completionOutcome(completionOutcome)
                .issueSubmittedAt(BookingTime.toOffsetDateTime(booking.getIssueSubmittedAt()))
                .issueType(booking.getIssueType())
                .issueDescription(booking.getIssueDescription())
                .issueRespondedAt(BookingTime.toOffsetDateTime(booking.getIssueRespondedAt()))
                .issueRespondedByUserId(booking.getIssueRespondedByUserId())
                .issueResponseNote(booking.getIssueResponseNote())
                .issueResolvedAt(BookingTime.toOffsetDateTime(booking.getIssueResolvedAt()))
                .issueResolvedByUserId(booking.getIssueResolvedByUserId())
                .issueResolutionNote(booking.getIssueResolutionNote())
                .mentorNote(booking.getMentorNote())
                .menteeNote(booking.getMenteeNote())
                .createdAt(BookingTime.toOffsetDateTime(booking.getCreatedAt()))
                .updatedAt(BookingTime.toOffsetDateTime(booking.getUpdatedAt()))
                .conversationId(conversationId)
                .canCancel(canCancel)
                .canComplete(canComplete)
                .canPay(canPay)
                .canAccept(canAccept)
                .canReject(canReject)
                .canCompleteByMentor(canCompleteByMentor)
                .canConfirmByMentee(canConfirmByMentee)
                .canJoin(canJoin)
                .canReportIssue(canReportIssue)
                .canRespondIssue(canRespondIssue)
                .joinAvailableAt(BookingTime.toOffsetDateTime(startTime == null ? null : startTime.minusMinutes(BookingActionPolicy.JOIN_EARLY_MINUTES)))
                .joinClosesAt(BookingTime.toOffsetDateTime(endTime == null ? null : endTime.plusMinutes(BookingActionPolicy.JOIN_GRACE_MINUTES)))
                .canSubmitFeedback(canSubmitFeedback)
                .cancellationRefundPolicy(BookingCancellationRefundPolicy.current())
                .displayState(displayGuidance.state())
                .nextAction(displayGuidance.action())
                .actionDeadlineAt(BookingTime.toOffsetDateTime(displayGuidance.deadlineAt()))
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
            boolean canSubmitFeedback,
            boolean canJoin
    ) {
        if (booking.getStatus() == BookingStatus.UNDER_REVIEW || outcome == BookingCompletionOutcome.UNDER_REVIEW) {
            return new BookingDisplayGuidance(BookingDisplayState.UNDER_REVIEW, BookingNextAction.VIEW_ISSUE, null);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED_BY_MENTEE || booking.getStatus() == BookingStatus.CANCELLED_BY_MENTOR
                || booking.getStatus() == BookingStatus.REJECTED || booking.getStatus() == BookingStatus.EXPIRED) {
            return new BookingDisplayGuidance(BookingDisplayState.CANCELED_OR_EXPIRED, BookingNextAction.NONE, null);
        }
        if (booking.getStatus() == BookingStatus.PENDING) {
            boolean decisionWindowOpen = booking.getPendingExpireAt() == null
                    || now.isBefore(booking.getPendingExpireAt());
            return isMentor && decisionWindowOpen
                    ? new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED, BookingNextAction.ACCEPT_OR_REJECT, booking.getPendingExpireAt())
                    : new BookingDisplayGuidance(BookingDisplayState.PENDING_MENTOR_RESPONSE, BookingNextAction.NONE, booking.getPendingExpireAt());
        }
        if (booking.getStatus() == BookingStatus.ACCEPTED_AWAITING_PAYMENT) {
            LocalDateTime paymentDeadline = resolvePaymentDeadline(booking);
            boolean paymentWindowOpen = paymentDeadline == null || now.isBefore(paymentDeadline);
            return isMentee && paymentWindowOpen
                    ? new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.PAY_NOW, paymentDeadline)
                    : new BookingDisplayGuidance(BookingDisplayState.PAYMENT_REQUIRED, BookingNextAction.NONE, paymentDeadline);
        }
        if (endTime != null && !now.isBefore(endTime)
                && (booking.getStatus() == BookingStatus.PAID
                || booking.getStatus() == BookingStatus.AWAITING_MENTOR_COMPLETION
                || booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION)) {
            if (isMentee && now.isBefore(endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS))) {
                return new BookingDisplayGuidance(BookingDisplayState.WAITING_CONFIRMATION,
                        BookingNextAction.CONFIRM_SESSION,
                        endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
            }
            if (isMentor && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
                return new BookingDisplayGuidance(BookingDisplayState.MENTOR_ACTION_REQUIRED,
                        BookingNextAction.COMPLETE_SESSION,
                        endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
            }
            return new BookingDisplayGuidance(BookingDisplayState.WAITING_CONFIRMATION,
                    BookingNextAction.NONE,
                    endTime.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
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
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            return canSubmitFeedback
                    ? new BookingDisplayGuidance(BookingDisplayState.FEEDBACK_REQUIRED, BookingNextAction.LEAVE_FEEDBACK, null)
                    : new BookingDisplayGuidance(BookingDisplayState.COMPLETED, BookingNextAction.NONE, null);
        }
        if (canJoin) {
            BookingDisplayState state = startTime != null && now.isBefore(startTime)
                    ? BookingDisplayState.UPCOMING : BookingDisplayState.IN_SESSION;
            return new BookingDisplayGuidance(state, BookingNextAction.JOIN_SESSION,
                    endTime == null ? null : endTime.plusMinutes(BookingActionPolicy.JOIN_GRACE_MINUTES));
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

    private List<SessionAttendance> resolveAttendances(Session session,
                                                        Map<UUID, List<SessionAttendance>> attendancesBySessionId) {
        if (session == null || session.getId() == null) {
            return Collections.emptyList();
        }
        if (attendancesBySessionId != null) {
            return attendancesBySessionId.getOrDefault(session.getId(), Collections.emptyList());
        }
        if (sessionAttendanceRepository == null) {
            return Collections.emptyList();
        }
        return sessionAttendanceRepository.findBySessionId(session.getId());
    }

    private SessionAttendanceResponse toAttendanceResponse(Collection<SessionAttendance> attendances,
                                                            boolean currentUserCheckedIn,
                                                            boolean canCheckIn,
                                                            Instant startUtc,
                                                            Instant endUtc) {
        SessionAttendance mentorAttendance = attendances.stream()
                .filter(item -> item.getParticipantRole() == SessionParticipantRole.MENTOR)
                .findFirst()
                .orElse(null);
        SessionAttendance menteeAttendance = attendances.stream()
                .filter(item -> item.getParticipantRole() == SessionParticipantRole.MENTEE)
                .findFirst()
                .orElse(null);
        return new SessionAttendanceResponse(
                BookingTime.toOffsetDateTime(mentorAttendance == null ? null : mentorAttendance.getCheckedInAtUtc()),
                BookingTime.toOffsetDateTime(menteeAttendance == null ? null : menteeAttendance.getCheckedInAtUtc()),
                SessionAttendanceSummary.from(mentorAttendance != null, menteeAttendance != null),
                currentUserCheckedIn,
                canCheckIn,
                BookingTime.toOffsetDateTime(startUtc),
                BookingTime.toOffsetDateTime(endUtc)
        );
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
        if (booking.getSelectedStartTime() != null) return booking.getSelectedStartTime();
        if (booking.getSelectedStartTimeUtc() != null) return BookingTime.fromInstant(booking.getSelectedStartTimeUtc());
        return booking.getSlot() != null ? booking.getSlot().getStartTime() : null;
    }

    public static Instant selectedStartTimeUtc(Booking booking) {
        if (booking == null) return null;
        if (booking.getSelectedStartTimeUtc() != null) return booking.getSelectedStartTimeUtc();
        if (booking.getSelectedStartTime() != null) return BookingTime.toInstant(booking.getSelectedStartTime());
        if (booking.getSlot() != null) {
            return booking.getSlot().getStartTimeUtc() != null ? booking.getSlot().getStartTimeUtc()
                    : BookingTime.toInstant(booking.getSlot().getStartTime());
        }
        return null;
    }

    public static LocalDateTime selectedEndTime(Booking booking) {
        if (booking == null) return null;
        if (booking.getSelectedEndTime() != null) return booking.getSelectedEndTime();
        if (booking.getSelectedEndTimeUtc() != null) return BookingTime.fromInstant(booking.getSelectedEndTimeUtc());
        return booking.getSlot() != null ? booking.getSlot().getEndTime() : null;
    }

    public static Instant selectedEndTimeUtc(Booking booking) {
        if (booking == null) return null;
        if (booking.getSelectedEndTimeUtc() != null) return booking.getSelectedEndTimeUtc();
        if (booking.getSelectedEndTime() != null) return BookingTime.toInstant(booking.getSelectedEndTime());
        if (booking.getSlot() != null) {
            return booking.getSlot().getEndTimeUtc() != null ? booking.getSlot().getEndTimeUtc()
                    : BookingTime.toInstant(booking.getSlot().getEndTime());
        }
        return null;
    }

    /** Booking đã thanh toán và vẫn còn trong phiên học, nên có thể thao tác trước giờ bắt đầu. */
    public static boolean isScheduledBookingStatus(BookingStatus status) {
        return BookingActionPolicy.isScheduled(status);
    }

    /** Booking đã từng có phiên học; dùng khi chỉ cần đọc dữ liệu phiên, không cấp quyền thao tác. */
    public static boolean hasSessionBookingStatus(BookingStatus status) {
        return BookingActionPolicy.hasSession(status);
    }
}
