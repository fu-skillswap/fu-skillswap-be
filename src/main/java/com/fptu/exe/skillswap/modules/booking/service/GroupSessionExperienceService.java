package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.GroupSessionProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupAttendanceStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionAttendanceRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionMeetingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionExperienceResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationParticipantAccess;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.modules.payment.service.SettlementService;
import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.modules.session.service.SessionService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.VersionConflictException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Owns the shared group-session experience; commerce and direct-booking flows remain unchanged. */
@Service
@RequiredArgsConstructor
public class GroupSessionExperienceService {
    private static final List<BookingStatus> ATTENDANCE_SEAT_STATUSES = List.of(
            BookingStatus.PAID, BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingStatus.UNDER_REVIEW);

    private final GroupSessionRepository groupSessionRepository;
    private final BookingRepository bookingRepository;
    private final SessionService sessionService;
    private final ConversationService conversationService;
    private final SettlementService settlementService;
    private final GroupSessionProperties properties;

    @Transactional
    public void createSharedExperience(GroupSession groupSession) {
        sessionService.createForGroupSession(groupSession);
        conversationService.createGroupForPublishedSession(groupSession);
    }

    /** Called by free-seat creation and payment finalization in their existing transactions. */
    @Transactional
    public void activateConfirmedSeat(Booking booking) {
        if (booking != null && booking.getGroupSession() != null && ATTENDANCE_SEAT_STATUSES.contains(booking.getStatus())) {
            createSharedExperience(booking.getGroupSession());
            conversationService.activateGroupAttendee(booking);
        }
    }

    /** Bounded runtime backfill for sessions published before the Phase 3 rollout. */
    @Transactional
    public void backfillSharedExperience(GroupSession groupSession) {
        createSharedExperience(groupSession);
        bookingRepository.findGroupSeatBookingsForUpdate(groupSession.getId(), ATTENDANCE_SEAT_STATUSES)
                .forEach(this::activateConfirmedSeat);
    }

    @Transactional
    public void revokeSeat(Booking booking, boolean disputeRefund) {
        if (booking == null || booking.getGroupSession() == null) return;
        conversationService.updateGroupParticipantAccess(booking.getGroupSession().getId(), booking.getMentee().getId(),
                disputeRefund ? ConversationParticipantAccess.READ_ONLY : ConversationParticipantAccess.REVOKED);
    }

    @Transactional
    public void revokeAllForCancelledSession(GroupSession groupSession) {
        bookingRepository.findByGroupSessionIdOrderByCreatedAtAsc(groupSession.getId()).forEach(booking ->
                conversationService.updateGroupParticipantAccess(groupSession.getId(), booking.getMentee().getId(), ConversationParticipantAccess.REVOKED));
    }

