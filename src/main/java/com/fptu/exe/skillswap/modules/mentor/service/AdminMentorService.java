package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorListRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminMentorService {

    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";

    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorListItemResponse> getMentors(AdminMentorListRequest request) {
        AdminMentorListRequest safeRequest = request == null ? new AdminMentorListRequest() : request;
        Page<AdminMentorListItemResponse> page = mentorProfileRepository.searchForAdmin(
                buildKeywordPattern(safeRequest.getKeyword()),
                buildNormalizedKeywordPattern(safeRequest.getKeyword()),
                ACCENTED_CHARACTERS,
                PLAIN_CHARACTERS,
                safeRequest.getStatus(),
                safeRequest.getIsAvailable(),
                adminMentorPageable(safeRequest)
        );

        return PageResponse.<AdminMentorListItemResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminMentorDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorProfile profile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new com.fptu.exe.skillswap.shared.exception.BaseException(
                        com.fptu.exe.skillswap.shared.exception.ErrorCode.NOT_FOUND,
                        "Không tìm thấy thông tin mentor"
                ));

        User user = profile.getUser();
        String primaryLabel = studentProfileRepository.findWithDetailsByUserId(mentorUserId)
                .map(sp -> sp.getProgram() == null ? null : sp.getProgram().getCode())
                .orElse(null);
        List<MentorTagResponse> helpTopics = mentorTagRepository == null
                ? List.of()
                : mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(mentorUserId, List.of(MentorTagType.HELP_TOPIC))
                        .stream()
                        .sorted(Comparator.comparing(mentorTag -> mentorTag.getTag().getNameVi() == null ? "" : mentorTag.getTag().getNameVi()))
                        .map(this::toTagResponse)
                        .toList();
        List<MentorSubjectResultResponse> subjectResults = mentorSubjectResultRepository == null
                ? List.of()
                : mentorSubjectResultRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                        .stream()
                        .map(this::toSubjectResultResponse)
                        .toList();
        List<MentorFeaturedProjectResponse> featuredProjects = mentorFeaturedProjectRepository == null
                ? List.of()
                : mentorFeaturedProjectRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                        .stream()
                        .map(this::toFeaturedProjectResponse)
                        .toList();
        List<MentorAchievementResponse> achievements = mentorAchievementRepository == null
                ? List.of()
                : mentorAchievementRepository.findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(mentorUserId)
                        .stream()
                        .map(this::toAchievementResponse)
                        .toList();

        return AdminMentorDetailResponse.builder()
                .mentorUserId(profile.getUserId())
                .email(user == null ? null : user.getEmail())
                .displayName(user == null ? null : user.getFullName())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .phoneNumber(profile.getPhoneNumber())
                .userStatus(user == null ? null : user.getStatus())
                .mentorStatus(profile.getStatus())
                .isAvailable(profile.isAvailable())
                .bookingSuspendedUntil(profile.getBookingSuspendedUntil())
                .headline(profile.getHeadline())
                .expertiseDescription(profile.getExpertiseDescription())
                .subjectResults(subjectResults)
                .foundationSupportLevel(profile.getFoundationSupportLevel())
                .outputReviewSupportLevel(profile.getOutputReviewSupportLevel())
                .directionSupportLevel(profile.getDirectionSupportLevel())
                .helpTopics(helpTopics)
                .featuredProjects(featuredProjects)
                .achievements(achievements)
                .supportingSubjects(profile.getSupportingSubjects())
                .teachingMode(profile.getTeachingMode())
                .sessionDuration(profile.getSessionDuration())
                .ratingAverage(profile.getAverageRating())
                .reviewCount(profile.getTotalReviews())
                .completedSessions(profile.getTotalCompletedSessions())
                .rejectedBookings(profile.getTotalRejectedBookings())
                .lateCancellationPenaltyPoints(profile.getLateCancellationPenaltyPoints())
                .portfolioUrl(profile.getPortfolioUrl())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .primaryLabel(primaryLabel)
                .verifiedAt(profile.getVerifiedAt())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private MentorTagResponse toTagResponse(MentorTag mentorTag) {
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

    private MentorSubjectResultResponse toSubjectResultResponse(MentorSubjectResult subjectResult) {
        return MentorSubjectResultResponse.builder()
                .id(subjectResult.getId())
                .subjectCode(subjectResult.getSubjectCode())
                .subjectName(subjectResult.getSubjectName())
                .scoreValue(subjectResult.getScoreValue())
                .displayOrder(subjectResult.getDisplayOrder())
                .build();
    }

    private MentorFeaturedProjectResponse toFeaturedProjectResponse(MentorFeaturedProject project) {
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

    private MentorAchievementResponse toAchievementResponse(MentorAchievement achievement) {
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

    private Pageable adminMentorPageable(AdminMentorListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = switch (request.getSortBy() == null ? "" : request.getSortBy()) {
            case "verifiedAt" -> "verifiedAt";
            case "averageRating" -> "averageRating";
            case "totalReviews" -> "totalReviews";
            case "totalCompletedSessions", "completedSessions" -> "totalCompletedSessions";
            case "status" -> "status";
            case "createdAt" -> "createdAt";
            default -> "updatedAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String buildKeywordPattern(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ") + "%";
    }

    private String buildNormalizedKeywordPattern(String keyword) {
        String normalized = normalizeSearchText(keyword);
        if (normalized == null) {
            return null;
        }
        return "%" + normalized + "%";
    }

    private String normalizeSearchText(String keyword) {
        String normalized = normalizeKeyword(keyword);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
