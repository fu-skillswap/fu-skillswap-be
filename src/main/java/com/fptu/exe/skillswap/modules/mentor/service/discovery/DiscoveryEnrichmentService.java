package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DiscoveryEnrichmentService {

    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final BookingAvailabilityQueryPort bookingAvailabilityQueryPort;
    private final PublicAssetUploadPort publicAssetUploadPort;

    @Autowired
    public DiscoveryEnrichmentService(
            MentorSubjectResultRepository mentorSubjectResultRepository,
            MentorFeaturedProjectRepository mentorFeaturedProjectRepository,
            MentorAchievementRepository mentorAchievementRepository,
            MentorServiceRepository mentorServiceRepository,
            BookingAvailabilityQueryPort bookingAvailabilityQueryPort,
            PublicAssetUploadPort publicAssetUploadPort
    ) {
        this.mentorSubjectResultRepository = mentorSubjectResultRepository;
        this.mentorFeaturedProjectRepository = mentorFeaturedProjectRepository;
        this.mentorAchievementRepository = mentorAchievementRepository;
        this.mentorServiceRepository = mentorServiceRepository;
        this.bookingAvailabilityQueryPort = bookingAvailabilityQueryPort;
        this.publicAssetUploadPort = publicAssetUploadPort;
    }

    /** Compatibility constructor for focused discovery tests; production wiring uses the port-aware constructor. */
    public DiscoveryEnrichmentService(
            MentorSubjectResultRepository mentorSubjectResultRepository,
            MentorFeaturedProjectRepository mentorFeaturedProjectRepository,
            MentorAchievementRepository mentorAchievementRepository,
            MentorServiceRepository mentorServiceRepository,
            BookingAvailabilityQueryPort bookingAvailabilityQueryPort
    ) {
        this(mentorSubjectResultRepository, mentorFeaturedProjectRepository, mentorAchievementRepository,
                mentorServiceRepository, bookingAvailabilityQueryPort, null);
    }

    public Map<UUID, MentorEnrichedData> loadMentorEnrichedData(
            Collection<UUID> mentorUserIds,
            LocalDateTime now
    ) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorSubjectResultResponse>> subjectResultsByMentor = loadSubjectResultsByMentor(mentorUserIds);
        Map<UUID, List<MentorFeaturedProjectResponse>> featuredProjectsByMentor = loadFeaturedProjectsByMentor(mentorUserIds);
        Map<UUID, List<MentorAchievementResponse>> achievementsByMentor = loadAchievementsByMentor(mentorUserIds);
        Map<UUID, List<MentorService>> servicesByMentor = groupServicesByMentor(loadActiveServicesByMentorIds(mentorUserIds));
        Set<UUID> mentorsWithAvailability = loadMentorsWithAvailability(mentorUserIds, now);
        Set<UUID> mentorsWithPreferredDurationAvailability = Collections.emptySet();

        Map<UUID, MentorEnrichedData> result = new HashMap<>();
        for (UUID mentorUserId : mentorUserIds) {
            result.put(mentorUserId, new MentorEnrichedData(
                    subjectResultsByMentor.getOrDefault(mentorUserId, List.of()),
                    featuredProjectsByMentor.getOrDefault(mentorUserId, List.of()),
                    achievementsByMentor.getOrDefault(mentorUserId, List.of()),
                    servicesByMentor.getOrDefault(mentorUserId, List.of()),
                    mentorsWithAvailability.contains(mentorUserId),
                    mentorsWithPreferredDurationAvailability.contains(mentorUserId)
            ));
        }
        return result;
    }

    private Set<UUID> loadMentorsWithAvailability(Collection<UUID> mentorUserIds, LocalDateTime now) {
        return new java.util.HashSet<>(Optional.ofNullable(
                bookingAvailabilityQueryPort.findMentorUserIdsWithActiveSlotsInFuture(mentorUserIds, com.fptu.exe.skillswap.shared.time.BusinessTime.toInstant(now))
        ).orElse(List.of()));
    }

    private List<MentorService> loadActiveServicesByMentorIds(Collection<UUID> mentorUserIds) {
        return Optional.ofNullable(mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(new ArrayList<>(mentorUserIds)))
                .orElse(List.of());
    }

    private Map<UUID, List<MentorService>> groupServicesByMentor(List<MentorService> services) {
        Map<UUID, List<MentorService>> grouped = new HashMap<>();
        for (MentorService service : services) {
            if (service == null || service.getMentorProfile() == null || service.getMentorProfile().getUserId() == null) {
                continue;
            }
            grouped.computeIfAbsent(service.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                    .add(service);
        }
        return grouped;
    }

    private Map<UUID, List<MentorSubjectResultResponse>> loadSubjectResultsByMentor(Collection<UUID> mentorUserIds) {
        Map<UUID, List<MentorSubjectResultResponse>> result = new HashMap<>();
        mentorSubjectResultRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(mentorUserIds)
                .forEach(subjectResult -> result.computeIfAbsent(subjectResult.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(MentorSubjectResultResponse.builder()
                                .id(subjectResult.getId())
                                .subjectCode(subjectResult.getSubjectCode())
                                .subjectName(subjectResult.getSubjectName())
                                .scoreValue(subjectResult.getScoreValue())
                                .displayOrder(subjectResult.getDisplayOrder())
                                .build()));
        return result;
    }

    private Map<UUID, List<MentorFeaturedProjectResponse>> loadFeaturedProjectsByMentor(Collection<UUID> mentorUserIds) {
        Map<UUID, List<MentorFeaturedProjectResponse>> result = new HashMap<>();
        mentorFeaturedProjectRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(mentorUserIds)
                .forEach(project -> result.computeIfAbsent(project.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(MentorFeaturedProjectResponse.builder()
                                .id(project.getId())
                                .title(project.getTitle())
                                .pictureUrl(assetUrl(project.getMentorProfile().getUserId(), project.getPictureFileId()))
                                .content(project.getContent())
                                .projectDescription(project.getProjectDescription())
                                .liveDemoUrl(project.getLiveDemoUrl())
                                .displayOrder(project.getDisplayOrder())
                                .createdAt(project.getCreatedAt())
                                .updatedAt(project.getUpdatedAt())
                                .build()));
        return result;
    }

    private Map<UUID, List<MentorAchievementResponse>> loadAchievementsByMentor(Collection<UUID> mentorUserIds) {
        Map<UUID, List<MentorAchievementResponse>> result = new HashMap<>();
        mentorAchievementRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(mentorUserIds)
                .forEach(achievement -> result.computeIfAbsent(achievement.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(MentorAchievementResponse.builder()
                                .id(achievement.getId())
                                .title(achievement.getTitle())
                                .pictureUrl(assetUrl(achievement.getMentorProfile().getUserId(), achievement.getPictureFileId()))
                                .awardDescription(achievement.getAwardDescription())
                                .achievedAt(achievement.getAchievedAt())
                                .productHeader(achievement.getProductHeader())
                                .productDescription(achievement.getProductDescription())
                                .demoUrl(achievement.getDemoUrl())
                                .displayOrder(achievement.getDisplayOrder())
                                .createdAt(achievement.getCreatedAt())
                                .updatedAt(achievement.getUpdatedAt())
                                .build()));
        return result;
    }

    private String assetUrl(UUID ownerId, UUID assetId) {
        if (assetId == null || publicAssetUploadPort == null) return null;
        return publicAssetUploadPort.requireOwnedPortfolioImage(ownerId, assetId).publicUrl();
    }
}
