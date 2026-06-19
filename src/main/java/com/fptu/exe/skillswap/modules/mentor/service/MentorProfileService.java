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
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Set<Integer> ALLOWED_SESSION_DURATIONS = Set.of(15, 30, 60, 90);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
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
        profile.setSupportingSubjects(cleanNullable(request.supportingSubjects()));
        profile.setPhoneNumber(clean(request.phoneNumber()));
        if (request.isAvailable() != null) {
            boolean wasAvailable = profile.isAvailable();
            boolean nowAvailable = request.isAvailable();
            profile.setAvailable(nowAvailable);
            
            if (wasAvailable && !nowAvailable) {
                bookingService.rejectAllPendingBookingsForMentor(userId, "Mentor đã chuyển sang trạng thái không nhận lịch");
            }
        }
        profile.setTeachingMode(request.teachingMode());
        profile.setSessionDuration(validateSessionDuration(request.sessionDuration()));
        profile.setLinkedinUrl(cleanNullable(request.linkedinUrl()));
        profile.setGithubUrl(cleanNullable(request.githubUrl()));
        profile.setPortfolioUrl(cleanNullable(request.portfolioUrl()));

        MentorProfile savedProfile = mentorProfileRepository.save(profile);
        replaceHelpTopics(savedProfile, helpTopics);
        return mapToResponseFromTags(savedProfile, helpTopics);
    }

    private MentorProfile getOrCreateProfile(UUID userId) {
        requireUserId(userId);
        return mentorProfileRepository.findWithUserByUserId(userId)
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
                .supportingSubjects(profile.getSupportingSubjects())
                .isAvailable(profile.isAvailable())
                .verifiedAt(profile.getVerifiedAt())
                .helpTopics(helpTopics)
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .phoneNumber(profile.getPhoneNumber())
                .teachingMode(profile.getTeachingMode())
                .sessionDuration(profile.getSessionDuration())
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
                && profile.getTeachingMode() != null
                && profile.getSessionDuration() != null;
    }

    private Integer validateSessionDuration(Integer sessionDuration) {
        if (!ALLOWED_SESSION_DURATIONS.contains(sessionDuration)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thời lượng mentoring chỉ được chọn một trong các giá trị: 15, 30, 60 hoặc 90 phút");
        }
        return sessionDuration;
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

    private void requireProfileRequest(MentorProfileUpsertRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu hồ sơ mentor không được để trống");
        }
    }
}
