package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorContentAccessService {

    private final MentorProfileRepository mentorProfileRepository;

    @Transactional(readOnly = true)
    public boolean canAccessMentorOnlyContent(UUID userId) {
        if (userId == null) {
            return false;
        }
        return mentorProfileRepository.findById(userId)
                .map(profile -> profile.getStatus() == MentorStatus.ACTIVE)
                .orElse(false);
    }
}
