package com.fptu.exe.skillswap.modules.mentor.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MentorBookingQueryPort {

    Optional<MentorBookingCapability> getBookingCapability(UUID mentorUserId);

    Optional<ServiceSlotCandidate> getServiceCandidate(UUID serviceId);

    Optional<ServiceSlotCandidate> getActiveServiceCandidate(UUID serviceId, UUID mentorUserId);

    List<ServiceSlotCandidate> getActiveServicesForMentor(UUID mentorUserId);

    Map<UUID, ServiceSlotCandidate> getServicesByIds(Collection<UUID> serviceIds);

    EffectiveBookingPolicy getEffectiveBookingPolicy(UUID mentorUserId);

    void validateBookingWindow(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now);

    boolean isBookableStartTime(UUID mentorUserId, LocalDateTime selectedStartTime, LocalDateTime now);

    boolean isActiveVerifiedMentor(UUID mentorUserId);

    List<UUID> findActiveMentorUserIds();

    boolean existsById(UUID mentorUserId);
}
