package com.fptu.exe.skillswap.modules.session.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.modules.session.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.session.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.session.repository.SessionRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    @Transactional
    public Session createForAcceptedBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking must not be null");
        }
        
        Optional<Session> existingSession = sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, booking.getId());
        if (existingSession.isPresent()) {
            return existingSession.get(); // Idempotency
        }

        Session session = Session.builder()
                .service(booking.getService())
                .mentor(booking.getMentorProfile().getUser())
                .sourceType(SessionSourceType.BOOKING)
                .sourceId(booking.getId())
                .scheduledStartTime(booking.getSelectedStartTime())
                .scheduledEndTime(booking.getSelectedEndTime())
                .status(SessionStatus.SCHEDULED)
                .build();

        try {
            return sessionRepository.save(session);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, booking.getId())
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public Session findByBookingId(UUID bookingId) {
        return sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, bookingId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Session> findByBookingIds(Collection<UUID> bookingIds) {
        if (bookingIds == null || bookingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Session> sessions = sessionRepository.findBySourceTypeAndSourceIdIn(SessionSourceType.BOOKING, bookingIds);
        return sessions.stream().collect(Collectors.toMap(Session::getSourceId, Function.identity(), (left, right) -> left));
    }

    @Transactional
    public void cancelForBooking(UUID bookingId) {
        if (bookingId == null) {
            return;
        }
        Optional<Session> sessionOpt = sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, bookingId);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
                return;
            }
            session.setStatus(SessionStatus.CANCELLED);
            sessionRepository.save(session);
        }
    }
}
