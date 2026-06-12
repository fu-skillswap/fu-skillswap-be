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
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileBasicRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileExpertiseRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
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

    private static final Set<TagType> EXPERTISE_TAG_TYPES = EnumSet.of(
            TagType.SPECIALIZATION,
            TagType.TECH_SKILL,
            TagType.BUSINESS_SKILL,
            TagType.LANGUAGE,
            TagType.CAREER,
            TagType.SOFT_SKILL,
            TagType.TOOL,
            TagType.INDUSTRY
    );

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MentorProfileResponse getMyProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserId(userId)
                .map(this::mapToResponse)
                .orElseGet(() -> MentorProfileResponse.empty(userId));
    }

    @Transactional
    public MentorProfileResponse upsertBasic(UUID userId, MentorProfileBasicRequest request) {
        requireUserId(userId);
        requireBasicRequest(request);
        MentorProfile profile = getOrCreateProfile(userId);
        profile.setHeadline(clean(request.headline()));
        profile.setCurrentPosition(clean(request.currentPosition()));
        profile.setCurrentCompany(clean(request.currentCompany()));
        profile.setBio(clean(request.bio()));
        profile.setAvailable(request.isAvailable());
        profile.getUser().setAvatarUrl(clean(request.avatarUrl()));

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        return mapToResponse(savedProfile);
    }

    @Transactional
    public MentorProfileResponse upsertExpertise(UUID userId, MentorProfileExpertiseRequest request) {
        requireUserId(userId);
        requireExpertiseRequest(request);
        MentorProfile profile = getOrCreateProfile(userId);
        List<Tag> expertiseTags = loadAndValidateTags(request.expertiseTagIds(), EXPERTISE_TAG_TYPES, "tag chuyên môn");
        List<Tag> helpTopics = loadAndValidateTags(request.helpTopicIds(), Set.of(TagType.HELP_TOPIC), "chủ đề hỗ trợ");

        profile.setYearsOfExperience(request.yearsOfExperience());
        profile.setIndustry(clean(request.industry()));
        profile.setExpertiseSummary(cleanNullable(request.expertiseSummary()));
        profile.setLinkedinUrl(cleanNullable(request.linkedinUrl()));
        profile.setGithubUrl(cleanNullable(request.githubUrl()));
        profile.setPortfolioUrl(cleanNullable(request.portfolioUrl()));

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        replaceMentorTags(savedProfile, expertiseTags, helpTopics);
        return mapToResponse(savedProfile, expertiseTags, helpTopics);
    }

    private MentorProfile getOrCreateProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
                    MentorProfile profile = new MentorProfile();
                    profile.setUser(user);
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

    private void replaceMentorTags(MentorProfile profile, List<Tag> expertiseTags, List<Tag> helpTopics) {
        mentorTagRepository.deleteByIdMentorUserId(profile.getUserId());

        List<MentorTag> mentorTags = new java.util.ArrayList<>(expertiseTags.size() + helpTopics.size());
        mentorTags.addAll(toMentorTags(profile, expertiseTags, MentorTagType.EXPERTISE));
        mentorTags.addAll(toMentorTags(profile, helpTopics, MentorTagType.HELP_TOPIC));
        mentorTagRepository.saveAll(mentorTags);
    }

    private List<MentorTag> toMentorTags(MentorProfile profile, List<Tag> tags, MentorTagType mentorTagType) {
        return tags.stream()
                .map(tag -> MentorTag.builder()
                        .id(new MentorTagId(profile.getUserId(), tag.getId(), mentorTagType))
                        .mentorProfile(profile)
                        .tag(tag)
                        .build())
                .toList();
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile) {
        return mapToResponse(profile, null, null);
    }

    private MentorProfileResponse mapToResponse(MentorProfile profile, List<Tag> expertiseTagsOverride, List<Tag> helpTopicsOverride) {
        Map<MentorTagType, List<MentorTagResponse>> tagsByType = expertiseTagsOverride != null && helpTopicsOverride != null
                ? Map.of(
                        MentorTagType.EXPERTISE, expertiseTagsOverride.stream().map(this::mapToTagResponseFromTag).sorted(Comparator.comparing(MentorTagResponse::nameVi)).toList(),
                        MentorTagType.HELP_TOPIC, helpTopicsOverride.stream().map(this::mapToTagResponseFromTag).sorted(Comparator.comparing(MentorTagResponse::nameVi)).toList()
                )
                : mentorTagRepository
                .findByIdMentorUserIdAndIdTagTypeIn(profile.getUserId(), List.of(MentorTagType.EXPERTISE, MentorTagType.HELP_TOPIC))
                .stream()
                .sorted(Comparator.comparing(mentorTag -> mentorTag.getTag().getNameVi()))
                .collect(Collectors.groupingBy(
                        MentorTag::getTagType,
                        Collectors.mapping(this::mapToTagResponse, Collectors.toList())
                ));

        User user = profile.getUser();
        List<MentorTagResponse> expertiseTags = tagsByType.getOrDefault(MentorTagType.EXPERTISE, List.of());
        List<MentorTagResponse> helpTopics = tagsByType.getOrDefault(MentorTagType.HELP_TOPIC, List.of());
        return MentorProfileResponse.builder()
                .exists(true)
                .requiredFieldsCompleted(isRequiredFieldsCompleted(profile, expertiseTags, helpTopics))
                .userId(profile.getUserId())
                .email(user.getEmail())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .mentorStatus(profile.getStatus())
                .headline(profile.getHeadline())
                .currentPosition(profile.getCurrentPosition())
                .currentCompany(profile.getCurrentCompany())
                .isAvailable(profile.isAvailable())
                .verifiedAt(profile.getVerifiedAt())
                .bio(profile.getBio())
                .expertiseSummary(profile.getExpertiseSummary())
                .expertiseTags(expertiseTags)
                .helpTopics(helpTopics)
                .yearsOfExperience(profile.getYearsOfExperience())
                .industry(profile.getIndustry())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .teachingMode(profile.getTeachingMode())
                .sessionDuration(profile.getSessionDuration())
                .hourlyRate(profile.getHourlyRate())
                .mentoringStyle(profile.getMentoringStyle())
                .targetMentees(profile.getTargetMentees())
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

    private boolean isRequiredFieldsCompleted(
            MentorProfile profile,
            List<MentorTagResponse> expertiseTags,
            List<MentorTagResponse> helpTopics
    ) {
        return hasText(profile.getHeadline())
                && hasText(profile.getCurrentPosition())
                && hasText(profile.getCurrentCompany())
                && hasText(profile.getUser().getAvatarUrl())
                && hasText(profile.getBio())
                && !expertiseTags.isEmpty()
                && !helpTopics.isEmpty()
                && profile.getYearsOfExperience() != null
                && hasText(profile.getIndustry())
                && profile.getTeachingMode() != null
                && profile.getSessionDuration() != null
                && profile.getHourlyRate() != null;
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

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }

    private void requireBasicRequest(MentorProfileBasicRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu hồ sơ mentor không được để trống");
        }
    }

    private void requireExpertiseRequest(MentorProfileExpertiseRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu chuyên môn mentor không được để trống");
        }
    }
}
