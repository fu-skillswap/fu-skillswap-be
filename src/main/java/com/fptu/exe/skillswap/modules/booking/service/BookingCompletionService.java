package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionKind;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateMapper;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionCommand;
import com.fptu.exe.skillswap.modules.booking.domain.BookingTransitionExecutor;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingIssueResolutionRepository;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementCommandPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.port.MentorDisciplineCommandPort;
import com.fptu.exe.skillswap.modules.notification.NotificationEvent;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
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
import java.util.Map;
import java.util.UUID;

@Service
public class BookingCompletionService {
    private final BookingRepository bookingRepository;
    private final SessionFinalizationService sessionFinalizationService;
    private final BookingSettlementCommandPort settlementCommandPort;
    private final BookingEventService bookingEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingResponseMapper bookingResponseMapper;
    private final TimeProvider timeProvider;
    private MentorDisciplineCommandPort mentorDisciplineCommandPort;
    private BookingIssueEvidenceService bookingIssueEvidenceService;
    private BookingDisputeNotificationService bookingDisputeNotificationService;
    private BookingIssueResolutionRepository bookingIssueResolutionRepository;

    @Autowired
    public BookingCompletionService(
            BookingRepository bookingRepository,
            SessionFinalizationService sessionFinalizationService,
            BookingSettlementCommandPort settlementCommandPort,
            BookingEventService bookingEventService,
            ApplicationEventPublisher eventPublisher,
            InternalTelemetryService internalTelemetryService,
            BookingResponseMapper bookingResponseMapper,
            TimeProvider timeProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.sessionFinalizationService = sessionFinalizationService;
        this.settlementCommandPort = settlementCommandPort;
        this.bookingEventService = bookingEventService;
        this.eventPublisher = eventPublisher;
        this.internalTelemetryService = internalTelemetryService;
        this.bookingResponseMapper = bookingResponseMapper;
        this.timeProvider = timeProvider != null ? timeProvider : TimeProvider.from(Clock.systemUTC());
    }

    public BookingCompletionService(
            BookingRepository bookingRepository,
            SessionFinalizationService sessionFinalizationService,
            BookingSettlementCommandPort settlementCommandPort,
            BookingEventService bookingEventService,
            ApplicationEventPublisher eventPublisher,
            InternalTelemetryService internalTelemetryService,
            BookingResponseMapper bookingResponseMapper
    ) {
        this(bookingRepository, sessionFinalizationService, settlementCommandPort, bookingEventService,
                eventPublisher, internalTelemetryService, bookingResponseMapper, null);
    }

    @Autowired(required = false)
    void setMentorDisciplineCommandPort(MentorDisciplineCommandPort mentorDisciplineCommandPort) {
        this.mentorDisciplineCommandPort = mentorDisciplineCommandPort;
    }

    @Autowired
    void setBookingIssueEvidenceService(BookingIssueEvidenceService bookingIssueEvidenceService) {
        this.bookingIssueEvidenceService = bookingIssueEvidenceService;
    }

    @Autowired
    void setBookingDisputeNotificationService(BookingDisputeNotificationService bookingDisputeNotificationService) {
        this.bookingDisputeNotificationService = bookingDisputeNotificationService;
    }

