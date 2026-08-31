package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendanceSummary;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionAttendanceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns one-to-one session check-in. The booking row is locked first, then the session row,
 * so concurrent mentor/mentee requests see one ordered state transition.
 */
@Service
@RequiredArgsConstructor
public class SessionAttendanceService {

    private final BookingRepository bookingRepository;
    private final SessionRepository sessionRepository;
    private final SessionAttendanceRepository sessionAttendanceRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    /**
     * Creates the participant's immutable attendance record or safely replays their first check-in.
     * It never changes booking/payment/settlement status.
     */
    @Transactional
    public Booking checkIn(UUID currentUserId, UUID bookingId) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        if (bookingId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã booking không hợp lệ");
        }

        // Lock order for this command: Booking -> Session -> Attendance rows.
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking"));
        SessionParticipantRole participantRole = resolveParticipantRole(booking, currentUserId);
        Session session = sessionRepository.findBySourceTypeAndSourceIdForUpdate(SessionSourceType.BOOKING, bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT,
                        "Booking chưa có session để check-in"));

        // A retry must remain successful even if the session has since been finalized.
        if (sessionAttendanceRepository.findBySessionIdAndParticipantRole(session.getId(), participantRole).isPresent()) {
            return booking;
        }

        Instant nowUtc = timeProvider.instant();
        Instant startUtc = BookingTime.resolveSelectedStartUtc(booking);
        Instant endUtc = BookingTime.resolveSelectedEndUtc(booking);
        if (!SessionAttendancePolicy.canCheckIn(booking.getStatus(), session.getStatus(), nowUtc, startUtc, endUtc)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, checkInUnavailableMessage(session, nowUtc, startUtc, endUtc));
        }

        SessionAttendance attendance = SessionAttendance.builder()
                .session(session)
                .participantRole(participantRole)
                .participantUserId(currentUserId)
                .checkedInAtUtc(nowUtc)
                .build();
        sessionAttendanceRepository.save(attendance);

        if (session.getStatus() == SessionStatus.SCHEDULED) {
            session.setStatus(SessionStatus.IN_PROGRESS);
        }

        List<SessionAttendance> attendances = sessionAttendanceRepository.findBySessionId(session.getId());
        boolean mentorCheckedIn = attendances.stream()
                .anyMatch(item -> item.getParticipantRole() == SessionParticipantRole.MENTOR);
        boolean menteeCheckedIn = attendances.stream()
                .anyMatch(item -> item.getParticipantRole() == SessionParticipantRole.MENTEE);
        if (SessionAttendanceSummary.from(mentorCheckedIn, menteeCheckedIn) == SessionAttendanceSummary.BOTH
                && session.getActualStartTimeUtc() == null && session.getActualStartTime() == null) {
            Instant actualStartUtc = attendances.stream()
                    .map(SessionAttendance::getCheckedInAtUtc)
                    .max(Instant::compareTo)
                    .orElse(nowUtc);
            session.setActualStartTimeUtc(actualStartUtc);
            session.setActualStartTime(BookingTime.fromInstant(actualStartUtc));
        }
        sessionRepository.save(session);
        return booking;
    }

    private SessionParticipantRole resolveParticipantRole(Booking booking, UUID currentUserId) {
        if (booking.getMentorUserId() != null && currentUserId.equals(booking.getMentorUserId())) {
            return SessionParticipantRole.MENTOR;
        }
        if (booking.getMentee() != null && currentUserId.equals(booking.getMentee().getId())) {
            return SessionParticipantRole.MENTEE;
        }
        throw new BaseException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền check-in cho booking này");
    }

    private String checkInUnavailableMessage(Session session, Instant nowUtc, Instant startUtc, Instant endUtc) {
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
            return "Session đã kết thúc hoặc bị hủy nên không thể check-in";
        }
        if (startUtc != null && nowUtc.isBefore(startUtc)) {
            return "Chưa đến thời gian check-in của buổi mentoring";
        }
        if (endUtc != null && !nowUtc.isBefore(endUtc)) {
            return "Đã qua thời gian check-in của buổi mentoring";
        }
        return "Booking hiện không đủ điều kiện để check-in";
    }
}
