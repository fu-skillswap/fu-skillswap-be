package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Bounded repair protects the denormalized seat counter from operational drift. */
@Service
@RequiredArgsConstructor
public class GroupSessionSeatReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(GroupSessionSeatReconciliationService.class);

    private static final List<BookingStatus> ACTIVE_SEAT_STATUSES = List.of(
            BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.ACCEPTED, BookingStatus.PAID,
            BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingStatus.UNDER_REVIEW);
    private final GroupSessionRepository groupSessionRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public int reconcileUpcomingSeats() {
        int repaired = 0;
        for (GroupSession candidate : groupSessionRepository.findSeatReconciliationCandidates(
                List.of(GroupSessionStatus.OPEN, GroupSessionStatus.IN_PROGRESS), PageRequest.of(0, 100))) {
            GroupSession locked = groupSessionRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked == null) continue;
            int actual = Math.toIntExact(bookingRepository.countByGroupSessionIdAndStatusIn(locked.getId(), ACTIVE_SEAT_STATUSES));
            if (locked.getReservedSeatCount() != actual) {
                locked.setReservedSeatCount(Math.min(actual, locked.getMaxParticipants()));
                repaired++;
            }
        }
        return repaired;
    }

    /** Historical sessions are audited only: Phase 2 must never reopen or mutate attendee lifecycle here. */
    @Transactional(readOnly = true)
    public int auditTerminalSeatCounters() {
        int mismatches = 0;
        for (GroupSession session : groupSessionRepository.findSeatReconciliationCandidates(
                List.of(GroupSessionStatus.COMPLETED, GroupSessionStatus.CANCELLED), PageRequest.of(0, 100))) {
            int actual = Math.toIntExact(bookingRepository.countByGroupSessionIdAndStatusIn(session.getId(), ACTIVE_SEAT_STATUSES));
            if (session.getReservedSeatCount() != actual) {
                mismatches++;
                log.warn("Group-session seat counter drift detected for terminal session {}: stored={}, actual={}",
                        session.getId(), session.getReservedSeatCount(), actual);
            }
        }
        return mismatches;
    }
}
