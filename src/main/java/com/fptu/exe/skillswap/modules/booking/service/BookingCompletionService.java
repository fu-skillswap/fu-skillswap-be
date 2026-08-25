package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMapper;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.event.BookingEmailNotificationEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.service.MentorViolationService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final SessionFinalizationService sessionFinalizationService;
    private final SettlementService settlementService;
    private final BookingEventService bookingEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingResponseMapper bookingResponseMapper;
    private final TimeProvider timeProvider;
    private MentorViolationService mentorViolationService;
    private BookingIssueEvidenceService bookingIssueEvidenceService;

    @Autowired
    public BookingCompletionService(
            BookingRepository bookingRepository,
            SessionFinalizationService sessionFinalizationService,
            SettlementService settlementService,
            BookingEventService bookingEventService,
            ApplicationEventPublisher eventPublisher,
            InternalTelemetryService internalTelemetryService,
            BookingResponseMapper bookingResponseMapper,
            TimeProvider timeProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.sessionFinalizationService = sessionFinalizationService;
        this.settlementService = settlementService;
        this.bookingEventService = bookingEventService;
        this.eventPublisher = eventPublisher;
        this.internalTelemetryService = internalTelemetryService;
        this.bookingResponseMapper = bookingResponseMapper;
        this.timeProvider = timeProvider != null ? timeProvider : TimeProvider.from(Clock.systemUTC());
    }

    public BookingCompletionService(
            BookingRepository bookingRepository,
            SessionFinalizationService sessionFinalizationService,
            SettlementService settlementService,
            BookingEventService bookingEventService,
            ApplicationEventPublisher eventPublisher,
            InternalTelemetryService internalTelemetryService,
            BookingResponseMapper bookingResponseMapper
    ) {
        this(bookingRepository, sessionFinalizationService, settlementService, bookingEventService,
                eventPublisher, internalTelemetryService, bookingResponseMapper, null);
    }

    @Autowired(required = false)
    void setMentorViolationService(MentorViolationService mentorViolationService) {
        this.mentorViolationService = mentorViolationService;
    }

    @Autowired
    void setBookingIssueEvidenceService(BookingIssueEvidenceService bookingIssueEvidenceService) {
        this.bookingIssueEvidenceService = bookingIssueEvidenceService;
    }

    @Transactional
    public BookingResponse completeBooking(UUID currentUserId, UUID bookingId, CompleteBookingRequest request) {
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);

        return isMentorOfBooking(booking, currentUserId)
                ? completeBookingByMentor(currentUserId, bookingId, request)
                : confirmBookingByParticipant(currentUserId, bookingId, new ConfirmBookingRequest(
                        request == null ? null : request.completionNote()
                ));
    }

    @Transactional
    public BookingResponse completeBookingByMentor(UUID mentorUserId, UUID bookingId, CompleteBookingRequest request) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Instant nowUtc = timeProvider.instant();
        Booking booking = getBookingForSessionAction(mentorUserId, bookingId);
        requireDirectBooking(booking);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentor của booking mới được xác nhận hoàn tất buổi mentoring");
        }
        if ((booking.getStatus() == BookingStatus.AWAITING_MENTEE_CONFIRMATION
                || booking.getStatus() == BookingStatus.COMPLETED)
                && (booking.getCompletedAtUtc() != null || booking.getCompletedAt() != null)) {
            return bookingResponseMapper.toBookingResponse(booking);
        }
        synchronizePostSessionStatusForPhaseOne(booking, nowUtc);
        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ mentor hoàn tất");
        }
        Instant endUtc = BookingTime.resolveSelectedEndUtc(booking);
        if (endUtc == null || nowUtc.isBefore(endUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chưa thể hoàn tất booking trước khi buổi mentoring kết thúc");
        }
        if (!nowUtc.isBefore(endUtc.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Đã quá thời hạn mentor xác nhận; booking sẽ được hệ thống tự động hoàn tất");
        }

        String completionNote = trimToNull(request == null ? null : request.completionNote());
        booking.setMentorNote(completionNote);
        BookingStatus oldStatus = booking.getStatus();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.MENTOR_COMPLETED, nowUtc);

        sessionFinalizationService.recordMentorReportedCompletion(booking, nowUtc);

        Booking savedBooking = bookingRepository.save(booking);
        recordBookingEvent(savedBooking, BookingEventType.MENTOR_COMPLETED,
                oldStatus, BookingEventActorType.USER, mentorUserId, null);
        eventPublisher.publishEvent(new NotificationEvent(
                savedBooking.getMentee().getId(),
                NotificationType.SESSION_COMPLETED,
                "Mentor đã xác nhận hoàn tất buổi mentoring",
                "Buổi mentoring đã chờ bạn xác nhận hoặc báo vấn đề trong " + PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS + " giờ.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentor đã xác nhận hoàn tất buổi học.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse confirmBookingByParticipant(UUID currentUserId, UUID bookingId, ConfirmBookingRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        Instant nowUtc = timeProvider.instant();
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        synchronizePostSessionStatusForPhaseOne(booking, nowUtc);
        assertBookingAccess(booking, currentUserId);

        if (isMentorOfBooking(booking, currentUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Mentor không thể tự xác nhận thay cho participant còn lại");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED
                && BookingStateMapper.toCanonicalCompletionOutcome(booking) == BookingCompletionOutcome.USER_CONFIRMED) {
            return bookingResponseMapper.toBookingResponse(booking);
        }

        if (booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION
                && booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ xác nhận sau buổi học");
        }
        ensureWithinPostSessionReviewWindow(booking, nowUtc);

        BookingStatus oldStatus = booking.getStatus();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.MENTEE_CONFIRMED, nowUtc);
        booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
        booking.setMenteeNote(trimToNull(request == null ? null : request.confirmationNote()));

        sessionFinalizationService.finalizeDeliveredSession(booking, nowUtc);
        Booking savedBooking = bookingRepository.save(booking);
        if (settlementService != null) {
            settlementService.releaseForBooking(savedBooking);
        }
        recordBookingEvent(savedBooking, BookingEventType.MENTEE_CONFIRMED,
                oldStatus, BookingEventActorType.USER, currentUserId, null);
        if (internalTelemetryService != null) {
            internalTelemetryService.record(
                    "BOOKING_COMPLETED",
                    currentUserId,
                    "BOOKING",
                    savedBooking.getId(),
                    Map.of(
                            "mentorUserId", String.valueOf(savedBooking.getMentorProfile() == null ? null : savedBooking.getMentorProfile().getUserId()),
                            "completionOutcome", String.valueOf(savedBooking.getCompletionOutcome())
                    )
            );
        }
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Mentee xác nhận hoàn tất buổi học thành công.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingIssueResponse submitBookingIssue(UUID currentUserId, UUID bookingId, SubmitBookingIssueRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu báo vấn đề");
        }

        Instant nowUtc = timeProvider.instant();
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        synchronizePostSessionStatusForPhaseOne(booking, nowUtc);
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() == BookingStatus.UNDER_REVIEW
                && currentUserId.equals(booking.getIssueSubmittedByUserId())
                && request.issueType() == booking.getIssueType()) {
            if (!trim(request.description()).equals(booking.getIssueDescription())) {
                throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Nội dung retry không khớp với issue đã gửi");
            }
            requireEvidenceService().assertReporterReplayMatches(booking, currentUserId, request.evidenceIds());
            return BookingIssueResponse.builder()
                    .bookingId(booking.getId()).status(booking.getStatus())
                    .issueSubmittedAt(BookingTime.toOffsetDateTime(booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt())))
                    .issueType(booking.getIssueType())
                    .issueRespondedAt(BookingTime.toOffsetDateTime(booking.getIssueRespondedAtUtc() != null ? booking.getIssueRespondedAtUtc() : (booking.getIssueRespondedAt() != null ? BookingTime.toInstant(booking.getIssueRespondedAt()) : null)))
                    .build();
        }

        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION
                && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái cho phép báo vấn đề");
        }
        Instant endUtc = BookingTime.resolveSelectedEndUtc(booking);
        if (endUtc == null || nowUtc.isBefore(endUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể báo vấn đề sau khi buổi mentoring đã kết thúc");
        }
        ensureWithinIssueWindow(booking, nowUtc);
        validateIssueReporter(booking, currentUserId, request.issueType());
        requireEvidenceService().attachReporterEvidence(booking, currentUserId, request.evidenceIds(), nowUtc);

        BookingStatus oldStatus = booking.getStatus();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ISSUE_REPORTED, nowUtc);
        booking.setIssueSubmittedAtUtc(nowUtc);
        booking.setIssueSubmittedAt(BookingTime.fromInstant(nowUtc));
        booking.setIssueSubmittedByUserId(currentUserId);
        booking.setIssueType(request.issueType());
        booking.setIssueDescription(trim(request.description()));
        booking.setCompletionOutcome(BookingCompletionOutcome.UNDER_REVIEW);

        Booking savedBooking = bookingRepository.save(booking);
        recordBookingEvent(savedBooking, BookingEventType.ISSUE_CREATED,
                oldStatus, BookingEventActorType.USER, currentUserId, null);
        publishIssueReportedNotifications(savedBooking, currentUserId);
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Buổi học đã được báo cáo vấn đề và đang được xem xét.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
        return BookingIssueResponse.builder()
                .bookingId(savedBooking.getId())
                .status(savedBooking.getStatus())
                .issueSubmittedAt(BookingTime.toOffsetDateTime(savedBooking.getIssueSubmittedAtUtc() != null ? savedBooking.getIssueSubmittedAtUtc() : BookingTime.toInstant(savedBooking.getIssueSubmittedAt())))
                .issueType(savedBooking.getIssueType())
                .issueRespondedAt(BookingTime.toOffsetDateTime(savedBooking.getIssueRespondedAtUtc() != null ? savedBooking.getIssueRespondedAtUtc() : (savedBooking.getIssueRespondedAt() != null ? BookingTime.toInstant(savedBooking.getIssueRespondedAt()) : null)))
                .build();
    }

    @Transactional
    public BookingIssueResponse respondToBookingIssue(UUID currentUserId, UUID bookingId, RespondBookingIssueRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (request == null || trimToNull(request.responseNote()) == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu nội dung phản hồi issue");
        }
        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        assertBookingAccess(booking, currentUserId);
        if (booking.getStatus() != BookingStatus.UNDER_REVIEW || (booking.getIssueSubmittedAtUtc() == null && booking.getIssueSubmittedAt() == null)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện không có issue đang mở");
        }
        if (currentUserId.equals(booking.getIssueRespondedByUserId()) && (booking.getIssueRespondedAtUtc() != null || booking.getIssueRespondedAt() != null)) {
            requireEvidenceService().assertResponderReplayMatches(booking, currentUserId, request.evidenceIds());
            return BookingIssueResponse.builder().bookingId(booking.getId()).status(booking.getStatus())
                    .issueSubmittedAt(BookingTime.toOffsetDateTime(booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt())))
                    .issueType(booking.getIssueType())
                    .issueRespondedAt(BookingTime.toOffsetDateTime(booking.getIssueRespondedAtUtc() != null ? booking.getIssueRespondedAtUtc() : BookingTime.toInstant(booking.getIssueRespondedAt())))
                    .build();
        }
        if (booking.getIssueRespondedAtUtc() != null || booking.getIssueRespondedAt() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Issue này đã có phản hồi từ counterparty");
        }
        if (currentUserId.equals(booking.getIssueSubmittedByUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ counterparty của người báo issue mới được phản hồi");
        }
        Instant nowUtc = timeProvider.instant();
        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        if (nowUtc.isAfter(submittedUtc.plus(Duration.ofHours(24)))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn phản hồi issue");
        }
        booking.setIssueRespondedAtUtc(nowUtc);
        booking.setIssueRespondedAt(BookingTime.fromInstant(nowUtc));
        booking.setIssueRespondedByUserId(currentUserId);
        booking.setIssueResponseNote(trimToNull(request.responseNote()));
        requireEvidenceService().attachResponderEvidence(booking, currentUserId, request.evidenceIds(), nowUtc);
        Booking saved = bookingRepository.save(booking);
        recordBookingEvent(saved, BookingEventType.ISSUE_RESPONDED,
                BookingStatus.UNDER_REVIEW, BookingEventActorType.USER, currentUserId, null);
        return BookingIssueResponse.builder().bookingId(saved.getId()).status(saved.getStatus())
                .issueSubmittedAt(BookingTime.toOffsetDateTime(saved.getIssueSubmittedAtUtc() != null ? saved.getIssueSubmittedAtUtc() : BookingTime.toInstant(saved.getIssueSubmittedAt())))
                .issueType(saved.getIssueType())
                .issueRespondedAt(BookingTime.toOffsetDateTime(saved.getIssueRespondedAtUtc() != null ? saved.getIssueRespondedAtUtc() : BookingTime.toInstant(saved.getIssueRespondedAt())))
                .build();
    }

    @Transactional
    public BookingResponse resolveBookingIssue(UUID adminUserId, UUID bookingId, AdminResolveBookingIssueRequest request) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu dữ liệu resolve booking issue");
        }

        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() != BookingStatus.UNDER_REVIEW) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể resolve booking đang UNDER_REVIEW");
        }

        Instant nowUtc = timeProvider.instant();
        booking.setIssueResolvedAtUtc(nowUtc);
        booking.setIssueResolvedAt(BookingTime.fromInstant(nowUtc));
        booking.setIssueResolvedByUserId(adminUserId);
        booking.setIssueResolutionNote(trimToNull(request.adminNote()));

        BookingStatus oldStatus = booking.getStatus();
        if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_SESSION) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_SESSION, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
            sessionFinalizationService.finalizeDeliveredSession(booking, nowUtc);
            if (settlementService != null) {
                settlementService.releaseForBooking(booking);
            }
        } else if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_MENTOR_NO_SHOW, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
            sessionFinalizationService.markSessionNotDelivered(booking);
            if (settlementService != null) {
                settlementService.refundForMentorNoShow(booking);
            }
            recordMentorNoShowViolation(booking);
        } else {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_MENTEE_NO_SHOW, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
            sessionFinalizationService.markSessionNotDelivered(booking);
            if (settlementService != null) {
                settlementService.releaseForBooking(booking);
            }
        }

        Booking savedBooking = bookingRepository.save(booking);

        recordBookingEvent(savedBooking, BookingEventType.ISSUE_RESOLVED,
                oldStatus, BookingEventActorType.ADMIN, adminUserId, null);
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private void recordMentorNoShowViolation(Booking booking) {
        if (mentorViolationService == null || booking == null || booking.getMentorProfile() == null) return;
        mentorViolationService.record(booking.getMentorProfile().getUserId(), booking.getId(),
                MentorViolationType.MENTOR_NO_SHOW, "Admin xác nhận mentor không có mặt trong buổi học.");
    }

    private BookingIssueEvidenceService requireEvidenceService() {
        if (bookingIssueEvidenceService == null) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Dịch vụ minh chứng dispute chưa sẵn sàng");
        }
        return bookingIssueEvidenceService;
    }

    private void publishIssueReportedNotifications(Booking booking, UUID reporterUserId) {
        UUID mentorUserId = booking.getMentorProfile() == null ? null : booking.getMentorProfile().getUserId();
        UUID menteeUserId = booking.getMentee() == null ? null : booking.getMentee().getId();
        UUID recipientUserId = reporterUserId != null && reporterUserId.equals(mentorUserId) ? menteeUserId : mentorUserId;
        if (recipientUserId == null) return;
        eventPublisher.publishEvent(new NotificationEvent(recipientUserId, NotificationType.BOOKING_ISSUE_REPORTED,
                "Có tranh chấp booking cần phản hồi", "Đối tác đã báo vấn đề. Hãy xem minh chứng và phản hồi trong 24 giờ.", "BOOKING", booking.getId()));
        String recipientEmail = reporterUserId != null && reporterUserId.equals(mentorUserId)
                ? booking.getMentee().getEmail() : booking.getMentorProfile().getUser().getEmail();
        String recipientName = reporterUserId != null && reporterUserId.equals(mentorUserId)
                ? booking.getMentee().getFullName() : booking.getMentorProfile().getUser().getFullName();
        String actorName = reporterUserId != null && reporterUserId.equals(mentorUserId)
                ? booking.getMentorProfile().getUser().getFullName() : booking.getMentee().getFullName();
        eventPublisher.publishEvent(BookingEmailNotificationEvent.builder().bookingId(booking.getId())
                .eventType(BookingEmailNotificationEvent.EventType.BOOKING_ISSUE_REPORTED_EMAIL)
                .recipientEmail(recipientEmail).recipientName(recipientName).actorName(actorName)
                .bookingStartTime(booking.getSelectedStartTime()).bookingEndTime(booking.getSelectedEndTime())
                .serviceTitle(booking.getServiceTitleSnapshot()).serviceDurationMinutes(booking.getServiceDurationSnapshot())
                .serviceFree(booking.getServiceIsFreeSnapshot()).servicePriceScoin(booking.getServicePriceScoinSnapshot())
                .learningGoalTitle(booking.getLearningGoalTitle()).learningGoalDescription(booking.getLearningGoalDescription())
                .serviceExpectedOutcome(booking.getServiceExpectedOutcomeSnapshot())
                .reason("Loại vấn đề: " + booking.getIssueType()).createdAt(timeProvider.nowBusiness()).build());
    }

    private Booking getBookingForSessionAction(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }
        return bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
    }

    private void assertBookingAccess(Booking booking, UUID currentUserId) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem hoặc thao tác booking này");
        }
    }

    private boolean isMentorOfBooking(Booking booking, UUID currentUserId) {
        return booking != null && booking.getMentorProfile() != null
                && currentUserId != null && currentUserId.equals(booking.getMentorProfile().getUserId());
    }

    private void requireDirectBooking(Booking booking) {
        if (booking == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking");
        }
    }

    private void synchronizePostSessionStatusForPhaseOne(Booking booking, Instant nowUtc) {
        if (booking == null) {
            return;
        }
        if (booking.getStatus() == BookingStatus.PAID) {
            Instant endTime = BookingTime.resolveSelectedEndUtc(booking);
            if (endTime != null && !nowUtc.isBefore(endTime)) {
                BookingTransitionExecutor.apply(booking, BookingTransitionCommand.SESSION_ENDED, nowUtc);
            }
        }
    }

    private void ensureWithinPostSessionReviewWindow(Booking booking, Instant nowUtc) {
        Instant end = BookingTime.resolveSelectedEndUtc(booking);
        if (end != null && !nowUtc.isBefore(end.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS)))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn xác nhận buổi học (" + PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS + " giờ)");
        }
    }

    private void ensureWithinIssueWindow(Booking booking, Instant nowUtc) {
        Instant end = BookingTime.resolveSelectedEndUtc(booking);
        Instant issueDeadline = end == null ? null : end.plus(Duration.ofHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS));
        if (issueDeadline != null && !nowUtc.isBefore(issueDeadline)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn báo cáo vấn đề (" + PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS + " giờ)");
        }
    }

    private void validateIssueReporter(Booking booking, UUID currentUserId, BookingIssueType issueType) {
        boolean isMentee = booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId());
        boolean isMentor = booking.getMentorProfile() != null && currentUserId.equals(booking.getMentorProfile().getUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền báo cáo vấn đề cho booking này");
        }
        if (issueType == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại vấn đề là bắt buộc");
        }
        if (issueType == BookingIssueType.MENTOR_NO_SHOW && !isMentee) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Chỉ mentee mới có thể báo cáo mentor không tham gia buổi học");
        }
        if (issueType == BookingIssueType.MENTEE_NO_SHOW && !isMentor) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Chỉ mentor mới có thể báo cáo mentee không tham gia buổi học");
        }
    }

    private void recordBookingEvent(Booking booking, BookingEventType type, BookingStatus oldStatus,
                                    BookingEventActorType actorType, UUID actorUserId, String reason) {
        if (bookingEventService != null) {
            bookingEventService.record(booking, type, oldStatus, actorType, actorUserId, reason);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
