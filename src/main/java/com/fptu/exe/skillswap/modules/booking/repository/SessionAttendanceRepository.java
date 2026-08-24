package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.SessionAttendance;
import com.fptu.exe.skillswap.modules.booking.domain.SessionParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, UUID> {

    Optional<SessionAttendance> findBySessionIdAndParticipantRole(UUID sessionId, SessionParticipantRole participantRole);

    List<SessionAttendance> findBySessionId(UUID sessionId);

    List<SessionAttendance> findBySessionIdIn(Collection<UUID> sessionIds);

    @Query("""
            select attendance
            from SessionAttendance attendance
            where attendance.session.sourceType = com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType.BOOKING
              and attendance.session.sourceId = :bookingId
            """)
    List<SessionAttendance> findByBookingId(@Param("bookingId") UUID bookingId);
}
