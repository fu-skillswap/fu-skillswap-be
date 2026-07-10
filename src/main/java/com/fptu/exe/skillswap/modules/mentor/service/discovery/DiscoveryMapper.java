package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DiscoveryMapper {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PaymentProperties paymentProperties;

    public MentorRecommendationResponse toRecommendation(
            MentorDiscoveryQueryRow candidate,
            MentorEnrichedData enrichedData,
            DiscoveryRankingService.RecommendationScore recommendationScore
    ) {
        return MentorRecommendationResponse.builder()
                .mentor(toCardResponseFromEnriched(candidate, enrichedData, null))
                .matchScore(recommendationScore.matchScore())
                .matchReasons(recommendationScore.matchReasons())
                .build();
    }

    public MentorDiscoveryCardResponse toCardResponseFromEnriched(
            MentorDiscoveryQueryRow row,
            MentorEnrichedData enrichedData,
            BigDecimal matchScore
    ) {
        return toCardResponse(
                row,
                enrichedData.helpTopics(),
                matchScore,
                enrichedData.subjectResults(),
                enrichedData.featuredProjects(),
                enrichedData.achievements()
        );
    }

    public MentorDiscoveryCardResponse toCardResponse(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopicTags,
            BigDecimal matchScore,
            List<MentorSubjectResultResponse> subjectResults,
            List<MentorFeaturedProjectResponse> featuredProjects,
            List<MentorAchievementResponse> achievements
    ) {
        BigDecimal rating = defaultDecimal(row.ratingAverage());
        int reviews = defaultInteger(row.reviewCount());
        BigDecimal displayRating = reviews == 0 ? BigDecimal.valueOf(5.0).setScale(2, RoundingMode.HALF_UP) : rating;
        return MentorDiscoveryCardResponse.builder()
                .mentorUserId(row.mentorUserId())
                .displayName(row.displayName())
                .avatarUrl(row.avatarUrl())
                .headline(row.headline())
                .expertiseDescription(row.expertiseDescription())
                .subjectResults(subjectResults)
                .foundationSupportLevel(row.foundationSupportLevel())
                .outputReviewSupportLevel(row.outputReviewSupportLevel())
                .directionSupportLevel(row.directionSupportLevel())
                .featuredProjects(featuredProjects.stream().limit(2).toList())
                .achievements(achievements.stream().limit(2).toList())
                .isAvailable(row.isAvailable())
                .ratingAverage(displayRating)
                .reviewCount(reviews)
                .completedSessions(defaultInteger(row.completedSessions()))
                .verifiedAt(row.verifiedAt())
                .campusId(row.campusId())
                .campusName(row.campusName())
                .programId(row.programId())
                .programName(row.programName())
                .specializationId(row.specializationId())
                .specializationName(row.specializationName())
                .matchScore(matchScore)
                .helpTopicTags(helpTopicTags.stream().limit(5).toList())
                .build();
    }

    public MentorServiceResponse toServiceResponse(MentorService mentorService) {
        List<MentorTagResponse> helpTopics = mentorService.getHelpTopics().stream()
                .sorted(Comparator.comparing(tag -> tag.getNameVi() == null ? "" : tag.getNameVi()))
                .map(tag -> MentorTagResponse.builder()
                        .id(tag.getId())
                        .code(tag.getCode())
                        .nameVi(tag.getNameVi())
                        .nameEn(tag.getNameEn())
                        .type(tag.getType())
                        .primary(false)
                        .build())
                .toList();

        return MentorServiceResponse.builder()
                .serviceId(mentorService.getId())
                .mentorUserId(mentorService.getMentorProfile() == null ? null : mentorService.getMentorProfile().getUserId())
                .title(mentorService.getTitle())
                .description(mentorService.getDescription())
                .durationMinutes(mentorService.getDurationMinutes())
                .free(mentorService.isFree())
                .priceScoin(mentorService.isFree() || defaultInteger(mentorService.getPriceScoin()) == 0 ? 0 : defaultInteger(mentorService.getPriceScoin()) + (defaultInteger(mentorService.getPriceScoin()) * (paymentProperties == null ? 1000 : paymentProperties.getMenteeSurchargeBps())) / 10_000)
                .active(mentorService.isActive())
                .helpTopics(helpTopics)
                .createdAt(mentorService.getCreatedAt())
                .updatedAt(mentorService.getUpdatedAt())
                .build();
    }

    public MentorReviewResponse toMentorReviewResponse(MentorReviewQueryRow row) {
        return MentorReviewResponse.builder()
                .reviewId(row.reviewId())
                .reviewerUserId(row.reviewerUserId())
                .reviewerDisplayName(row.reviewerDisplayName())
                .reviewerAvatarUrl(row.reviewerAvatarUrl())
                .rating(row.rating())
                .comment(row.comment())
                .createdAt(row.createdAt())
                .build();
    }

    public List<MentorTagResponse> filterTagsByType(List<MentorTagResponse> tags, MentorTagType tagType) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<com.fptu.exe.skillswap.modules.catalog.domain.TagType> acceptedTypes = switch (tagType) {
            case EXPERTISE -> EnumSet.noneOf(com.fptu.exe.skillswap.modules.catalog.domain.TagType.class);
            case HELP_TOPIC -> EnumSet.of(com.fptu.exe.skillswap.modules.catalog.domain.TagType.HELP_TOPIC);
        };
        return tags.stream()
                .filter(tag -> tag.type() != null && acceptedTypes.contains(tag.type()))
                .toList();
    }

    public static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
