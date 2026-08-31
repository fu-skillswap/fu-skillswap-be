package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.port.MentorDisciplinePort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorQueryPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorQueryPortImpl implements MentorQueryPort, MentorDisciplinePort {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;

    @Override
    public Optional<MentorProfile> findMentorProfileById(UUID mentorId) {
        return mentorId == null ? Optional.empty() : mentorProfileRepository.findById(mentorId);
    }

    @Override
    public Optional<MentorProfile> findMentorProfileByUserId(UUID userId) {
        return userId == null ? Optional.empty() : mentorProfileRepository.findById(userId);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return userId != null && mentorProfileRepository.existsById(userId);
    }

    @Override
    public boolean isActiveVerifiedMentor(UUID userId) {
        return userId != null && mentorProfileRepository.findWithUserByUserId(userId)
                .map(profile -> profile.getStatus() == com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE
                        && profile.getVerifiedAt() != null)
                .orElse(false);
    }

    @Override
    public Optional<MentorService> findMentorServiceById(UUID serviceId) {
        return serviceId == null ? Optional.empty() : mentorServiceRepository.findById(serviceId);
    }

    @Override
    public Optional<MentorService> findActiveServiceByIdAndMentorUserId(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null || mentorUserId == null) {
            return Optional.empty();
        }
        return mentorServiceRepository.findByIdAndMentorProfileUserIdAndIsActiveTrue(serviceId, mentorUserId);
    }

    @Override
    public Optional<MentorService> findByIdForPricingPreview(UUID serviceId) {
        return serviceId == null ? Optional.empty() : mentorServiceRepository.findByIdForPricingPreview(serviceId);
    }

    @Override
    public Optional<MentorProfile> findMentorProfileByIdForUpdate(UUID mentorUserId) {
        return mentorUserId == null ? Optional.empty() : mentorProfileRepository.findByIdForUpdate(mentorUserId);
    }

    @Override
    @Transactional
    public MentorProfile saveMentorProfile(MentorProfile profile) {
        return mentorProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void incrementMentorNoShowCount(UUID mentorUserId) {
        // Deprecated: violations are recorded only through MentorViolationService.
    }

    @Override
    @Transactional
    public void incrementMentorCompletionOverdueCount(UUID mentorUserId) {
        // Deprecated: violations are recorded only through MentorViolationService.
    }

    @Override
    public java.util.List<UUID> findActiveMentorUserIds() {
        return mentorProfileRepository.findActiveMentorUserIds(com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.ACTIVE);
    }

    @Override
    public Optional<MentorProfile> findWithUserByUserId(UUID userId) {
        return userId == null ? Optional.empty() : mentorProfileRepository.findWithUserByUserId(userId);
    }

    @Override
    public Optional<MentorService> findServiceByIdAndMentorProfileUserId(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null || mentorUserId == null) {
            return Optional.empty();
        }
        return mentorServiceRepository.findByIdAndMentorProfileUserId(serviceId, mentorUserId);
    }

    @Override
    public java.util.List<MentorService> findAllServicesByIdIn(java.util.Collection<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return mentorServiceRepository.findAllById(serviceIds);
    }

    @Override
    public java.util.List<MentorService> findActiveServicesByMentorUserId(UUID mentorUserId) {
        return mentorUserId == null ? java.util.List.of()
                : mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId);
    }
}
