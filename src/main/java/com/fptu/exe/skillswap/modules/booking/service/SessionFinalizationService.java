package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Clock;

/**
 * Keeps the delivery record ({@link Session}) and mentor completion counters aligned with a
 * booking's final outcome. Financial settlement remains owned by the booking/payment services.
 * Callers must already hold the booking lock before entering this service.
 */
@Service
@RequiredArgsConstructor
public class SessionFinalizationService {

    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final MentorProfileRepository mentorProfileRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    /** Records the mentor's declaration without finalizing the booking or incrementing counters. */
    @Transactional
    public void recordMentorReportedCompletion(Booking booking, Instant reportedAtUtc) {
        Session session = findOrCreateForUpdate(booking);
        markCompleted(session, booking);
        touchMentorActivity(booking, reportedAtUtc);
    }

    @Transactional
    public void recordMentorReportedCompletion(Booking booking, LocalDateTime reportedAt) {
        recordMentorReportedCompletion(booking, reportedAt != null ? BookingTime.toInstant(reportedAt) : timeProvider.instant());
    }

    /**
     * Records a delivered session exactly once. Auto-close is intentionally considered delivered
     * by product policy when no issue was raised before the review deadline.
     */
    @Transactional
    public void finalizeDeliveredSession(Booking booking, Instant finalizedAtUtc) {
        if (booking == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Booking không hợp lệ");
        }

        Session session = findOrCreateForUpdate(booking);
        markCompleted(session, booking);

        // finalizedAt is the durable idempotency boundary for mentor counters. A repeated API
        // request or scheduler run may repair the Session but must never increment again.
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

    /** A confirmed no-show means the session did not take place and must not count for the mentor. */
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

    private void markCompleted(Session session, Booking booking) {
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Session đã bị hủy nên không thể xác nhận hoàn tất");
        }
        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);
    }

    private void incrementMentorCompletionCounters(Booking booking, Instant finalizedAtUtc) {
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            return;
        }
        MentorProfile mentor = mentorProfileRepository.findByIdForUpdate(booking.getMentorProfile().getUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        mentor.setTotalCompletedSessions(defaultInteger(mentor.getTotalCompletedSessions()) + 1);
        mentor.setTotalSessions(defaultInteger(mentor.getTotalSessions()) + 1);
        mentor.setLastActiveAt(BookingTime.fromInstant(finalizedAtUtc));
        mentorProfileRepository.save(mentor);
    }

    private void touchMentorActivity(Booking booking, Instant activityAtUtc) {
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            return;
        }
        MentorProfile mentor = mentorProfileRepository.findByIdForUpdate(booking.getMentorProfile().getUserId())
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
        mentor.setLastActiveAt(BookingTime.fromInstant(activityAtUtc));
        mentorProfileRepository.save(mentor);
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
