package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.port.MentorOwnershipQueryPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorOwnershipQueryService implements MentorOwnershipQueryPort {

    private final MentorProfileRepository mentorProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID mentorProfileId, UUID userId) {
        return mentorProfileId != null
                && userId != null
                && mentorProfileRepository.findById(mentorProfileId)
                .map(profile -> userId.equals(profile.getUserId()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveOwner(UUID mentorProfileId, UUID userId) {
        return mentorProfileId != null
                && userId != null
                && mentorProfileRepository.findById(mentorProfileId)
                .map(profile -> userId.equals(profile.getUserId())
                        && profile.getStatus() == com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE)
                .orElse(false);
    }
}
