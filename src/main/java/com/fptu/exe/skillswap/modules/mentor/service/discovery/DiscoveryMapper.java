package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRatingState;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.payment.service.PricingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                matchScore,
                enrichedData.subjectResults(),
                enrichedData.featuredProjects(),
                enrichedData.achievements()
        );
    }

    public MentorDiscoveryCardResponse toCardResponse(
            MentorDiscoveryQueryRow row,
            BigDecimal matchScore,
            List<MentorSubjectResultResponse> subjectResults,
            List<MentorFeaturedProjectResponse> featuredProjects,
            List<MentorAchievementResponse> achievements
    ) {
        int reviews = defaultInteger(row.reviewCount());
        return MentorDiscoveryCardResponse.builder()
                .identity(new MentorDiscoveryCardResponse.Identity(
                        row.mentorUserId(), row.displayName(), row.avatarUrl(), row.headline(),
                        row.verifiedAt() != null, row.verifiedAt()))
                .mentoring(new MentorDiscoveryCardResponse.Mentoring(
                        row.expertiseDescription(), row.foundationSupportLevel(),
                        row.outputReviewSupportLevel(), row.directionSupportLevel()))
                .evidence(new MentorDiscoveryCardResponse.Evidence(
                        row.campusId(), row.campusName(), row.programId(), row.programName(),
                        row.specializationId(), row.specializationName(),
                        subjectResults == null ? List.of() : subjectResults.stream().limit(2).toList(),
                        featuredProjects == null ? List.of() : featuredProjects.stream().limit(2).toList(),
                        achievements == null ? List.of() : achievements.stream().limit(2).toList()))
                .reputation(new MentorDiscoveryCardResponse.Reputation(
                        reviews == 0 ? MentorRatingState.NO_REVIEWS : MentorRatingState.RATED,
                        reviews == 0 ? null : row.ratingAverage(), reviews, defaultInteger(row.completedSessions())))
                .availability(new MentorDiscoveryCardResponse.Availability(Boolean.TRUE.equals(row.isAvailable())))
                .match(new MentorDiscoveryCardResponse.Match(matchScore))
                .mentorUserId(row.mentorUserId())
                .displayName(row.displayName())
                .ratingState(reviews == 0 ? MentorRatingState.NO_REVIEWS : MentorRatingState.RATED)
                .ratingAverage(reviews == 0 ? null : row.ratingAverage())
                .completedSessions(defaultInteger(row.completedSessions()))
                .matchScore(matchScore)
                .build();
    }

    /**
     * Temporary Java compatibility for callers compiled against the removed HelpTopic argument.
     * The argument is intentionally ignored; the public JSON contract has no help-topic field.
     */
    @Deprecated(forRemoval = true)
    public MentorDiscoveryCardResponse toCardResponse(
            MentorDiscoveryQueryRow row,
            List<?> ignoredHelpTopics,
            BigDecimal matchScore,
            List<?> subjectResults,
            List<?> featuredProjects,
            List<?> achievements
    ) {
        return toCardResponse(
                row,
                matchScore,
                typedItems(subjectResults, MentorSubjectResultResponse.class),
                typedItems(featuredProjects, MentorFeaturedProjectResponse.class),
                typedItems(achievements, MentorAchievementResponse.class)
        );
    }

    public MentorServiceResponse toServiceResponse(MentorService mentorService) {
        return MentorServiceResponse.builder()
                .serviceId(mentorService.getId())
                .mentorUserId(mentorService.getMentorProfile() == null ? null : mentorService.getMentorProfile().getUserId())
                .title(mentorService.getTitle())
                .description(mentorService.getDescription())
                .expectedOutcome(mentorService.getExpectedOutcome())
                .durationMinutes(mentorService.getDurationMinutes())
                .isFree(mentorService.isFree())
                .priceScoin(mentorService.isFree() || defaultInteger(mentorService.getPriceScoin()) == 0 ? 0
                        : PricingPolicy.menteePayableScoin(defaultInteger(mentorService.getPriceScoin()), paymentProperties))
                .isActive(mentorService.isActive())
                .maintainPostSessionChat(mentorService.isMaintainPostSessionChat())
                .deliveryMode(mentorService.getDeliveryMode())
                .version(mentorService.getVersion())
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

    public static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private static <T> List<T> typedItems(List<?> source, Class<T> type) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
