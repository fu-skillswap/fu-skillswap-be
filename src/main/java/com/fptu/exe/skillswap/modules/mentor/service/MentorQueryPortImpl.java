package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.dto.MentorBlogAuthorSummary;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorQueryPortImpl implements MentorQueryPort {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MentorDiscoveryService mentorDiscoveryService;
    @Override
    public com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse getMentorDetail(UUID mentorId) {
        return mentorDiscoveryService != null ? mentorDiscoveryService.getMentorDetail(mentorId) : null;
    }

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorContentAccessService mentorContentAccessService;
    private final MentorProfileService mentorProfileService;
    private final MentorVerificationService mentorVerificationService;

    @Override
    public Optional<MentorProfile> findMentorProfileById(UUID mentorId) {
        return mentorProfileRepository.findById(mentorId);
    }

    @Override
    public Optional<MentorProfile> findMentorProfileByUserId(UUID userId) {
        return mentorProfileRepository.findByUserId(userId);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return mentorProfileRepository.existsByUserId(userId);
    }

    @Override
    public Optional<MentorService> findMentorServiceById(UUID serviceId) {
        return mentorServiceRepository.findById(serviceId);
    }

    @Override
    public Optional<MentorService> findActiveServiceByIdAndMentorUserId(UUID serviceId, UUID mentorUserId) {
        return mentorServiceRepository.findByIdAndMentorProfileUserIdAndIsActiveTrue(serviceId, mentorUserId);
    }

    @Override
    public Optional<MentorService> findByIdForPricingPreview(UUID serviceId) {
        return mentorServiceRepository.findByIdForPricingPreview(serviceId);
    }

    @Override
    public Optional<MentorProfile> findMentorProfileByIdForUpdate(UUID mentorUserId) {
        return mentorProfileRepository.findByIdForUpdate(mentorUserId);
    }

    @Override
    public MentorProfile saveMentorProfile(MentorProfile profile) {
        return mentorProfileRepository.save(profile);
    }

    @Override
    public boolean hasCompletedMentorProfile(UUID userId) {
        return mentorProfileService != null && mentorProfileService.hasCompletedMentorProfile(userId);
    }

    @Override
    public String getLatestVerificationStatus(UUID userId) {
        return mentorVerificationService != null ? mentorVerificationService.getLatestVerificationStatus(userId) : "NOT_STARTED";
    }

    @Override
    public Map<UUID, MentorBlogAuthorSummary> getBlogAuthorSummaries(Collection<UUID> userIds) {
        return mentorContentAccessService.getBlogAuthorSummaries(userIds);
    }
}
