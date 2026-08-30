package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorBlogAuthorSummary;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;

public interface MentorQueryPort {
    MentorDiscoveryDetailResponse getMentorDetail(UUID mentorId);

    Optional<MentorProfile> findMentorProfileById(UUID mentorId);

    Optional<MentorProfile> findMentorProfileByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Optional<MentorService> findMentorServiceById(UUID serviceId);

    Optional<MentorService> findActiveServiceByIdAndMentorUserId(UUID serviceId, UUID mentorUserId);

    Optional<MentorService> findByIdForPricingPreview(UUID serviceId);

    Optional<MentorProfile> findMentorProfileByIdForUpdate(UUID mentorUserId);

    MentorProfile saveMentorProfile(MentorProfile profile);

    Map<UUID, MentorBlogAuthorSummary> getBlogAuthorSummaries(Collection<UUID> userIds);

    boolean hasCompletedMentorProfile(UUID userId);

    String getLatestVerificationStatus(UUID userId);
}
