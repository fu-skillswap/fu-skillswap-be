package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentorProfileService {

    private static final Set<Integer> SUPPORT_LEVELS = Set.of(1, 2, 3, 4);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    @org.springframework.context.annotation.Lazy
    private final com.fptu.exe.skillswap.modules.booking.service.BookingService bookingService;

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
        return getMyProfile(userId).requiredFieldsCompleted();
    }

    @Transactional
    public MentorProfileResponse upsertProfile(UUID userId, MentorProfileUpsertRequest request) {
        requireUserId(userId);
        requireProfileRequest(request);
        MentorProfile profile = getOrCreateProfile(userId);
        List<Tag> helpTopics = loadAndValidateTags(request.helpTopicIds(), Set.of(TagType.HELP_TOPIC), "chủ đề hỗ trợ");

        profile.setHeadline(clean(request.headline()));
        profile.setExpertiseDescription(clean(request.expertiseDescription()));
        profile.setFoundationSupportLevel(validateSupportLevel(request.foundationSupportLevel(), "lấy gốc"));
        profile.setOutputReviewSupportLevel(validateSupportLevel(request.outputReviewSupportLevel(), "review output"));
        profile.setDirectionSupportLevel(validateSupportLevel(request.directionSupportLevel(), "định hướng"));
        profile.setPhoneNumber(clean(request.phoneNumber()));
        if (request.isAvailable() != null) {
            boolean wasAvailable = profile.isAvailable();
            boolean nowAvailable = request.isAvailable();
            profile.setAvailable(nowAvailable);
            
            if (wasAvailable && !nowAvailable) {
                bookingService.rejectAllPendingBookingsForMentor(userId, "Mentor đã chuyển sang trạng thái không nhận lịch");
            }
        }
        profile.setGithubUrl(cleanNullable(request.githubUrl()));
        profile.setPortfolioUrl(cleanNullable(request.portfolioUrl()));
        profile.setSupportingSubjects(buildLegacySubjectSummary(request.subjectResults()));
        touchMentorActivity(profile, LocalDateTime.now());

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        replaceHelpTopics(savedProfile, helpTopics);
        replaceSubjectResults(savedProfile, request.subjectResults());
        return mapToResponseFromTags(savedProfile, helpTopics);
    }

    private MentorProfile getOrCreateProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
                    MentorProfile profile = new MentorProfile();
                    profile.setUser(user);
                    profile.setAvailable(true);
                    return profile;
                });
    }

    private List<Tag> loadAndValidateTags(List<UUID> tagIds, Set<TagType> allowedTypes, String label) {
        if (tagIds == null || tagIds.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách " + label + " không được để trống");
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(tagIds);
        if (uniqueIds.size() != tagIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách " + label + " không được trùng lặp");
        }

        Map<UUID, Tag> tagsById = tagRepository.findByIdInAndStatus(uniqueIds, TagStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));

        if (tagsById.size() != uniqueIds.size()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều " + label + " không tồn tại hoặc chưa được duyệt");
        }

        List<Tag> tags = uniqueIds.stream()
                .map(tagsById::get)
                .toList();
        boolean hasInvalidType = tags.stream().anyMatch(tag -> !allowedTypes.contains(tag.getType()));
        if (hasInvalidType) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều " + label + " không đúng loại dữ liệu");
        }
        return tags;
    }

    private void replaceHelpTopics(MentorProfile profile, List<Tag> helpTopics) {
        mentorTagRepository.deleteByIdMentorUserId(profile.getUserId());

        List<MentorTag> mentorTags = helpTopics.stream()
                .map(tag -> MentorTag.builder()
                        .id(new MentorTagId(profile.getUserId(), tag.getId(), MentorTagType.HELP_TOPIC))
                        .mentorProfile(profile)
                        .tag(tag)
                        .build())
                .toList();
        mentorTagRepository.saveAll(mentorTags);
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile) {
        List<MentorTagResponse> helpTopics = mentorTagRepository
                .findByIdMentorUserIdAndIdTagTypeIn(profile.getUserId(), List.of(MentorTagType.HELP_TOPIC))
                .stream()
                .sorted(Comparator.comparing(mentorTag -> mentorTag.getTag().getNameVi()))
                .map(this::mapToTagResponse)
                .toList();
        return mapToResponse(profile, helpTopics);
    }

    private MentorProfileResponse mapToResponseFromTags(MentorProfile profile, List<Tag> helpTopicsOverride) {
        List<MentorTagResponse> helpTopics = helpTopicsOverride.stream()
                .map(this::mapToTagResponseFromTag)
                .sorted(Comparator.comparing(MentorTagResponse::nameVi))
                .toList();
        return mapToResponse(profile, helpTopics);
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile, List<MentorTagResponse> helpTopics) {
        User user = profile.getUser();
        List<MentorSubjectResultResponse> subjectResults = loadSubjectResults(profile.getUserId());
        List<MentorFeaturedProjectResponse> featuredProjects = loadFeaturedProjects(profile.getUserId());
        List<MentorAchievementResponse> achievements = loadAchievements(profile.getUserId());
        return MentorProfileResponse.builder()
                .exists(true)
                .requiredFieldsCompleted(isRequiredFieldsCompleted(profile, helpTopics))
                .userId(profile.getUserId())
                .email(user.getEmail())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .mentorStatus(profile.getStatus())
                .headline(profile.getHeadline())
                .expertiseDescription(profile.getExpertiseDescription())
                .isAvailable(profile.isAvailable())
                .verifiedAt(profile.getVerifiedAt())
                .helpTopics(helpTopics)
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

    private MentorTagResponse mapToTagResponseFromTag(Tag tag) {
        return MentorTagResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .type(tag.getType())
                .primary(false)
                .build();
    }

    private MentorTagResponse mapToTagResponse(MentorTag mentorTag) {
        Tag tag = mentorTag.getTag();
        return MentorTagResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .type(tag.getType())
                .primary(mentorTag.isPrimary())
                .build();
    }

    private boolean isRequiredFieldsCompleted(MentorProfile profile, List<MentorTagResponse> helpTopics) {
        return hasText(profile.getHeadline())
                && hasText(profile.getExpertiseDescription())
                && hasText(profile.getPhoneNumber())
                && !helpTopics.isEmpty()
                && SUPPORT_LEVELS.contains(profile.getFoundationSupportLevel())
                && SUPPORT_LEVELS.contains(profile.getOutputReviewSupportLevel())
                && SUPPORT_LEVELS.contains(profile.getDirectionSupportLevel())
                && !mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(profile.getUserId()).isEmpty();
    }

    private Integer validateSupportLevel(Integer level, String label) {
        if (!SUPPORT_LEVELS.contains(level)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mức hỗ trợ " + label + " chỉ được chọn từ 1 đến 4");
        }
        return level;
    }

    private void replaceSubjectResults(MentorProfile profile, List<MentorSubjectResultRequest> subjectResults) {
        if (subjectResults == null || subjectResults.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Danh sách môn - điểm không được để trống");
        }
        mentorSubjectResultRepository.deleteByMentorProfileUserId(profile.getUserId());
        List<MentorSubjectResult> entities = new java.util.ArrayList<>();
        int displayOrder = 0;
        Set<String> seenCodes = new LinkedHashSet<>();
        for (MentorSubjectResultRequest request : subjectResults) {
            String subjectCode = clean(request.subjectCode()).toUpperCase(java.util.Locale.ROOT);
            if (!seenCodes.add(subjectCode)) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Mã môn không được trùng lặp: " + subjectCode);
            }
            BigDecimal scoreValue = request.scoreValue();
            if (scoreValue == null || scoreValue.compareTo(BigDecimal.ZERO) < 0 || scoreValue.compareTo(BigDecimal.TEN) > 0) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "Điểm môn phải từ 0 đến 10");
            }
            entities.add(MentorSubjectResult.builder()
                    .mentorProfile(profile)
                    .subjectCode(subjectCode)
                    .subjectName(cleanNullable(request.subjectName()))
                    .scoreValue(scoreValue)
                    .displayOrder(displayOrder++)
                    .build());
        }
        mentorSubjectResultRepository.saveAll(entities);
    }

    private String buildLegacySubjectSummary(List<MentorSubjectResultRequest> subjectResults) {
        if (subjectResults == null || subjectResults.isEmpty()) {
            return null;
        }
        return subjectResults.stream()
                .map(subject -> {
                    String code = subject.subjectCode() == null ? "" : subject.subjectCode().trim().toUpperCase(java.util.Locale.ROOT);
                    String name = subject.subjectName() == null ? "" : subject.subjectName().trim();
                    return hasText(name) ? code + " - " + name : code;
                })
                .filter(this::hasText)
                .collect(Collectors.joining(", "));
    }

    private List<MentorSubjectResultResponse> loadSubjectResults(UUID mentorUserId) {
        return mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::mapSubjectResultResponse)
                .toList();
    }

    private List<MentorFeaturedProjectResponse> loadFeaturedProjects(UUID mentorUserId) {
        return mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::mapFeaturedProjectResponse)
                .toList();
    }

    private List<MentorAchievementResponse> loadAchievements(UUID mentorUserId) {
        return mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::mapAchievementResponse)
                .toList();
    }

    private MentorSubjectResultResponse mapSubjectResultResponse(MentorSubjectResult subjectResult) {
        return MentorSubjectResultResponse.builder()
                .id(subjectResult.getId())
                .subjectCode(subjectResult.getSubjectCode())
                .subjectName(subjectResult.getSubjectName())
                .scoreValue(subjectResult.getScoreValue())
                .displayOrder(subjectResult.getDisplayOrder())
                .build();
    }

    private MentorFeaturedProjectResponse mapFeaturedProjectResponse(MentorFeaturedProject project) {
        return MentorFeaturedProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .pictureUrl(project.getPictureFile() == null ? null : project.getPictureFile().getPublicUrl())
                .content(project.getContent())
                .projectDescription(project.getProjectDescription())
                .liveDemoUrl(project.getLiveDemoUrl())
                .displayOrder(project.getDisplayOrder())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private MentorAchievementResponse mapAchievementResponse(MentorAchievement achievement) {
        return MentorAchievementResponse.builder()
                .id(achievement.getId())
                .title(achievement.getTitle())
                .awardDescription(achievement.getAwardDescription())
                .achievedAt(achievement.getAchievedAt())
                .productHeader(achievement.getProductHeader())
                .productDescription(achievement.getProductDescription())
                .demoUrl(achievement.getDemoUrl())
                .displayOrder(achievement.getDisplayOrder())
                .createdAt(achievement.getCreatedAt())
                .updatedAt(achievement.getUpdatedAt())
                .build();
    }

    private String clean(String value) {
        if (!hasText(value)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu văn bản bắt buộc không được để trống");
        }
        return value.trim();
    }

    private String cleanNullable(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void touchMentorActivity(MentorProfile profile, LocalDateTime activityTime) {
        if (profile == null || activityTime == null) {
            return;
        }
        if (profile.getLastActiveAt() == null || profile.getLastActiveAt().isBefore(activityTime)) {
            profile.setLastActiveAt(activityTime);
        }
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }

    private void requireProfileRequest(MentorProfileUpsertRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu hồ sơ mentor không được để trống");
        }
    }
}
