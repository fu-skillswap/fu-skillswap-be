package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.event.MentorAvailabilityChangedEvent;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MentorProfileService {

    private static final Set<Integer> SUPPORT_LEVELS = Set.of(1, 2, 3, 4);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MentorBookingPolicyService mentorBookingPolicyService;
    private final UserQueryPort userQueryPort;
    private final PublicAssetUploadPort publicAssetUploadPort;

    @Autowired
    public MentorProfileService(
            MentorProfileRepository mentorProfileRepository,
            MentorSubjectResultRepository mentorSubjectResultRepository,
            MentorFeaturedProjectRepository mentorFeaturedProjectRepository,
            MentorAchievementRepository mentorAchievementRepository,
            ApplicationEventPublisher eventPublisher,
            MentorBookingPolicyService mentorBookingPolicyService,
            UserQueryPort userQueryPort,
            PublicAssetUploadPort publicAssetUploadPort
    ) {
        this.mentorProfileRepository = mentorProfileRepository;
        this.mentorSubjectResultRepository = mentorSubjectResultRepository;
        this.mentorFeaturedProjectRepository = mentorFeaturedProjectRepository;
        this.mentorAchievementRepository = mentorAchievementRepository;
        this.eventPublisher = eventPublisher;
        this.mentorBookingPolicyService = mentorBookingPolicyService;
        this.userQueryPort = userQueryPort;
        this.publicAssetUploadPort = publicAssetUploadPort;
    }

    public MentorProfileService(
            MentorProfileRepository mentorProfileRepository,
            MentorSubjectResultRepository mentorSubjectResultRepository,
            MentorFeaturedProjectRepository mentorFeaturedProjectRepository,
            MentorAchievementRepository mentorAchievementRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this(mentorProfileRepository,
                mentorSubjectResultRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository,
                eventPublisher,
                null,
                null,
                null);
    }

    @Transactional(readOnly = true)
    public MentorProfileResponse getMyProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserId(userId)
                .map(this::mapToResponse)
                .orElseGet(() -> MentorProfileResponse.empty(userId));
    }

    @Transactional(readOnly = true)
    public boolean hasCompletedMentorProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserId(userId)
                .map(this::isRequiredFieldsCompleted)
                .orElse(false);
    }

    @Transactional
    public MentorProfileResponse upsertProfile(UUID userId, MentorProfileUpsertRequest request) {
        requireUserId(userId);
        requireProfileRequest(request);
        MentorProfile profile = getOrCreateProfile(userId);
        Boolean previousAvailability = null;
        Boolean currentAvailability = null;

        profile.setHeadline(clean(request.headline()));
        profile.setExpertiseDescription(clean(request.expertiseDescription()));
        profile.setFoundationSupportLevel(validateSupportLevel(request.foundationSupportLevel(), "lấy gốc"));
        profile.setOutputReviewSupportLevel(validateSupportLevel(request.outputReviewSupportLevel(), "review output"));
        profile.setDirectionSupportLevel(validateSupportLevel(request.directionSupportLevel(), "định hướng"));
        profile.setPhoneNumber(clean(request.phoneNumber()));
        if (request.isAvailable() != null) {
            previousAvailability = profile.isAvailable();
            currentAvailability = request.isAvailable();
            profile.setAvailable(currentAvailability);
        }
        profile.setGithubUrl(cleanNullable(request.githubUrl()));
        profile.setPortfolioUrl(cleanNullable(request.portfolioUrl()));
        profile.setSupportingSubjects(buildLegacySubjectSummary(request.subjectResults()));
        touchMentorActivity(profile, DateTimeUtil.now());

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        if (mentorBookingPolicyService != null) {
            mentorBookingPolicyService.upsertPolicy(
                    savedProfile.getUserId(),
                    request.minimumBookingLeadTimeMinutes(),
                    request.maximumBookingHorizonDays(),
                    request.bookingTimezone()
            );
        }
        replaceSubjectResults(savedProfile, request.subjectResults());
        publishAvailabilityChangedEventIfNeeded(savedProfile, previousAvailability, currentAvailability);
        return mapToResponse(savedProfile);
    }

    private MentorProfile getOrCreateProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    if (userQueryPort == null || !userQueryPort.existsById(userId)) {
                        throw new ResourceNotFoundException("Không tìm thấy người dùng");
                    }
                    MentorProfile profile = new MentorProfile();
                    profile.setUserId(userId);
                    profile.setAvailable(true);
                    return profile;
                });
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile) {
        UserSummaryRecord user = userQueryPort == null ? null : userQueryPort.findUserSummaryById(profile.getUserId()).orElse(null);
        List<MentorSubjectResultResponse> subjectResults = loadSubjectResults(profile.getUserId());
        List<MentorFeaturedProjectResponse> featuredProjects = loadFeaturedProjects(profile.getUserId());
        List<MentorAchievementResponse> achievements = loadAchievements(profile.getUserId());
        MentorBookingPolicyService.MentorBookingPolicySnapshot policy = mentorBookingPolicyService == null
                ? MentorBookingPolicyService.MentorBookingPolicySnapshot.defaults()
                : mentorBookingPolicyService.getEffectivePolicy(profile.getUserId());
        return MentorProfileResponse.builder()
                .exists(true)
                .requiredFieldsCompleted(isRequiredFieldsCompleted(profile))
                .userId(profile.getUserId())
                .email(user != null ? user.email() : null)
                .displayName(user != null ? user.fullName() : null)
                .avatarUrl(user != null ? user.avatarUrl() : null)
                .mentorStatus(profile.getStatus())
                .headline(profile.getHeadline())
                .expertiseDescription(profile.getExpertiseDescription())
                .isAvailable(profile.isAvailable())
                .bookingSuspendedUntil(profile.getBookingSuspendedUntil())
                .verifiedAt(profile.getVerifiedAt())
                .minimumBookingLeadTimeMinutes(policy.minimumBookingLeadTimeMinutes())
                .maximumBookingHorizonDays(policy.maximumBookingHorizonDays())
                .bookingTimezone(policy.timezone())
                .subjectResults(subjectResults)
                .foundationSupportLevel(profile.getFoundationSupportLevel())
                .outputReviewSupportLevel(profile.getOutputReviewSupportLevel())
                .directionSupportLevel(profile.getDirectionSupportLevel())
                .featuredProjects(featuredProjects)
                .achievements(achievements)
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .phoneNumber(profile.getPhoneNumber())
                .ratingAverage(profile.getAverageRating())
                .reviewCount(profile.getTotalReviews())
                .completedSessions(profile.getTotalCompletedSessions())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private boolean isRequiredFieldsCompleted(MentorProfile profile) {
        return hasText(profile.getHeadline())
                && hasText(profile.getExpertiseDescription())
                && hasText(profile.getPhoneNumber())
                && isValidSupportLevel(profile.getFoundationSupportLevel())
                && isValidSupportLevel(profile.getOutputReviewSupportLevel())
                && isValidSupportLevel(profile.getDirectionSupportLevel())
                && !mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(profile.getUserId()).isEmpty();
    }

    private boolean isValidSupportLevel(Integer level) {
        return level != null && SUPPORTED_SUPPORT_LEVELS().contains(level);
    }

    private Set<Integer> SUPPORTED_SUPPORT_LEVELS() {
        return SUPPORT_LEVELS;
    }

    private void replaceSubjectResults(MentorProfile profile, List<MentorSubjectResultRequest> subjectResults) {
        mentorSubjectResultRepository.deleteByMentorProfileUserId(profile.getUserId());
        if (subjectResults == null || subjectResults.isEmpty()) {
            return;
        }

        Set<String> uniqueCodes = new LinkedHashSet<>();
        int displayOrder = 0;
        for (MentorSubjectResultRequest item : subjectResults) {
            if (item == null) {
                continue;
            }
            String code = cleanSubjectCode(item.subjectCode());
            String name = cleanSubjectName(item.subjectName());
            if (code == null || name == null) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Mã môn và tên môn học không được để trống");
            }
            if (!uniqueCodes.add(code)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách môn học bị trùng mã: " + code);
            }
            BigDecimal score = item.scoreValue();
            if (score != null && (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Điểm môn " + code + " phải nằm trong khoảng từ 0.0 đến 10.0");
            }

            MentorSubjectResult entity = MentorSubjectResult.builder()
                    .mentorProfile(profile)
                    .subjectCode(code)
                    .subjectName(name)
                    .scoreValue(score)
                    .displayOrder(displayOrder++)
                    .build();
            mentorSubjectResultRepository.save(entity);
        }
    }

    private List<MentorSubjectResultResponse> loadSubjectResults(UUID userId) {
        return mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(userId)
                .stream()
                .map(this::mapSubjectResult)
                .toList();
    }

    private List<MentorFeaturedProjectResponse> loadFeaturedProjects(UUID userId) {
        return mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(userId)
                .stream()
                .map(item -> mapFeaturedProject(item, userId))
                .toList();
    }

    private List<MentorAchievementResponse> loadAchievements(UUID userId) {
        return mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(userId)
                .stream()
                .map(item -> mapAchievement(item, userId))
                .toList();
    }

    private MentorSubjectResultResponse mapSubjectResult(MentorSubjectResult item) {
        return MentorSubjectResultResponse.builder()
                .id(item.getId())
                .subjectCode(item.getSubjectCode())
                .subjectName(item.getSubjectName())
                .scoreValue(item.getScoreValue())
                .displayOrder(item.getDisplayOrder())
                .build();
    }

    private MentorFeaturedProjectResponse mapFeaturedProject(MentorFeaturedProject item, UUID userId) {
        return MentorFeaturedProjectResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .pictureUrl(item.getPictureFileId() == null || publicAssetUploadPort == null ? null
                        : publicAssetUploadPort.requireOwnedPortfolioImage(userId, item.getPictureFileId()).publicUrl())
                .content(item.getContent())
                .projectDescription(item.getProjectDescription())
                .liveDemoUrl(item.getLiveDemoUrl())
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private MentorAchievementResponse mapAchievement(MentorAchievement item, UUID userId) {
        return MentorAchievementResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .pictureUrl(item.getPictureFileId() == null || publicAssetUploadPort == null ? null
                        : publicAssetUploadPort.requireOwnedPortfolioImage(userId, item.getPictureFileId()).publicUrl())
                .awardDescription(item.getAwardDescription())
                .productHeader(item.getProductHeader())
                .productDescription(item.getProductDescription())
                .displayOrder(item.getDisplayOrder())
                .build();
    }

    private void touchMentorActivity(MentorProfile profile, LocalDateTime when) {
        profile.setLastActiveAt(when);
    }

    private void publishAvailabilityChangedEventIfNeeded(
            MentorProfile profile,
            Boolean previousAvailability,
            Boolean currentAvailability
    ) {
        if (previousAvailability == null || currentAvailability == null || previousAvailability.equals(currentAvailability)) {
            return;
        }
        eventPublisher.publishEvent(new MentorAvailabilityChangedEvent(
                UUID.randomUUID(),
                profile.getUserId(),
                profile.getUserId(),
                Boolean.TRUE.equals(previousAvailability),
                Boolean.TRUE.equals(currentAvailability),
                DateTimeUtil.now()
        ));
    }

    private String buildLegacySubjectSummary(List<MentorSubjectResultRequest> subjectResults) {
        if (subjectResults == null || subjectResults.isEmpty()) {
            return null;
        }
        return subjectResults.stream()
                .filter(item -> item != null && hasText(item.subjectCode()))
                .map(item -> item.subjectCode().trim().toUpperCase())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private Integer validateSupportLevel(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (!SUPPORT_LEVELS.contains(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mức độ hỗ trợ " + fieldName + " phải từ 1 đến 4");
        }
        return value;
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }

    private void requireProfileRequest(MentorProfileUpsertRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thông tin hồ sơ mentor không được để trống");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String cleanSubjectCode(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase();
    }

    private String cleanSubjectName(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
