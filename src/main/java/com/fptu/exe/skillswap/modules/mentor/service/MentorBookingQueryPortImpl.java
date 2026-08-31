package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.port.EffectiveBookingPolicy;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingActivityCommandPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
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
@Transactional(readOnly = true)
public class MentorBookingQueryPortImpl implements MentorBookingQueryPort, MentorBookingActivityCommandPort {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorBookingPolicyService mentorBookingPolicyService;
    private final MentorProfileService mentorProfileService;

    @Override
    public Optional<MentorBookingCapability> getBookingCapability(UUID mentorUserId) {
        if (mentorUserId == null) {
            return Optional.empty();
        }
        return mentorProfileRepository.findById(mentorUserId).map(profile ->
                new MentorBookingCapability(
                        profile.getUserId(),
                        profile.getStatus() != null ? profile.getStatus().name() : MentorStatus.DRAFT.name(),
                        profile.isAvailable(),
                        profile.getSessionDuration(),
                        profile.getAverageRating(),
                        profile.getTotalCompletedSessions(),
                        profile.getBookingSuspendedUntil(),
                        profile.getStatus() == MentorStatus.ACTIVE && profile.getVerifiedAt() != null,
                        mentorProfileService.hasCompletedMentorProfile(profile.getUserId())
                )
        );
    }

    @Override
    public Optional<ServiceSlotCandidate> getServiceCandidate(UUID serviceId) {
        if (serviceId == null) {
            return Optional.empty();
        }
        return mentorServiceRepository.findById(serviceId).map(this::toCandidate);
    }

    @Override
    public Optional<ServiceSlotCandidate> getActiveServiceCandidate(UUID serviceId, UUID mentorUserId) {
        if (serviceId == null || mentorUserId == null) {
            return Optional.empty();
        }
        return mentorServiceRepository.findByIdAndMentorProfileUserIdAndIsActiveTrue(serviceId, mentorUserId)
                .map(this::toCandidate);
    }

    @Override
    public List<ServiceSlotCandidate> getActiveServicesForMentor(UUID mentorUserId) {
        if (mentorUserId == null) {
            return List.of();
        }
        return mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public Map<UUID, ServiceSlotCandidate> getServicesByIds(Collection<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mentorServiceRepository.findAllById(serviceIds).stream()
                .map(this::toCandidate)
                .collect(Collectors.toMap(ServiceSlotCandidate::serviceId, Function.identity(), (a, b) -> a));
    }

    @Override
    public EffectiveBookingPolicy getEffectiveBookingPolicy(UUID mentorUserId) {
        MentorBookingPolicyService.MentorBookingPolicySnapshot policy = mentorBookingPolicyService.getEffectivePolicy(mentorUserId);
        return new EffectiveBookingPolicy(
                mentorUserId,
                policy.minimumBookingLeadTimeMinutes(),
                policy.maximumBookingHorizonDays(),
                policy.timezone()
        );
    }

    @Override
    public void validateBookingWindow(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now) {
        mentorBookingPolicyService.validateBookingWindow(mentorUserId, selectedStartTime, now);
    }

    @Override
    public boolean isBookableStartTime(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now) {
        return mentorBookingPolicyService.isBookableStartTime(mentorUserId, selectedStartTime, now);
    }

    @Override
    public boolean isActiveVerifiedMentor(UUID mentorUserId) {
        if (mentorUserId == null) {
            return false;
        }
        return mentorProfileRepository.findById(mentorUserId)
                .map(p -> p.getStatus() == MentorStatus.ACTIVE && p.getVerifiedAt() != null)
                .orElse(false);
    }

    @Override
    public List<UUID> findActiveMentorUserIds() {
        return mentorProfileRepository.findActiveMentorUserIds(MentorStatus.ACTIVE);
    }

    @Override
    public boolean existsById(UUID mentorUserId) {
        return mentorUserId != null && mentorProfileRepository.existsById(mentorUserId);
    }

    @Override
    @Transactional
    public void recordMentorActivity(UUID mentorUserId, java.time.Instant occurredAtUtc) {
        MentorProfile profile = requireProfileForUpdate(mentorUserId);
        profile.setLastActiveAt(java.time.LocalDateTime.ofInstant(occurredAtUtc, java.time.ZoneOffset.UTC));
        mentorProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void recordCompletedSession(UUID mentorUserId, java.time.Instant completedAtUtc) {
        MentorProfile profile = requireProfileForUpdate(mentorUserId);
        profile.setTotalCompletedSessions(defaultInteger(profile.getTotalCompletedSessions()) + 1);
        profile.setTotalSessions(defaultInteger(profile.getTotalSessions()) + 1);
        profile.setLastActiveAt(java.time.LocalDateTime.ofInstant(completedAtUtc, java.time.ZoneOffset.UTC));
        mentorProfileRepository.save(profile);
    }

    private MentorProfile requireProfileForUpdate(UUID mentorUserId) {
        return mentorProfileRepository.findByIdForUpdate(mentorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor profile not found"));
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private ServiceSlotCandidate toCandidate(MentorService service) {
        return new ServiceSlotCandidate(
                service.getId(),
                service.getMentorProfile() != null ? service.getMentorProfile().getUserId() : null,
                service.getTitle(),
                service.getDescription(),
                service.getExpectedOutcome(),
                service.getDurationMinutes(),
                service.getPriceScoin(),
                service.isFree(),
                service.isActive(),
                service.getDeliveryMode() != null ? service.getDeliveryMode().name() : null,
                service.getMentorProfile() != null && service.getMentorProfile().getTeachingMode() != null
                        ? service.getMentorProfile().getTeachingMode().name()
                        : null,
                service.isMaintainPostSessionChat()
        );
    }
}