    @Autowired
    void setBookingIssueResolutionRepository(BookingIssueResolutionRepository bookingIssueResolutionRepository) {
        this.bookingIssueResolutionRepository = bookingIssueResolutionRepository;
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
                savedBooking.getMenteeUserId(),
                NotificationType.SESSION_COMPLETED,
                "Mentor đã xác nhận hoàn tất buổi mentoring",
                "Buổi mentoring đã chờ bạn xác nhận hoặc báo vấn đề trong " + PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS + " giờ.",
                "BOOKING",
                savedBooking.getId()
        ));

        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
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
        if (settlementCommandPort != null) {
            settlementCommandPort.requestBookingRelease(savedBooking.getId());
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
                            "mentorUserId", String.valueOf(savedBooking.getMentorUserId()),
                            "completionOutcome", String.valueOf(savedBooking.getCompletionOutcome())
                    )
            );
        }
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
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
            return toIssueResponse(booking);
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
        if (bookingDisputeNotificationService != null) {
            bookingDisputeNotificationService.notifyIssueReported(savedBooking, currentUserId);
        }
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMenteeUserId(),
                savedBooking.getMentorUserId(),
                savedBooking.getStatus(),
                "Buổi học đã được báo cáo vấn đề và đang được xem xét.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : timeProvider.nowBusiness()
        ));
        return toIssueResponse(savedBooking);
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
            return toIssueResponse(booking);
        }
        if (booking.getIssueRespondedAtUtc() != null || booking.getIssueRespondedAt() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Issue này đã có phản hồi từ counterparty");
        }
        if (currentUserId.equals(booking.getIssueSubmittedByUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ counterparty của người báo issue mới được phản hồi");
        }
        Instant nowUtc = timeProvider.instant();
        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        if (!nowUtc.isBefore(BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(submittedUtc))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn phản hồi issue");
        }
        booking.setIssueRespondedAtUtc(nowUtc);
        booking.setIssueRespondedAt(BookingTime.fromInstant(nowUtc));
        booking.setIssueRespondedByUserId(currentUserId);
        booking.setIssueResponseNote(trimToNull(request.responseNote()));
        requireEvidenceService().attachResponderEvidence(booking, currentUserId, request.evidenceIds(), nowUtc);

        booking.setIssueHumanReviewEscalatedAtUtc(nowUtc);
        Booking saved = bookingRepository.save(booking);
        recordBookingEvent(saved, BookingEventType.ISSUE_RESPONDED,
                BookingStatus.UNDER_REVIEW, BookingEventActorType.USER, currentUserId, null);
        recordBookingEvent(saved, BookingEventType.ISSUE_ESCALATED_TO_ADMIN,
                BookingStatus.UNDER_REVIEW, BookingEventActorType.SYSTEM, null, null);
        if (bookingDisputeNotificationService != null) {
            bookingDisputeNotificationService.notifyIssueResponded(saved, currentUserId);
        }
        return toIssueResponse(saved);
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
        AdminBookingIssueResolutionPolicy.validate(request, booking.getIssueType());
        if (requireIssueResolutionRepository().findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
                bookingId, BookingIssueResolutionKind.RESOLUTION, BookingIssueResolutionStatus.APPLIED).isPresent()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Dispute này đã có quyết định settlement đang hiệu lực");
        }

        Instant nowUtc = timeProvider.instant();
        BookingIssueResolution resolution = requireIssueResolutionRepository().saveAndFlush(BookingIssueResolution.builder()
                .bookingId(bookingId)
                .resolvedByUserId(adminUserId)
                .resolutionKind(BookingIssueResolutionKind.RESOLUTION)
                .status(BookingIssueResolutionStatus.APPLIED)
                .action(request.action())
                .reasonCode(request.reasonCode())
                .adminNote(trimToNull(request.adminNote()))
                .menteeBps(request.menteeBps())
                .mentorBps(request.mentorBps())
                .platformBps(request.platformBps())
                .createdAtUtc(nowUtc)
                .build());
        booking.setIssueResolvedAtUtc(nowUtc);
        booking.setIssueResolvedAt(BookingTime.fromInstant(nowUtc));
        booking.setIssueResolvedByUserId(adminUserId);
        booking.setIssueResolutionNote(trimToNull(request.adminNote()));

        BookingStatus oldStatus = booking.getStatus();
        if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_SESSION
                || request.action() == AdminBookingIssueResolutionAction.RELEASE_AS_IS) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_SESSION, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
            sessionFinalizationService.finalizeDeliveredSession(booking, nowUtc);
        } else if (request.action() == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_SESSION, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.PARTIALLY_SETTLED);
            sessionFinalizationService.finalizeDisputedSessionWithoutCompletionCounter(booking, nowUtc);
        } else if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_MENTOR_NO_SHOW, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
            sessionFinalizationService.markSessionNotDelivered(booking);
            recordMentorNoShowViolation(booking);
        } else if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_MENTEE_NO_SHOW_RELEASE) {
            BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_CONFIRM_MENTEE_NO_SHOW, nowUtc);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
            sessionFinalizationService.markSessionNotDelivered(booking);
        } else {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Action resolve dispute không hợp lệ");
        }
        if (settlementCommandPort != null) {
            settlementCommandPort.requestAdminIssueResolution(booking.getId(), resolution.getId());
        }

        Booking savedBooking = bookingRepository.save(booking);
        requireIssueResolutionRepository().save(resolution);

        recordBookingEvent(savedBooking, BookingEventType.ISSUE_RESOLVED,
                oldStatus, BookingEventActorType.ADMIN, adminUserId, null);
        if (bookingDisputeNotificationService != null) {
            bookingDisputeNotificationService.notifyIssueResolved(savedBooking, false, resolution);
        }
        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    @Transactional
    public BookingResponse reverseBookingIssueResolution(UUID adminUserId, UUID bookingId, AdminReverseResolutionRequest request) {
        if (adminUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực admin");
        }
        if (bookingId == null || request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu thông tin reversal bắt buộc");
        }
        if (request.reasonCode() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "reasonCode là bắt buộc cho reversal");
        }
        if (request.adminNote() == null || request.adminNote().isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "adminNote là bắt buộc cho reversal");
        }

        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể đảo ngược quyết định của booking đã COMPLETED");
        }

        BookingIssueResolution originalResolution = requireIssueResolutionRepository()
                .findFirstByBookingIdAndResolutionKindAndStatusOrderByCreatedAtUtcDesc(
                        bookingId, BookingIssueResolutionKind.RESOLUTION, BookingIssueResolutionStatus.APPLIED)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy quyết định dispute đang hiệu lực để đảo ngược"));

        if (requireIssueResolutionRepository().existsByReversalOfResolutionIdAndResolutionKind(
                originalResolution.getId(), BookingIssueResolutionKind.REVERSAL)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Quyết định dispute này đã được đảo ngược trước đó");
        }

        Instant nowUtc = timeProvider.instant();

        originalResolution.setStatus(BookingIssueResolutionStatus.REVERSED);
        requireIssueResolutionRepository().save(originalResolution);

        BookingIssueResolution reversalRecord = requireIssueResolutionRepository().saveAndFlush(BookingIssueResolution.builder()
                .bookingId(bookingId)
                .resolvedByUserId(adminUserId)
                .resolutionKind(BookingIssueResolutionKind.REVERSAL)
                .status(BookingIssueResolutionStatus.APPLIED)
                .action(originalResolution.getAction())
                .reasonCode(request.reasonCode())
                .adminNote(trimToNull(request.adminNote()))
                .menteeBps(originalResolution.getMenteeBps())
                .mentorBps(originalResolution.getMentorBps())
                .platformBps(originalResolution.getPlatformBps())
                .reversalOfResolutionId(originalResolution.getId())
                .createdAtUtc(nowUtc)
                .build());

        if (settlementCommandPort != null) {
            settlementCommandPort.requestResolutionReversal(booking.getId(), originalResolution.getId(), reversalRecord.getId());
        }
        requireIssueResolutionRepository().save(reversalRecord);

        BookingStatus oldStatus = booking.getStatus();
        BookingTransitionExecutor.apply(booking, BookingTransitionCommand.ADMIN_REVERSE_RESOLUTION, nowUtc);
        booking.setIssueResolvedAtUtc(null);
        booking.setIssueResolvedAt(null);
        booking.setIssueResolvedByUserId(null);
        booking.setIssueResolutionNote(null);
        booking.setCompletionOutcome(BookingCompletionOutcome.UNDER_REVIEW);

        Booking savedBooking = bookingRepository.save(booking);

        recordBookingEvent(savedBooking, BookingEventType.ISSUE_RESOLUTION_REVERSED,
                oldStatus, BookingEventActorType.ADMIN, adminUserId, trimToNull(request.adminNote()));

        if (bookingDisputeNotificationService != null) {
            bookingDisputeNotificationService.notifyIssueResolutionReversed(savedBooking, reversalRecord);
        }

        return bookingResponseMapper.toBookingResponse(savedBooking);
    }

    private void recordMentorNoShowViolation(Booking booking) {
        if (mentorDisciplineCommandPort == null || booking == null || booking.getMentorUserId() == null) return;
        mentorDisciplineCommandPort.recordMentorNoShow(booking.getMentorUserId(), booking.getId(),
                "Admin xác nhận mentor không có mặt trong buổi học.");
    }

    private BookingIssueResponse toIssueResponse(Booking booking) {
        Instant submittedUtc = booking.getIssueSubmittedAtUtc() != null
                ? booking.getIssueSubmittedAtUtc() : BookingTime.toInstant(booking.getIssueSubmittedAt());
        Instant respondedUtc = booking.getIssueRespondedAtUtc() != null
                ? booking.getIssueRespondedAtUtc() : BookingTime.toInstant(booking.getIssueRespondedAt());
        Instant escalatedUtc = booking.getIssueHumanReviewEscalatedAtUtc();
        Instant overdueUtc = booking.getAdminSlaOverdueAtUtc();
        Instant resolvedUtc = booking.getIssueResolvedAtUtc() != null
                ? booking.getIssueResolvedAtUtc() : BookingTime.toInstant(booking.getIssueResolvedAt());

        return BookingIssueResponse.builder()
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .issueSubmittedAt(BookingTime.toOffsetDateTime(submittedUtc))
                .issueType(booking.getIssueType())
                .issueRespondedAt(BookingTime.toOffsetDateTime(respondedUtc))
                .issueResponseDeadlineAt(BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveIssueResponseDeadlineUtc(submittedUtc)))
                .issueAdminEscalatedAt(BookingTime.toOffsetDateTime(escalatedUtc))
                .issueAdminResolutionDeadlineAt(BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveAdminDisputeSlaDeadlineUtc(escalatedUtc)))
                .issueAdminSlaOverdueAt(BookingTime.toOffsetDateTime(overdueUtc))
                .issueAdminSlaReminderCount(booking.getAdminSlaReminderCount())
                .issueAutoReleaseAt(BookingTime.toOffsetDateTime(BookingDeadlinePolicy.resolveAdminDisputeAutoReleaseDeadlineUtc(overdueUtc)))
                .disputeSlaStatus(BookingDeadlinePolicy.resolveDisputeSlaStatus(submittedUtc, escalatedUtc, overdueUtc, resolvedUtc))
                .build();
    }

    private BookingIssueEvidenceService requireEvidenceService() {
        if (bookingIssueEvidenceService == null) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Dịch vụ minh chứng dispute chưa sẵn sàng");
        }
        return bookingIssueEvidenceService;
    }

    private BookingIssueResolutionRepository requireIssueResolutionRepository() {
        if (bookingIssueResolutionRepository == null) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Dịch vụ audit settlement dispute chưa sẵn sàng");
        }
        return bookingIssueResolutionRepository;
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
        boolean isMentee = booking.getMenteeUserId() != null && currentUserId.equals(booking.getMenteeUserId());
        boolean isMentor = booking.getMentorUserId() != null && currentUserId.equals(booking.getMentorUserId());
        if (!isMentee && !isMentor) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem hoặc thao tác booking này");
        }
    }

    private boolean isMentorOfBooking(Booking booking, UUID currentUserId) {
        return booking != null && booking.getMentorUserId() != null
                && currentUserId != null && currentUserId.equals(booking.getMentorUserId());
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
        boolean isMentee = booking.getMenteeUserId() != null && currentUserId.equals(booking.getMenteeUserId());
        boolean isMentor = booking.getMentorUserId() != null && currentUserId.equals(booking.getMentorUserId());
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
