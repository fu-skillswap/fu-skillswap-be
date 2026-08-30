package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public Map<UUID, MentorBlogAuthorSummary> getBlogAuthorSummaries(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return mentorProfileRepository.findAllById(userIds)
                .stream()
                .filter(profile -> profile.getStatus() == MentorStatus.ACTIVE)
                .collect(Collectors.toMap(
                        profile -> profile.getUserId(),
                        profile -> new MentorBlogAuthorSummary(
                                profile.getUserId(),
                                profile.getHeadline(),
                                profile.getVerifiedAt() != null,
                                profile.getAverageRating(),
                                profile.getTotalCompletedSessions(),
                                profile.getVerifiedAt() != null ? "Book a verified mentor session" : "View mentor profile"
                        )
                ));
    }
}
