package com.fptu.exe.skillswap.modules.mentor.port;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;

import java.util.Optional;
import java.util.UUID;

public interface MentorQueryPort {

    Optional<MentorProfile> findMentorProfileById(UUID mentorId);

    Optional<MentorProfile> findMentorProfileByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Optional<MentorService> findMentorServiceById(UUID serviceId);

    Optional<MentorService> findActiveServiceByIdAndMentorUserId(UUID serviceId, UUID mentorUserId);

    Optional<MentorService> findByIdForPricingPreview(UUID serviceId);

    Optional<MentorProfile> findMentorProfileByIdForUpdate(UUID mentorUserId);

    MentorProfile saveMentorProfile(MentorProfile profile);
}
