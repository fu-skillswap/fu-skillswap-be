package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingActivityCommandPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Keeps the delivery record ({@link Session}) and mentor completion counters aligned with a
 * booking's final outcome. Financial settlement remains owned by the booking/payment services.
 * Callers must already hold the booking lock before entering this service.
 */
@Service
public class SessionFinalizationService {

    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final MentorBookingActivityCommandPort mentorBookingActivityCommandPort;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired
    public SessionFinalizationService(
            SessionRepository sessionRepository,
            SessionService sessionService,
            @Autowired(required = false) MentorBookingActivityCommandPort mentorBookingActivityCommandPort
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.mentorBookingActivityCommandPort = mentorBookingActivityCommandPort;
    }

    public SessionFinalizationService(SessionRepository sessionRepository, SessionService sessionService) {
        this(sessionRepository, sessionService, null);
    }

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public void recordMentorReportedCompletion(Booking booking, Instant reportedAtUtc) {
        findOrCreateForUpdate(booking);
        touchMentorActivity(booking, reportedAtUtc);
    }

    @Transactional
    public void recordMentorReportedCompletion(Booking booking, LocalDateTime reportedAt) {
        recordMentorReportedCompletion(booking, reportedAt != null ? BookingTime.toInstant(reportedAt) : timeProvider.instant());
    }

    @Transactional
    public void finalizeDeliveredSession(Booking booking, Instant finalizedAtUtc) {
        if (booking == null || finalizedAtUtc == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm hoàn tất là bắt buộc");
        }

        Session session = findOrCreateForUpdate(booking);
        completeDeliveredSession(session, booking, finalizedAtUtc);

        if (booking.getFinalizedAtUtc() == null && booking.getFinalizedAt() == null) {
            incrementMentorCompletionCounters(booking, finalizedAtUtc);
            booking.setFinalizedAtUtc(finalizedAtUtc);
            booking.setFinalizedAt(BookingTime.fromInstant(finalizedAtUtc));
        }
        if (booking.getCompletedAtUtc() == null && booking.getCompletedAt() == null) {
            booking.setCompletedAtUtc(finalizedAtUtc);
            booking.setCompletedAt(BookingTime.fromInstant(finalizedAtUtc));
        }
    }

    @Transactional
    public void finalizeDeliveredSession(Booking booking, LocalDateTime finalizedAt) {
        finalizeDeliveredSession(booking, finalizedAt != null ? BookingTime.toInstant(finalizedAt) : timeProvider.instant());
    }

    @Transactional
    public void finalizeDisputedSessionWithoutCompletionCounter(Booking booking, Instant finalizedAtUtc) {
        if (booking == null || finalizedAtUtc == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking và thời điểm hoàn tất là bắt buộc");
        }
        Session session = findOrCreateForUpdate(booking);
        completeDeliveredSession(session, booking, finalizedAtUtc);
        if (booking.getCompletedAtUtc() == null && booking.getCompletedAt() == null) {
            booking.setCompletedAtUtc(finalizedAtUtc);
            booking.setCompletedAt(BookingTime.fromInstant(finalizedAtUtc));
        }
    }

    @Transactional
    public void markSessionNotDelivered(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId())
                .ifPresent(session -> {
                    session.setStatus(SessionStatus.CANCELLED);
                    session.setActualStartTime(null);
                    session.setActualStartTimeUtc(null);
                    session.setActualEndTime(null);
                    session.setActualEndTimeUtc(null);
                    sessionRepository.save(session);
                });
        booking.setActualStartTime(null);
        booking.setActualEndTime(null);
        booking.setCompletedAt(null);
        booking.setCompletedAtUtc(null);
    }

    private Session findOrCreateForUpdate(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không hợp lệ");
        }
        return sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId())
                .orElseGet(() -> {
                    sessionService.createForAcceptedBooking(booking);
                    return sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, booking.getId())
                            .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                                    "Không thể khởi tạo session cho booking"));
                });
    }

    private void completeDeliveredSession(Session session, Booking booking, Instant finalizedAtUtc) {
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Session đã bị hủy nên không thể xác nhận hoàn tất");
        }
        session.setStatus(SessionStatus.COMPLETED);
        setConfirmedEndTimeWhenMissing(session, booking, finalizedAtUtc);
        mirrorActualTimesToLegacyBooking(booking, session);
        sessionRepository.save(session);
    }

    private void setConfirmedEndTimeWhenMissing(Session session, Booking booking, Instant finalizedAtUtc) {
        if (session.getActualEndTimeUtc() != null || session.getActualEndTime() != null) {
            return;
        }
        Instant scheduledEndUtc = session.getScheduledEndTimeUtc() != null
                ? session.getScheduledEndTimeUtc()
                : BookingTime.toInstant(session.getScheduledEndTime());
        if (scheduledEndUtc == null) {
            scheduledEndUtc = BookingTime.resolveSelectedEndUtc(booking);
        }
        if (scheduledEndUtc == null || scheduledEndUtc.isAfter(finalizedAtUtc)) {
            return;
        }
        session.setActualEndTimeUtc(scheduledEndUtc);
        session.setActualEndTime(BookingTime.fromInstant(scheduledEndUtc));
    }

    private void mirrorActualTimesToLegacyBooking(Booking booking, Session session) {
        if (booking.getActualStartTime() == null) {
            Instant actualStartUtc = session.getActualStartTimeUtc() != null
                    ? session.getActualStartTimeUtc()
                    : BookingTime.toInstant(session.getActualStartTime());
            if (actualStartUtc != null) {
                booking.setActualStartTime(BookingTime.fromInstant(actualStartUtc));
            }
        }
        if (booking.getActualEndTime() == null) {
            Instant actualEndUtc = session.getActualEndTimeUtc() != null
                    ? session.getActualEndTimeUtc()
                    : BookingTime.toInstant(session.getActualEndTime());
            if (actualEndUtc != null) {
                booking.setActualEndTime(BookingTime.fromInstant(actualEndUtc));
            }
        }
    }

    private void incrementMentorCompletionCounters(Booking booking, Instant finalizedAtUtc) {
        if (booking.getMentorUserId() == null || mentorBookingActivityCommandPort == null) {
            return;
        }
        mentorBookingActivityCommandPort.recordCompletedSession(booking.getMentorUserId(), finalizedAtUtc);
    }

    private void touchMentorActivity(Booking booking, Instant activityAtUtc) {
        if (booking.getMentorUserId() == null || mentorBookingActivityCommandPort == null) {
            return;
        }
        mentorBookingActivityCommandPort.recordMentorActivity(booking.getMentorUserId(), activityAtUtc);
    }
}