    @Transactional(readOnly = true)
    public GroupSessionExperienceResponse getExperience(UUID mentorUserId, UUID groupSessionId) {
        GroupSession groupSession = groupSessionRepository.findByIdAndMentorProfileUserId(groupSessionId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        return response(groupSession);
    }

    @Transactional
    public GroupSessionExperienceResponse updateMeeting(UUID mentorUserId, UUID groupSessionId, GroupSessionMeetingRequest request) {
        GroupSession groupSession = groupSessionRepository.findOwnedByIdForUpdate(groupSessionId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        Session session = sessionService.createForGroupSession(groupSession);
        session.setMeetingPlatform(request.meetingPlatform());
        session.setMeetingLink(trimToNull(request.meetingLink()));
        sessionService.save(session);
        return response(groupSession);
    }

    @Transactional
    public GroupSessionExperienceResponse submitAttendance(UUID mentorUserId, UUID groupSessionId, GroupSessionAttendanceRequest request) {
        GroupSession groupSession = groupSessionRepository.findOwnedByIdForUpdate(groupSessionId, mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        if (!java.util.Objects.equals(groupSession.getVersion(), request.expectedVersion())) {
            throw new VersionConflictException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_VERSION_CONFLICT", groupSessionId,
                    request.expectedVersion(), groupSession.getVersion());
        }
        LocalDateTime now = DateTimeUtil.now();
        if (now.isBefore(groupSession.getScheduledEndAt()) || now.isAfter(attendanceDeadline(groupSession))) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_ATTENDANCE_WINDOW_CLOSED");
        }
        List<Booking> seats = bookingRepository.findGroupSeatBookingsForUpdate(groupSessionId, ATTENDANCE_SEAT_STATUSES);
        Map<UUID, GroupAttendanceStatus> submitted = request.attendees().stream()
                .collect(Collectors.toMap(GroupSessionAttendanceRequest.Attendee::bookingId,
                        GroupSessionAttendanceRequest.Attendee::status, (left, right) -> { throw new BaseException(ErrorCode.BAD_REQUEST, "GROUP_SESSION_ATTENDANCE_DUPLICATE_BOOKING"); }));
        if (submitted.size() != seats.size() || seats.stream().anyMatch(seat -> !submitted.containsKey(seat.getId()) || seat.getGroupAttendanceStatus() != null)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "GROUP_SESSION_ATTENDANCE_ROSTER_INVALID");
        }
        for (Booking seat : seats) {
            GroupAttendanceStatus status = submitted.get(seat.getId());
            seat.setGroupAttendanceStatus(status);
            seat.setGroupAttendanceMarkedAt(now);
            seat.setGroupAttendanceMarkedByUserId(mentorUserId);
            seat.setCompletedAt(now);
            if (status == GroupAttendanceStatus.PRESENT) {
                seat.setStatus(BookingStatus.AWAITING_MENTEE_CONFIRMATION);
            } else {
                seat.setStatus(BookingStatus.UNDER_REVIEW);
                seat.setCompletionOutcome(BookingCompletionOutcome.UNDER_REVIEW);
                seat.setIssueType(BookingIssueType.MENTEE_NO_SHOW);
                seat.setIssueSubmittedAt(now);
                seat.setIssueSubmittedByUserId(mentorUserId);
                seat.setIssueDescription("Mentor reported attendee no-show through group attendance.");
            }
        }
        bookingRepository.saveAll(seats);
        return response(groupSession);
    }

    @Transactional
    public boolean autoCloseIfDue(UUID bookingId, LocalDateTime now) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId).orElse(null);
        if (booking == null || booking.getGroupSession() == null || booking.getStatus() != BookingStatus.AWAITING_MENTEE_CONFIRMATION
                || booking.getCompletedAt() == null || now.isBefore(booking.getCompletedAt().plusHours(24))) return false;
        booking.setStatus(BookingStatus.AUTO_CLOSED);
        booking.setAutoClosedAt(now);
        booking.setFinalizedAt(now);
        booking.setCompletionOutcome(BookingCompletionOutcome.AUTO_CLOSED);
        settlementService.releaseForBooking(booking);
        return true;
    }

    @Transactional
    public void makeCompletedSessionReadOnly(UUID groupSessionId, LocalDateTime now) {
        GroupSession session = groupSessionRepository.findById(groupSessionId).orElse(null);
        if (session == null || now.isBefore(session.getScheduledEndAt().plusHours(24))) return;
        bookingRepository.findByGroupSessionIdOrderByCreatedAtAsc(groupSessionId).forEach(booking ->
                conversationService.updateGroupParticipantAccess(groupSessionId, booking.getMentee().getId(), ConversationParticipantAccess.READ_ONLY));
        conversationService.updateGroupParticipantAccess(groupSessionId, session.getMentorProfile().getUserId(), ConversationParticipantAccess.READ_ONLY);
    }

    private GroupSessionExperienceResponse response(GroupSession groupSession) {
        Session session = sessionService.findByGroupSessionId(groupSession.getId());
        var conversation = conversationService.findGroupConversation(groupSession.getId());
        return new GroupSessionExperienceResponse(groupSession.getId(), session == null ? null : session.getId(),
                session == null ? null : session.getStatus(), conversation == null ? null : conversation.getId(),
                session == null ? null : session.getMeetingPlatform(), session == null ? null : session.getMeetingLink(), null,
                attendanceDeadline(groupSession));
    }

    private LocalDateTime attendanceDeadline(GroupSession session) {
        return session.getScheduledEndAt().plusHours(Math.max(1, Math.min(168, properties.getAttendanceSubmissionWindowHours())));
    }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
