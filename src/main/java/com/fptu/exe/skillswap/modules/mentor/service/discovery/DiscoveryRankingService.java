package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DiscoveryRankingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ACTIVE_SERVICE_BONUS_SCORE = decimal(5);
    private static final BigDecimal HAS_AVAILABILITY_BONUS_SCORE = decimal(15);
    private static final BigDecimal CAPABILITY_MATCH_MULTIPLIER = decimal("10.00");
    private static final BigDecimal MENTOR_FIT_SUBJECT_BONUS = decimal("15.00");
    private static final BigDecimal MENTOR_FIT_ALUMNI_BONUS = decimal("15.00");
    private static final BigDecimal DURATION_PREFERENCE_MATCH_BONUS = decimal("10.00");
    private static final int MAX_RECOMMENDATION_SERVICE_BONUS_COUNT = 3;
    private static final BigDecimal BAYESIAN_PRIOR_RATING = decimal("4.50");
    private static final int BAYESIAN_MIN_REVIEWS = 5;
    private static final BigDecimal RATING_QUALITY_MULTIPLIER = decimal("3.00");
    private static final BigDecimal MAX_REVIEW_VOLUME_SCORE = decimal("5.00");
    private static final BigDecimal MAX_SESSION_VOLUME_SCORE = decimal("5.00");
    private static final BigDecimal ACCEPTANCE_RATE_PRIOR = decimal("0.75");
    private static final int ACCEPTANCE_RATE_PRIOR_DECISIONS = 6;
    private static final BigDecimal MAX_ACCEPTANCE_RATE_SCORE = decimal("6.00");
    private static final BigDecimal CANCELLATION_RELIABILITY_PRIOR = decimal("0.90");
    private static final int CANCELLATION_RELIABILITY_PRIOR_ACCEPTANCES = 4;
    private static final BigDecimal MAX_CANCELLATION_RELIABILITY_SCORE = decimal("4.00");
    private static final BigDecimal RECENT_ACTIVITY_14D_BONUS = decimal("3.00");
    private static final BigDecimal RECENT_ACTIVITY_30D_BONUS = decimal("1.50");
    private static final BigDecimal MAX_RECOMMENDATION_QUALITY_SCORE = decimal(38);
    private static final BigDecimal COLD_START_VERIFIED_BONUS = decimal("6.00");
    private static final BigDecimal STALE_MATCHING_30D_MULTIPLIER = decimal("0.85");
    private static final BigDecimal STALE_MATCHING_90D_MULTIPLIER = decimal("0.60");

    public RecommendationScore scoreRecommendation(
            MentorDiscoveryQueryRow candidate,
            MentorEnrichedData enrichedData,
            StudentProfile menteeProfile,
            LocalDateTime evaluatedAt
    ) {
        List<RecommendationReason> reasons = new ArrayList<>();
        RecommendationScoreBreakdown breakdown = calculateMatchScoreBreakdown(
                candidate,
                enrichedData,
                menteeProfile,
                reasons,
                evaluatedAt
        );
        BigDecimal score = breakdown.totalRawScore();

        BigDecimal maxScore = MAX_RECOMMENDATION_QUALITY_SCORE
                .add(calculateMaxCapabilityScore(evaluatedAt))
                .add(HAS_AVAILABILITY_BONUS_SCORE)
                .add(serviceBonusScore(MAX_RECOMMENDATION_SERVICE_BONUS_COUNT));


        maxScore = maxScore.max(BigDecimal.ONE);
        BigDecimal percentageScore = score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);

        if (defaultInteger(candidate.completedSessions()) > 0) {
            addReason(reasons, RecommendationReasonCode.COMPLETED_SESSION);
        }

        if (reasons.isEmpty()) {
            addReason(reasons, RecommendationReasonCode.DEFAULT_DISCOVERY_MATCH);
        }

        return new RecommendationScore(
                percentageScore,
                reasons.stream()
                        .limit(3)
                        .map(RecommendationReasonTextMapper::toVietnamese)
                        .toList(),
                new RecommendationScoreBreakdown(
                        breakdown.qualityScore(),
                        breakdown.capabilityScore(),
                        breakdown.serviceScore(),
                        breakdown.availabilityScore(),
                        breakdown.durationScore(),
                        breakdown.totalRawScore(),
                        percentageScore
                )
        );
    }

    private RecommendationScoreBreakdown calculateMatchScoreBreakdown(
            MentorDiscoveryQueryRow candidate,
            MentorEnrichedData enrichedData,
            StudentProfile menteeProfile,
            List<RecommendationReason> reasons,
            LocalDateTime evaluatedAt
    ) {

        BigDecimal rating = defaultDecimal(candidate.ratingAverage());
        int reviews = defaultInteger(candidate.reviewCount());
        int completedSessions = defaultInteger(candidate.completedSessions());
        if (rating.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            addReason(reasons, RecommendationReasonCode.HIGH_RATING);
        }
        if (reviews >= 5) {
            addReason(reasons, RecommendationReasonCode.TRUSTED_REVIEW_VOLUME);
        }
        if (completedSessions >= 10) {
            addReason(reasons, RecommendationReasonCode.MENTORING_EXPERIENCE);
        }
        if (calculateAcceptanceRate(candidate).compareTo(decimal("0.80")) >= 0) {
            addReason(reasons, RecommendationReasonCode.STABLE_ACCEPTANCE_RATE);
        }
        if (calculateNonCancellationRate(candidate).compareTo(decimal("0.90")) >= 0) {
            addReason(reasons, RecommendationReasonCode.LOW_CANCELLATION_RATE);
        }
        if (isRecentlyActive(candidate, evaluatedAt)) {
            addReason(reasons, RecommendationReasonCode.RECENT_ACTIVITY);
        }
        BigDecimal qualityScore = calculateSearchQualityScore(candidate, evaluatedAt);
        BigDecimal capabilityScore = calculateCapabilityScore(candidate, menteeProfile, enrichedData.subjectResults(), reasons, evaluatedAt);
        BigDecimal serviceScore = enrichedData.services().isEmpty()
                ? ZERO
                : serviceBonusScore(enrichedData.services().size());
        BigDecimal availabilityScore = enrichedData.hasAvailability() ? HAS_AVAILABILITY_BONUS_SCORE : ZERO;
        BigDecimal durationScore = enrichedData.hasPreferredDurationAvailability()
                ? DURATION_PREFERENCE_MATCH_BONUS
                : ZERO;

        if (!enrichedData.services().isEmpty()) {
            addReason(reasons, RecommendationReasonCode.ACTIVE_SERVICES, enrichedData.services().size());
        }
        if (enrichedData.hasAvailability()) {
            addReason(reasons, RecommendationReasonCode.HAS_AVAILABILITY);
        }
        if (enrichedData.hasPreferredDurationAvailability()) {
            addReason(reasons, RecommendationReasonCode.PREFERRED_DURATION_AVAILABLE);
        }

        BigDecimal totalRawScore = qualityScore
                .add(capabilityScore)
                .add(serviceScore)
                .add(availabilityScore)
                .add(durationScore)
                .setScale(2, RoundingMode.HALF_UP);
        return new RecommendationScoreBreakdown(
                qualityScore,
                capabilityScore,
                serviceScore,
                availabilityScore,
                durationScore,
                totalRawScore,
                ZERO
        );
    }

    private BigDecimal calculateCapabilityScore(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            List<MentorSubjectResultResponse> subjectResults,
            List<RecommendationReason> reasons,
            LocalDateTime evaluatedAt
    ) {
        return ZERO;
    }

    private BigDecimal calculateMaxCapabilityScore(LocalDateTime evaluatedAt) {
        return ZERO;
    }

    private BigDecimal calculatePersonalizationScore(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile
    ) {
        BigDecimal baseScore = ZERO;
        if (menteeProfile == null) {
            return baseScore;
        }

        return ZERO;
    }

    private BigDecimal calculateSearchQualityScore(MentorDiscoveryQueryRow row, LocalDateTime evaluatedAt) {
        BigDecimal score = ZERO;
        BigDecimal rating = defaultDecimal(row.ratingAverage());
        int reviews = defaultInteger(row.reviewCount());
        int completedSessions = defaultInteger(row.completedSessions());

        score = score.add(calculateBayesianRating(rating, reviews).multiply(RATING_QUALITY_MULTIPLIER));
        score = score.add(boundedLogScore(reviews, 10, MAX_REVIEW_VOLUME_SCORE));
        score = score.add(boundedLogScore(completedSessions, 50, MAX_SESSION_VOLUME_SCORE));
        score = score.add(calculateBehaviorScore(row, evaluatedAt));
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBehaviorScore(MentorDiscoveryQueryRow row, LocalDateTime evaluatedAt) {
        BigDecimal score = ZERO;
        score = score.add(calculateAcceptanceRate(row).multiply(MAX_ACCEPTANCE_RATE_SCORE));
        score = score.add(calculateNonCancellationRate(row).multiply(MAX_CANCELLATION_RELIABILITY_SCORE));

        if (row.lastActiveAt() != null) {
            if (!row.lastActiveAt().isBefore(evaluatedAt.minusDays(14))) {
                score = score.add(RECENT_ACTIVITY_14D_BONUS);
            } else if (!row.lastActiveAt().isBefore(evaluatedAt.minusDays(30))) {
                score = score.add(RECENT_ACTIVITY_30D_BONUS);
            }
        }

        if (row.verifiedAt() != null
                && row.verifiedAt().isAfter(evaluatedAt.minusDays(14))
                && defaultInteger(row.completedSessions()) <= 3
                && defaultInteger(row.reviewCount()) <= 2) {
            score = score.add(COLD_START_VERIFIED_BONUS);
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyMatchingRecencyMultiplier(BigDecimal score, LocalDateTime evaluatedAt) {
        return score;
    }

    private BigDecimal calculateAcceptanceRate(MentorDiscoveryQueryRow row) {
        int accepted = defaultInteger(row.acceptedBookingCount());
        int rejected = defaultInteger(row.rejectedBookingCount());
        return calculateBayesianRate(
                accepted,
                accepted + rejected,
                ACCEPTANCE_RATE_PRIOR,
                ACCEPTANCE_RATE_PRIOR_DECISIONS
        );
    }

    private BigDecimal calculateNonCancellationRate(MentorDiscoveryQueryRow row) {
        int accepted = defaultInteger(row.acceptedBookingCount());
        int cancelled = Math.min(defaultInteger(row.mentorCancelledBookingCount()), accepted);
        return calculateBayesianRate(
                Math.max(accepted - cancelled, 0),
                accepted,
                CANCELLATION_RELIABILITY_PRIOR,
                CANCELLATION_RELIABILITY_PRIOR_ACCEPTANCES
        );
    }

    private boolean isRecentlyActive(MentorDiscoveryQueryRow row, LocalDateTime evaluatedAt) {
        return row != null
                && row.lastActiveAt() != null
                && !row.lastActiveAt().isBefore(evaluatedAt.minusDays(30));
    }

    private boolean sameUuid(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private boolean shouldBoostAlumni(MentorDiscoveryQueryRow candidate) {
        return false;
    }

    private boolean hasSubjectMatchSignal(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            List<MentorSubjectResultResponse> subjectResults
    ) {
        return false;
    }

    private BigDecimal levelAlignmentScore(Integer needLevel, Integer supportLevel) {
        if (needLevel == null || supportLevel == null) {
            return ZERO;
        }
        int gap = Math.max(0, needLevel - supportLevel);
        int aligned = Math.max(0, needLevel - gap);
        return CAPABILITY_MATCH_MULTIPLIER.multiply(BigDecimal.valueOf(aligned)).setScale(2, RoundingMode.HALF_UP);
    }

    private void addReason(List<RecommendationReason> reasons, RecommendationReasonCode code) {
        if (reasons != null && code != null && reasons.stream().noneMatch(reason -> reason.code() == code)) {
            reasons.add(RecommendationReason.of(code));
        }
    }

    private void addReason(List<RecommendationReason> reasons, RecommendationReasonCode code, int count) {
        if (reasons != null && code != null && reasons.stream().noneMatch(reason -> reason.code() == code)) {
            reasons.add(RecommendationReason.counted(code, count));
        }
    }

    private BigDecimal calculateBayesianRating(BigDecimal rating, int reviewCount) {
        BigDecimal safeRating = defaultDecimal(rating);
        int boundedReviews = Math.max(reviewCount, 0);
        BigDecimal reviewWeight = BigDecimal.valueOf(boundedReviews);
        BigDecimal minReviews = BigDecimal.valueOf(BAYESIAN_MIN_REVIEWS);
        BigDecimal denominator = reviewWeight.add(minReviews);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BAYESIAN_PRIOR_RATING;
        }
        return safeRating.multiply(reviewWeight)
                .add(BAYESIAN_PRIOR_RATING.multiply(minReviews))
                .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBayesianRate(int positiveCount, int totalCount, BigDecimal priorRate, int priorWeight) {
        int safePositive = Math.max(positiveCount, 0);
        int safeTotal = Math.max(totalCount, 0);
        int safePriorWeight = Math.max(priorWeight, 0);
        if (safeTotal == 0 && safePriorWeight == 0) {
            return ZERO;
        }

        BigDecimal observed = BigDecimal.valueOf(Math.min(safePositive, safeTotal));
        BigDecimal weightedPrior = priorRate.multiply(BigDecimal.valueOf(safePriorWeight));
        BigDecimal denominator = BigDecimal.valueOf(safeTotal + safePriorWeight);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        return observed.add(weightedPrior)
                .divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal boundedLogScore(int value, int saturationPoint, BigDecimal maxScore) {
        if (value <= 0 || saturationPoint <= 0 || maxScore.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        double bounded = Math.min(Math.max(value, 0), saturationPoint);
        double ratio = Math.log1p(bounded) / Math.log1p(saturationPoint);
        return maxScore.multiply(BigDecimal.valueOf(ratio)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal serviceBonusScore(int activeServiceCount) {
        int cappedServiceCount = Math.min(Math.max(activeServiceCount, 0), MAX_RECOMMENDATION_SERVICE_BONUS_COUNT);
        return ACTIVE_SERVICE_BONUS_SCORE.multiply(BigDecimal.valueOf(cappedServiceCount));
    }

    public static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }


    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record RecommendationScore(
            BigDecimal matchScore,
            List<String> matchReasons,
            RecommendationScoreBreakdown breakdown
    ) {
        public RecommendationScore(BigDecimal matchScore, List<String> matchReasons) {
            this(matchScore, matchReasons, RecommendationScoreBreakdown.empty());
        }
    }
}
