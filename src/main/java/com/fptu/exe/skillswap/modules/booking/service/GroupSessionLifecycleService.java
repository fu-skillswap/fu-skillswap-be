package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupSessionLifecycleService {

    private final GroupSessionRepository groupSessionRepository;
    private GroupSessionExperienceService groupSessionExperienceService;

    @Autowired(required = false)
    void setGroupSessionExperienceService(GroupSessionExperienceService groupSessionExperienceService) {
        this.groupSessionExperienceService = groupSessionExperienceService;
    }

    /** Processes a small batch so the single VPS scheduler never monopolizes the database. */
    @Transactional
    public int processDueSessions() {
        LocalDateTime now = LocalDateTime.ofInstant(DateTimeUtil.getClock().instant(), ZoneOffset.UTC);
        int changed = 0;
        for (UUID id : groupSessionRepository.findLifecycleCandidates(
                GroupSessionStatus.OPEN, GroupSessionStatus.IN_PROGRESS,
                GroupSessionRegistrationStatus.OPEN, now, PageRequest.of(0, 100))) {
            changed += processOne(id, now) ? 1 : 0;
        }
        if (groupSessionExperienceService != null) {
            groupSessionRepository.findExperienceBackfillCandidates(
                    List.of(GroupSessionStatus.OPEN, GroupSessionStatus.IN_PROGRESS, GroupSessionStatus.COMPLETED), PageRequest.of(0, 100))
                    .forEach(groupSessionExperienceService::backfillSharedExperience);
            for (UUID id : groupSessionRepository.findCompletedBefore(GroupSessionStatus.COMPLETED, now.minusHours(24), PageRequest.of(0, 100))) {
                groupSessionExperienceService.makeCompletedSessionReadOnly(id, now);
            }
        }
        return changed;
    }

    private boolean processOne(UUID id, LocalDateTime now) {
        GroupSession session = groupSessionRepository.findById(id).orElse(null);
        if (session == null || session.getStatus() == GroupSessionStatus.CANCELLED || session.getStatus() == GroupSessionStatus.COMPLETED) {
            return false;
        }
        boolean changed = false;
        if (session.getRegistrationStatus() == GroupSessionRegistrationStatus.OPEN
                && !session.getRegistrationClosesAt().isAfter(now)) {
            session.setRegistrationStatus(GroupSessionRegistrationStatus.CLOSED);
            changed = true;
        }
        if (session.getStatus() == GroupSessionStatus.OPEN && !session.getScheduledStartAt().isAfter(now)) {
            session.setStatus(GroupSessionStatus.IN_PROGRESS);
            changed = true;
        }
        if (session.getStatus() == GroupSessionStatus.IN_PROGRESS && !session.getScheduledEndAt().isAfter(now)) {
            session.setStatus(GroupSessionStatus.COMPLETED);
            changed = true;
        }
        return changed;
    }
}
