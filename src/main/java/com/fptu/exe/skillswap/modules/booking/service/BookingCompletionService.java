package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventActorType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingEventType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ConfirmBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SubmitBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.event.BookingStatusUpdatedEvent;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorViolationService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.event.NotificationEvent;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.isConfirmedBookingStatus;
import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.selectedEndTime;
import static com.fptu.exe.skillswap.modules.booking.service.BookingResponseMapper.selectedStartTime;

@Service
@RequiredArgsConstructor
public class BookingCompletionService {

    private final BookingRepository bookingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final EntityManager entityManager;
    private final SessionService sessionService;
    private final SettlementService settlementService;
    private final BookingEventService bookingEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalTelemetryService internalTelemetryService;
    private final BookingResponseMapper bookingResponseMapper;
    private MentorViolationService mentorViolationService;

    @Autowired(required = false)
    void setMentorViolationService(MentorViolationService mentorViolationService) {
        this.mentorViolationService = mentorViolationService;
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

        LocalDateTime now = DateTimeUtil.now();
        Booking booking = getBookingForSessionAction(mentorUserId, bookingId);
        requireDirectBooking(booking);
        if (!isMentorOfBooking(booking, mentorUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ mentor của booking mới được xác nhận hoàn tất buổi mentoring");
        }
        synchronizePostSessionStatusForPhaseOne(booking, now);
        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ mentor hoàn tất");
        }
        if (selectedEndTime(booking) == null || now.isBefore(selectedEndTime(booking))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chưa thể hoàn tất booking trước khi buổi mentoring kết thúc");
        }
        if (!now.isBefore(selectedEndTime(booking).plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Đã quá thời hạn mentor xác nhận; booking sẽ được hệ thống tự động hoàn tất");
        }

        String completionNote = trimToNull(request == null ? null : request.completionNote());
        booking.setMentorNote(completionNote);
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        booking.setCompletedAt(now);

        if (booking.getActualStartTime() == null) {
            booking.setActualStartTime(selectedStartTime(booking));
        }
        if (booking.getActualEndTime() == null) {
            booking.setActualEndTime(selectedEndTime(booking));
        }

        if (sessionService != null) {
            Session session = sessionService.findByBookingId(bookingId);
            if (session != null) {
                session.setStatus(SessionStatus.COMPLETED);
                if (session.getActualStartTime() == null) {
                    session.setActualStartTime(selectedStartTime(booking));
                }
                if (session.getActualEndTime() == null) {
                    session.setActualEndTime(selectedEndTime(booking));
                }
            }
        }

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            if (entityManager != null) {
                entityManager.refresh(lockedProfile);
            }
            touchMentorActivity(lockedProfile, now);
            mentorProfileRepository.save(lockedProfile);
        }

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
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
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

        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        LocalDateTime now = DateTimeUtil.now();
        synchronizePostSessionStatusForPhaseOne(booking, now);
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION
                && booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái chờ xác nhận sau buổi học");
        }
        if (isMentorOfBooking(booking, currentUserId)) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Mentor không thể tự xác nhận thay cho participant còn lại");
        }
        ensureWithinPostSessionReviewWindow(booking, now);

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setFinalizedAt(now);
        booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
        booking.setMenteeNote(trimToNull(request == null ? null : request.confirmationNote()));

        MentorProfile mentorProfile = booking.getMentorProfile();
        if (mentorProfile != null) {
            MentorProfile lockedProfile = mentorProfileRepository.findByIdForUpdate(mentorProfile.getUserId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
            if (entityManager != null) {
                entityManager.refresh(lockedProfile);
            }
            lockedProfile.setTotalCompletedSessions(defaultInteger(lockedProfile.getTotalCompletedSessions()) + 1);
            lockedProfile.setTotalSessions(defaultInteger(lockedProfile.getTotalSessions()) + 1);
            touchMentorActivity(lockedProfile, now);
            mentorProfileRepository.save(lockedProfile);
        }

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
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
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

        Booking booking = getBookingForSessionAction(currentUserId, bookingId);
        LocalDateTime now = DateTimeUtil.now();
        synchronizePostSessionStatusForPhaseOne(booking, now);
        assertBookingAccess(booking, currentUserId);

        if (booking.getStatus() != BookingStatus.AWAITING_MENTOR_COMPLETION
                && booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện chưa ở trạng thái cho phép báo vấn đề");
        }
        if (selectedEndTime(booking) == null || now.isBefore(selectedEndTime(booking))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Chỉ có thể báo vấn đề sau khi buổi mentoring đã kết thúc");
        }
        ensureWithinIssueWindow(booking, now);
        validateIssueReporter(booking, currentUserId, request.issueType());

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.UNDER_REVIEW);
        booking.setIssueSubmittedAt(now);
        booking.setIssueSubmittedByUserId(currentUserId);
        booking.setIssueType(request.issueType());
        booking.setIssueDescription(trim(request.description()));
        booking.setCompletionOutcome(BookingCompletionOutcome.UNDER_REVIEW);

        Booking savedBooking = bookingRepository.save(booking);
        recordBookingEvent(savedBooking, BookingEventType.ISSUE_CREATED,
                oldStatus, BookingEventActorType.USER, currentUserId, null);
        eventPublisher.publishEvent(new BookingStatusUpdatedEvent(
                savedBooking.getId(),
                savedBooking.getMentee().getId(),
                savedBooking.getMentorProfile().getUserId(),
                savedBooking.getStatus(),
                "Buổi học đã được báo cáo vấn đề và đang được xem xét.",
                savedBooking.getUpdatedAt() != null ? savedBooking.getUpdatedAt() : DateTimeUtil.now()
        ));
        return BookingIssueResponse.builder()
                .bookingId(savedBooking.getId())
                .status(savedBooking.getStatus())
                .issueSubmittedAt(savedBooking.getIssueSubmittedAt())
                .issueType(savedBooking.getIssueType())
                .issueRespondedAt(savedBooking.getIssueRespondedAt())
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
        if (booking.getStatus() != BookingStatus.UNDER_REVIEW || booking.getIssueSubmittedAt() == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking hiện không có issue đang mở");
        }
        if (booking.getIssueRespondedAt() != null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Issue này đã có phản hồi từ counterparty");
        }
        if (currentUserId.equals(booking.getIssueSubmittedByUserId())) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ counterparty của người báo issue mới được phản hồi");
        }
        LocalDateTime now = DateTimeUtil.now();
        if (now.isAfter(booking.getIssueSubmittedAt().plusHours(24))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn phản hồi issue");
        }
        booking.setIssueRespondedAt(now);
        booking.setIssueRespondedByUserId(currentUserId);
        booking.setIssueResponseNote(trimToNull(request.responseNote()));
        Booking saved = bookingRepository.save(booking);
        recordBookingEvent(saved, BookingEventType.ISSUE_RESPONDED,
                BookingStatus.UNDER_REVIEW, BookingEventActorType.USER, currentUserId, null);
        return BookingIssueResponse.builder().bookingId(saved.getId()).status(saved.getStatus())
                .issueSubmittedAt(saved.getIssueSubmittedAt()).issueType(saved.getIssueType())
                .issueRespondedAt(saved.getIssueRespondedAt()).build();
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

        LocalDateTime now = DateTimeUtil.now();
        booking.setIssueResolvedAt(now);
        booking.setIssueResolvedByUserId(adminUserId);
        booking.setIssueResolutionNote(trimToNull(request.adminNote()));

        BookingStatus oldStatus = booking.getStatus();
        if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_SESSION) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setCompletedAt(booking.getCompletedAt() == null ? now : booking.getCompletedAt());
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.USER_CONFIRMED);
            if (settlementService != null) {
                settlementService.releaseForBooking(booking);
            }
        } else if (request.action() == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND) {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTOR);
            if (settlementService != null) {
                settlementService.refundForMentorNoShow(booking);
            }
            recordMentorNoShowViolation(booking);
        } else {
            booking.setStatus(BookingStatus.COMPLETED);
            booking.setFinalizedAt(now);
            booking.setCompletionOutcome(BookingCompletionOutcome.NO_SHOW_MENTEE);
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

    private void synchronizePostSessionStatusForPhaseOne(Booking booking, LocalDateTime now) {
        if (booking == null) {
            return;
        }
        if (booking.getStatus() == BookingStatus.PAID) {
            LocalDateTime endTime = selectedEndTime(booking);
            if (endTime != null && !now.isBefore(endTime)) {
                booking.setStatus(BookingStatus.AWAITING_MENTOR_COMPLETION);
            }
        }
    }

    private void ensureWithinPostSessionReviewWindow(Booking booking, LocalDateTime now) {
        LocalDateTime end = selectedEndTime(booking);
        if (end != null && !now.isBefore(end.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Đã quá thời hạn xác nhận buổi học (" + PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS + " giờ)");
        }
    }

    private void ensureWithinIssueWindow(Booking booking, LocalDateTime now) {
        LocalDateTime end = selectedEndTime(booking);
        LocalDateTime issueDeadline = end == null ? null : end.plusHours(PostSessionPolicy.MENTEE_REVIEW_WINDOW_HOURS);
        if (issueDeadline != null && !now.isBefore(issueDeadline)) {
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

    private void touchMentorActivity(MentorProfile profile, LocalDateTime activityAt) {
        if (profile == null) {
            return;
        }
        profile.setLastActiveAt(activityAt != null ? activityAt : DateTimeUtil.now());
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
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
