package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiscoveryRankingService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal SAME_PROGRAM_SCORE = decimal(25);
    private static final BigDecimal SAME_SPECIALIZATION_SCORE = decimal(20);
    private static final BigDecimal SAME_CAMPUS_SCORE = decimal(5);
    private static final BigDecimal MENTOR_ALUMNI_SCORE = decimal(10);
    private static final BigDecimal MENTOR_HIGHER_SEMESTER_SCORE = decimal(15);
    private static final BigDecimal MENTOR_EQUAL_SEMESTER_SCORE = decimal(10);
    private static final BigDecimal HEADLINE_EXACT_BONUS = decimal(35);
    private static final BigDecimal VERIFIED_RECENT_7D_BONUS = decimal(10);
    private static final BigDecimal VERIFIED_RECENT_30D_BONUS = decimal(5);
    private static final BigDecimal SESSION_VELOCITY_HIGH_BONUS = decimal(10);
    private static final BigDecimal SESSION_VELOCITY_MED_BONUS = decimal(5);
    private static final BigDecimal ACTIVE_SERVICE_BONUS_SCORE = decimal(5);
    private static final BigDecimal HAS_AVAILABILITY_BONUS_SCORE = decimal(15);
    private static final BigDecimal CAPABILITY_MATCH_MULTIPLIER = decimal("10.00");
    private static final BigDecimal MENTOR_FIT_SUBJECT_BONUS = decimal("15.00");
    private static final BigDecimal MENTOR_FIT_ALUMNI_BONUS = decimal("15.00");
    private static final BigDecimal DURATION_PREFERENCE_MATCH_BONUS = decimal("10.00");
    private static final BigDecimal MAX_SEARCH_PERSONALIZATION_SCORE = decimal(75);
    private static final BigDecimal MAX_SEARCH_QUALITY_SCORE = decimal(38);
    private static final BigDecimal MAX_PERCENTAGE_SCORE = decimal(100);
    private static final int MAX_SEARCH_SERVICE_BONUS_COUNT = 3;
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

    private final DiscoveryKeywordSupport keywordSupport;

    public List<RankedSearchCandidate> rankSearchCandidates(
            List<MentorDiscoveryQueryRow> rows,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            String normalizedKeyword,
            java.util.Map<UUID, MentorEnrichedData> enrichedDataByMentor
    ) {
        return rows.stream()
                .map(row -> {
                    MentorEnrichedData enrichedData = enrichedDataByMentor.getOrDefault(row.mentorUserId(), MentorEnrichedData.empty());
                    BigDecimal rawScore = calculateSearchScore(
                            row,
                            menteeProfile,
                            menteeFeatures,
                            normalizedKeyword,
                            enrichedData
                    );
                    return new RankedSearchCandidate(
                            row,
                            enrichedData,
                            rawScore,
                            calculateSearchScorePercentage(rawScore, normalizedKeyword, enrichedData.services().size(), menteeFeatures != null && menteeFeatures.durationPreferenceCode() != null, menteeFeatures)
                    );
                })
                .sorted(Comparator
                        .comparing(RankedSearchCandidate::score).reversed()
                        .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                        .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.row().verifiedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(candidate -> candidate.row().mentorUserId()))
                .toList();
    }

    public List<MentorDiscoveryQueryRow> sortRowsForRequestedSort(
            List<MentorDiscoveryQueryRow> rows,
            String sortBy,
            Sort.Direction direction
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Comparator<MentorDiscoveryQueryRow> comparator = switch (sortBy) {
            case "ratingAverage" -> Comparator
                    .comparing(MentorDiscoveryQueryRow::ratingAverage, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::completedSessions, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::verifiedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::mentorUserId);
            case "reviewCount" -> Comparator
                    .comparing(MentorDiscoveryQueryRow::reviewCount, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::ratingAverage, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::completedSessions, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::mentorUserId);
            case "completedSessions" -> Comparator
                    .comparing(MentorDiscoveryQueryRow::completedSessions, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::ratingAverage, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::mentorUserId);
            case "updatedAt" -> Comparator
                    .comparing(MentorDiscoveryQueryRow::verifiedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::ratingAverage, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::mentorUserId);
            default -> Comparator
                    .comparing(MentorDiscoveryQueryRow::ratingAverage, Comparator.nullsLast(BigDecimal::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::completedSessions, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::verifiedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                    .thenComparing(MentorDiscoveryQueryRow::mentorUserId);
        };

        if (direction != Sort.Direction.ASC) {
            comparator = comparator.reversed();
        }

        return rows.stream().sorted(comparator).toList();
    }

    public RecommendationScore scoreRecommendation(
            MentorDiscoveryQueryRow candidate,
            MentorEnrichedData enrichedData,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures
    ) {
        List<String> reasons = new ArrayList<>();
        BigDecimal score = calculateMatchScore(
                candidate,
                enrichedData,
                menteeProfile,
                menteeFeatures,
                reasons
        );

        BigDecimal maxScore = SAME_PROGRAM_SCORE
                .add(SAME_SPECIALIZATION_SCORE)
                .add(SAME_CAMPUS_SCORE)
                .add(MENTOR_ALUMNI_SCORE.max(MENTOR_HIGHER_SEMESTER_SCORE))
                .add(MAX_RECOMMENDATION_QUALITY_SCORE)
                .add(calculateMaxCapabilityScore(menteeFeatures))
                .add(HAS_AVAILABILITY_BONUS_SCORE)
                .add(serviceBonusScore(MAX_SEARCH_SERVICE_BONUS_COUNT));
        if (menteeFeatures != null && menteeFeatures.durationPreferenceCode() != null) {
            maxScore = maxScore.add(DURATION_PREFERENCE_MATCH_BONUS);
        }

        maxScore = maxScore.max(BigDecimal.ONE);
        BigDecimal percentageScore = score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);

        if (defaultInteger(candidate.completedSessions()) > 0 && reasons.stream().noneMatch("Đã có phiên mentoring hoàn thành"::equals)) {
            reasons.add("Đã có phiên mentoring hoàn thành");
        }

        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với các tiêu chí discovery hiện tại");
        }

        return new RecommendationScore(percentageScore, reasons.stream().limit(3).toList());
    }

    private BigDecimal calculateMatchScore(
            MentorDiscoveryQueryRow candidate,
            MentorEnrichedData enrichedData,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            List<String> reasons
    ) {
        BigDecimal baseScore = ZERO;

        if (menteeProfile != null) {
            if (sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId())) {
                baseScore = baseScore.add(SAME_PROGRAM_SCORE);
                reasons.add("Cùng chương trình học");
            }
            if (sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId())) {
                baseScore = baseScore.add(SAME_SPECIALIZATION_SCORE);
                reasons.add("Cùng chuyên ngành với mentee");
            }
            if (sameUuid(menteeProfile.getCampus() == null ? null : menteeProfile.getCampus().getId(), candidate.campusId())) {
                baseScore = baseScore.add(SAME_CAMPUS_SCORE);
                reasons.add("Cùng campus");
            }
            if (Boolean.TRUE.equals(candidate.alumni()) && shouldBoostAlumni(candidate, menteeFeatures)) {
                baseScore = baseScore.add(MENTOR_ALUMNI_SCORE);
                reasons.add("Mentor là cựu sinh viên");
            } else {
                Integer menteeSemester = menteeProfile.getSemester();
                Integer mentorSemester = candidate.semester();
                if (menteeSemester != null && mentorSemester != null) {
                    if (mentorSemester > menteeSemester) {
                        baseScore = baseScore.add(MENTOR_HIGHER_SEMESTER_SCORE);
                        reasons.add("Mentor đi trước mentee về học kỳ");
                    } else if (mentorSemester.equals(menteeSemester)) {
                        baseScore = baseScore.add(MENTOR_EQUAL_SEMESTER_SCORE);
                        reasons.add("Mentor cùng học kỳ chuyên ngành với mentee");
                    }
                }
            }
        }

        BigDecimal rating = defaultDecimal(candidate.ratingAverage());
        int reviews = defaultInteger(candidate.reviewCount());
        int completedSessions = defaultInteger(candidate.completedSessions());
        if (rating.compareTo(BigDecimal.valueOf(4.5)) >= 0) {
            reasons.add("Được đánh giá cao từ mentee");
        }
        if (reviews >= 5) {
            reasons.add("Có lượng đánh giá đủ tin cậy");
        }
        if (completedSessions >= 10) {
            reasons.add("Đã có kinh nghiệm mentoring thực tế");
        }
        if (calculateAcceptanceRate(candidate).compareTo(decimal("0.80")) >= 0) {
            reasons.add("Tỷ lệ chấp nhận yêu cầu ổn định");
        }
        if (calculateNonCancellationRate(candidate).compareTo(decimal("0.90")) >= 0) {
            reasons.add("Ít hủy lịch sau khi đã nhận");
        }
        if (isRecentlyActive(candidate)) {
            reasons.add("Hoạt động gần đây");
        }
        baseScore = baseScore.add(calculateSearchQualityScore(candidate));
        BigDecimal capabilityScore = calculateCapabilityScore(candidate, menteeProfile, menteeFeatures, enrichedData.helpTopics(), enrichedData.subjectResults(), reasons);
        if (capabilityScore.compareTo(BigDecimal.ZERO) > 0) {
            baseScore = baseScore.add(capabilityScore);
        }

        if (!enrichedData.services().isEmpty()) {
            baseScore = baseScore.add(serviceBonusScore(enrichedData.services().size()));
            reasons.add("Có " + enrichedData.services().size() + " dịch vụ đang hoạt động");
        }
        if (enrichedData.hasAvailability()) {
            baseScore = baseScore.add(HAS_AVAILABILITY_BONUS_SCORE);
            reasons.add("Có lịch rảnh khả dụng");
        }
        if (enrichedData.hasPreferredDurationAvailability()) {
            baseScore = baseScore.add(DURATION_PREFERENCE_MATCH_BONUS);
            reasons.add("Có slot phù hợp đúng thời lượng mentee muốn book");
        }

        return baseScore.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSearchScore(
            MentorDiscoveryQueryRow row,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            String normalizedKeyword,
            MentorEnrichedData enrichedData
    ) {
        BigDecimal extraBonus = ZERO;
        if (!enrichedData.services().isEmpty()) {
            extraBonus = extraBonus.add(serviceBonusScore(enrichedData.services().size()));
        }
        if (enrichedData.hasAvailability()) {
            extraBonus = extraBonus.add(HAS_AVAILABILITY_BONUS_SCORE);
        }
        if (enrichedData.hasPreferredDurationAvailability()) {
            extraBonus = extraBonus.add(DURATION_PREFERENCE_MATCH_BONUS);
        }

        List<String> tokens = keywordSupport.tokenizeSearchText(normalizedKeyword);
        if (tokens.isEmpty()) {
            return calculatePersonalizationScore(row, menteeProfile, menteeFeatures)
                    .add(calculateSearchQualityScore(row))
                    .add(calculateCapabilityScore(row, menteeProfile, menteeFeatures, enrichedData.helpTopics(), enrichedData.subjectResults(), null))
                    .add(extraBonus)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal score = ZERO;
        String exactKeyword = keywordSupport.normalizeSearchText(normalizedKeyword);

        String normalizedHeadline = keywordSupport.normalizeSearchText(row.headline());
        if (!normalizedHeadline.isBlank() && normalizedHeadline.contains(exactKeyword)) {
            score = score.add(HEADLINE_EXACT_BONUS);
        }

        List<String> profileFields = Arrays.stream(new String[]{
                        row.displayName(),
                        row.headline(),
                        row.expertiseDescription(),
                        row.bio(),
                        row.campusName(),
                        row.programName(),
                        row.specializationName()
                })
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> tagFields = enrichedData.helpTopics().stream()
                .flatMap(tag -> Arrays.stream(new String[]{tag.nameVi(), tag.nameEn(), tag.code()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> subjectFields = enrichedData.subjectResults().stream()
                .flatMap(subject -> Arrays.stream(new String[]{
                        subject.subjectCode(),
                        subject.subjectName(),
                        subject.scoreValue() == null ? null : subject.scoreValue().toPlainString()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> projectFields = enrichedData.featuredProjects().stream()
                .flatMap(project -> Arrays.stream(new String[]{
                        project.title(),
                        project.content(),
                        project.projectDescription(),
                        project.liveDemoUrl()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> achievementFields = enrichedData.achievements().stream()
                .flatMap(achievement -> Arrays.stream(new String[]{
                        achievement.title(),
                        achievement.awardDescription(),
                        achievement.productHeader(),
                        achievement.productDescription(),
                        achievement.demoUrl()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> serviceFields = enrichedData.services().stream()
                .flatMap(service -> Arrays.stream(new String[]{service.getTitle(), service.getDescription(), service.getExpectedOutcome()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        if (containsPhrase(profileFields, exactKeyword)
                || containsPhrase(tagFields, exactKeyword)
                || containsPhrase(subjectFields, exactKeyword)
                || containsPhrase(projectFields, exactKeyword)
                || containsPhrase(achievementFields, exactKeyword)
                || containsPhrase(serviceFields, exactKeyword)) {
            score = score.add(decimal(25));
        }

        int profileMatches = countTokenMatches(profileFields, tokens);
        int tagMatches = countTokenMatches(tagFields, tokens);
        int subjectMatches = countTokenMatches(subjectFields, tokens);
        int projectMatches = countTokenMatches(projectFields, tokens);
        int achievementMatches = countTokenMatches(achievementFields, tokens);
        int serviceMatches = countTokenMatches(serviceFields, tokens);
        int totalMatches = profileMatches + tagMatches + subjectMatches + projectMatches + achievementMatches + serviceMatches;

        score = score.add(decimal(profileMatches * 8));
        score = score.add(decimal(tagMatches * 10));
        score = score.add(decimal(subjectMatches * 12));
        score = score.add(decimal(projectMatches * 10));
        score = score.add(decimal(achievementMatches * 8));
        score = score.add(decimal(serviceMatches * 12));

        if (totalMatches > 0) {
            BigDecimal coverageBonus = BigDecimal.valueOf(totalMatches)
                    .multiply(BigDecimal.valueOf(10))
                    .divide(BigDecimal.valueOf(tokens.size()), 2, RoundingMode.HALF_UP);
            score = score.add(coverageBonus);
        }

        if (row.verifiedAt() != null) {
            LocalDateTime now = currentTime();
            if (row.verifiedAt().isAfter(now.minusDays(7))) {
                score = score.add(VERIFIED_RECENT_7D_BONUS);
            } else if (row.verifiedAt().isAfter(now.minusDays(30))) {
                score = score.add(VERIFIED_RECENT_30D_BONUS);
            }
        }

        int completedSessions = defaultInteger(row.completedSessions());
        if (completedSessions >= 50) {
            score = score.add(SESSION_VELOCITY_HIGH_BONUS);
        } else if (completedSessions >= 20) {
            score = score.add(SESSION_VELOCITY_MED_BONUS);
        }

        score = score.add(calculatePersonalizationScore(row, menteeProfile, menteeFeatures));
        score = score.add(calculateSearchQualityScore(row));
        score = score.add(calculateCapabilityScore(row, menteeProfile, menteeFeatures, enrichedData.helpTopics(), enrichedData.subjectResults(), null));
        score = score.add(extraBonus);
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCapabilityScore(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            List<MentorTagResponse> helpTopics,
            List<MentorSubjectResultResponse> subjectResults,
            List<String> reasons
    ) {
        if (menteeFeatures == null || !menteeFeatures.hasAnySignal()) {
            return ZERO;
        }
        BigDecimal score = ZERO;
        score = score.add(levelAlignmentScore(menteeFeatures.foundationNeedLevel(), candidate.foundationSupportLevel()));
        score = score.add(levelAlignmentScore(menteeFeatures.outputReviewNeedLevel(), candidate.outputReviewSupportLevel()));
        score = score.add(levelAlignmentScore(menteeFeatures.directionNeedLevel(), candidate.directionSupportLevel()));

        String mentorFitCode = menteeFeatures.mentorFitCode();
        if ("MENTOR_FIT_SUBJECT_MATCH".equals(mentorFitCode)
                && hasSubjectMatchSignal(candidate, menteeProfile, helpTopics, subjectResults)
                && (defaultInteger(candidate.foundationSupportLevel()) >= 3 || defaultInteger(candidate.outputReviewSupportLevel()) >= 3)) {
            score = score.add(MENTOR_FIT_SUBJECT_BONUS);
            addReason(reasons, "Khớp kiểu mentor mạnh đúng phần đang cần");
        }
        if ("MENTOR_FIT_RECENT_ALUMNI".equals(mentorFitCode) && Boolean.TRUE.equals(candidate.alumni())) {
            score = score.add(MENTOR_FIT_ALUMNI_BONUS);
            addReason(reasons, "Khớp nhu cầu góc nhìn alumni/OJT");
        }
        if ("MENTOR_FIT_SIMILAR_EXPERIENCE".equals(mentorFitCode) && defaultInteger(candidate.completedSessions()) > 0) {
            score = score.add(decimal("8.00"));
            addReason(reasons, "Mentor đã có trải nghiệm mentoring thực tế");
        }
        if (score.compareTo(BigDecimal.ZERO) > 0) {
            addReason(reasons, "Khớp nhu cầu mentoring đã khai báo");
        }
        return applyMatchingRecencyMultiplier(score, menteeFeatures).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxCapabilityScore(MenteeMatchingFeatures menteeFeatures) {
        if (menteeFeatures == null || !menteeFeatures.hasAnySignal()) {
            return ZERO;
        }
        BigDecimal maxScore = ZERO;
        int foundationMax = menteeFeatures.foundationNeedLevel() != null ? menteeFeatures.foundationNeedLevel() : 0;
        int outputMax = menteeFeatures.outputReviewNeedLevel() != null ? menteeFeatures.outputReviewNeedLevel() : 0;
        int directionMax = menteeFeatures.directionNeedLevel() != null ? menteeFeatures.directionNeedLevel() : 0;
        
        maxScore = maxScore.add(CAPABILITY_MATCH_MULTIPLIER.multiply(BigDecimal.valueOf(foundationMax)));
        maxScore = maxScore.add(CAPABILITY_MATCH_MULTIPLIER.multiply(BigDecimal.valueOf(outputMax)));
        maxScore = maxScore.add(CAPABILITY_MATCH_MULTIPLIER.multiply(BigDecimal.valueOf(directionMax)));

        String mentorFitCode = menteeFeatures.mentorFitCode();
        if ("MENTOR_FIT_SUBJECT_MATCH".equals(mentorFitCode)) {
            maxScore = maxScore.add(MENTOR_FIT_SUBJECT_BONUS);
        } else if ("MENTOR_FIT_RECENT_ALUMNI".equals(mentorFitCode)) {
            maxScore = maxScore.add(MENTOR_FIT_ALUMNI_BONUS);
        } else if ("MENTOR_FIT_SIMILAR_EXPERIENCE".equals(mentorFitCode)) {
            maxScore = maxScore.add(decimal("8.00"));
        }
        
        return applyMatchingRecencyMultiplier(maxScore, menteeFeatures).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePersonalizationScore(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures
    ) {
        BigDecimal baseScore = ZERO;
        if (menteeProfile == null) {
            return baseScore;
        }

        if (sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId())) {
            baseScore = baseScore.add(SAME_PROGRAM_SCORE);
        }
        if (sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId())) {
            baseScore = baseScore.add(SAME_SPECIALIZATION_SCORE);
        }
        if (sameUuid(menteeProfile.getCampus() == null ? null : menteeProfile.getCampus().getId(), candidate.campusId())) {
            baseScore = baseScore.add(SAME_CAMPUS_SCORE);
        }
        if (Boolean.TRUE.equals(candidate.alumni()) && shouldBoostAlumni(candidate, menteeFeatures)) {
            baseScore = baseScore.add(MENTOR_ALUMNI_SCORE);
        } else {
            Integer menteeSemester = menteeProfile.getSemester();
            Integer mentorSemester = candidate.semester();
            if (menteeSemester != null && mentorSemester != null) {
                if (mentorSemester > menteeSemester) {
                    baseScore = baseScore.add(MENTOR_HIGHER_SEMESTER_SCORE);
                } else if (mentorSemester.equals(menteeSemester)) {
                    baseScore = baseScore.add(MENTOR_EQUAL_SEMESTER_SCORE);
                }
            }
        }
        return baseScore;
    }

    private BigDecimal calculateSearchQualityScore(MentorDiscoveryQueryRow row) {
        BigDecimal score = ZERO;
        BigDecimal rating = defaultDecimal(row.ratingAverage());
        int reviews = defaultInteger(row.reviewCount());
        int completedSessions = defaultInteger(row.completedSessions());

        score = score.add(calculateBayesianRating(rating, reviews).multiply(RATING_QUALITY_MULTIPLIER));
        score = score.add(boundedLogScore(reviews, 10, MAX_REVIEW_VOLUME_SCORE));
        score = score.add(boundedLogScore(completedSessions, 50, MAX_SESSION_VOLUME_SCORE));
        score = score.add(calculateBehaviorScore(row));
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSearchScorePercentage(BigDecimal rawScore, String normalizedKeyword, int activeServiceCount, boolean hasDurationPreference, MenteeMatchingFeatures menteeFeatures) {
        BigDecimal maxScore = calculateSearchScoreMax(normalizedKeyword, activeServiceCount, hasDurationPreference, menteeFeatures);
        maxScore = maxScore.max(BigDecimal.ONE);

        BigDecimal percentage = rawScore.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 2, RoundingMode.HALF_UP);
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        if (percentage.compareTo(MAX_PERCENTAGE_SCORE) > 0) {
            return MAX_PERCENTAGE_SCORE;
        }
        return percentage;
    }

    private BigDecimal calculateSearchScoreMax(String normalizedKeyword, int activeServiceCount, boolean hasDurationPreference, MenteeMatchingFeatures menteeFeatures) {
        int tokenCount = keywordSupport.tokenizeSearchText(normalizedKeyword).size();
        int cappedServiceCount = Math.min(Math.max(activeServiceCount, 0), MAX_SEARCH_SERVICE_BONUS_COUNT);

        BigDecimal maxScore = MAX_SEARCH_PERSONALIZATION_SCORE
                .add(MAX_SEARCH_QUALITY_SCORE)
                .add(calculateMaxCapabilityScore(menteeFeatures))
                .add(serviceBonusScore(cappedServiceCount))
                .add(HAS_AVAILABILITY_BONUS_SCORE);
        if (hasDurationPreference) {
            maxScore = maxScore.add(DURATION_PREFERENCE_MATCH_BONUS);
        }

        if (tokenCount <= 0) {
            return maxScore.setScale(2, RoundingMode.HALF_UP);
        }

        return maxScore
                .add(HEADLINE_EXACT_BONUS)
                .add(decimal(25))
                .add(decimal(tokenCount * 8))
                .add(decimal(tokenCount * 10))
                .add(decimal(tokenCount * 12))
                .add(decimal(tokenCount * 10))
                .add(decimal(tokenCount * 8))
                .add(decimal(tokenCount * 12))
                .add(decimal(60))
                .add(VERIFIED_RECENT_7D_BONUS)
                .add(SESSION_VELOCITY_HIGH_BONUS)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBehaviorScore(MentorDiscoveryQueryRow row) {
        BigDecimal score = ZERO;
        score = score.add(calculateAcceptanceRate(row).multiply(MAX_ACCEPTANCE_RATE_SCORE));
        score = score.add(calculateNonCancellationRate(row).multiply(MAX_CANCELLATION_RELIABILITY_SCORE));

        if (row.lastActiveAt() != null) {
            LocalDateTime now = currentTime();
            if (!row.lastActiveAt().isBefore(now.minusDays(14))) {
                score = score.add(RECENT_ACTIVITY_14D_BONUS);
            } else if (!row.lastActiveAt().isBefore(now.minusDays(30))) {
                score = score.add(RECENT_ACTIVITY_30D_BONUS);
            }
        }

        if (row.verifiedAt() != null
                && row.verifiedAt().isAfter(currentTime().minusDays(14))
                && defaultInteger(row.completedSessions()) <= 3
                && defaultInteger(row.reviewCount()) <= 2) {
            score = score.add(COLD_START_VERIFIED_BONUS);
        }

        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal applyMatchingRecencyMultiplier(BigDecimal score, MenteeMatchingFeatures menteeFeatures) {
        if (score == null || menteeFeatures == null || menteeFeatures.latestAnsweredAt() == null) {
            return score == null ? ZERO : score;
        }
        LocalDateTime latestAnsweredAt = menteeFeatures.latestAnsweredAt();
        if (latestAnsweredAt.isBefore(currentTime().minusDays(90))) {
            return score.multiply(STALE_MATCHING_90D_MULTIPLIER);
        }
        if (latestAnsweredAt.isBefore(currentTime().minusDays(30))) {
            return score.multiply(STALE_MATCHING_30D_MULTIPLIER);
        }
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

    private boolean isRecentlyActive(MentorDiscoveryQueryRow row) {
        return row != null
                && row.lastActiveAt() != null
                && !row.lastActiveAt().isBefore(currentTime().minusDays(30));
    }

    private int countTokenMatches(List<String> fields, List<String> tokens) {
        if (fields == null || fields.isEmpty() || tokens == null || tokens.isEmpty()) {
            return 0;
        }
        Set<String> matchedTokens = new LinkedHashSet<>();
        for (String token : tokens) {
            for (String field : fields) {
                if (containsToken(field, token)) {
                    matchedTokens.add(token);
                    break;
                }
            }
        }
        return matchedTokens.size();
    }

    private boolean containsPhrase(List<String> fields, String normalizedPhrase) {
        if (fields == null || fields.isEmpty() || normalizedPhrase == null || normalizedPhrase.isBlank()) {
            return false;
        }
        return fields.stream().filter(value -> value != null && !value.isBlank())
                .map(keywordSupport::normalizeSearchText)
                .anyMatch(normalized -> normalized.contains(normalizedPhrase));
    }

    private boolean containsToken(String field, String token) {
        if (field == null || field.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        return keywordSupport.normalizeSearchText(field).contains(token);
    }

    private boolean sameUuid(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private boolean shouldBoostAlumni(MentorDiscoveryQueryRow candidate, MenteeMatchingFeatures menteeFeatures) {
        if (!Boolean.TRUE.equals(candidate.alumni()) || menteeFeatures == null) {
            return false;
        }
        if ("MENTOR_FIT_RECENT_ALUMNI".equals(menteeFeatures.mentorFitCode())) {
            return true;
        }
        Integer directionNeedLevel = menteeFeatures.directionNeedLevel();
        return directionNeedLevel != null && directionNeedLevel >= 3;
    }

    private boolean hasSubjectMatchSignal(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            List<MentorTagResponse> helpTopics,
            List<MentorSubjectResultResponse> subjectResults
    ) {
        boolean sameSpecialization = menteeProfile != null
                && sameUuid(menteeProfile.getSpecialization() == null ? null : menteeProfile.getSpecialization().getId(), candidate.specializationId());
        if (sameSpecialization) {
            return true;
        }

        boolean sameProgram = menteeProfile != null
                && sameUuid(menteeProfile.getProgram() == null ? null : menteeProfile.getProgram().getId(), candidate.programId());
        return sameProgram
                && ((helpTopics != null && !helpTopics.isEmpty()) || (subjectResults != null && !subjectResults.isEmpty()));
    }

    private BigDecimal levelAlignmentScore(Integer needLevel, Integer supportLevel) {
        if (needLevel == null || supportLevel == null) {
            return ZERO;
        }
        int gap = Math.max(0, needLevel - supportLevel);
        int aligned = Math.max(0, needLevel - gap);
        return CAPABILITY_MATCH_MULTIPLIER.multiply(BigDecimal.valueOf(aligned)).setScale(2, RoundingMode.HALF_UP);
    }

    private void addReason(List<String> reasons, String reason) {
        if (reasons != null && reason != null && reasons.stream().noneMatch(reason::equals)) {
            reasons.add(reason);
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
        int cappedServiceCount = Math.min(Math.max(activeServiceCount, 0), MAX_SEARCH_SERVICE_BONUS_COUNT);
        return ACTIVE_SERVICE_BONUS_SCORE.multiply(BigDecimal.valueOf(cappedServiceCount));
    }

    public static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDateTime currentTime() {
        return LocalDateTime.now(APP_ZONE);
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record RankedSearchCandidate(
            MentorDiscoveryQueryRow row,
            MentorEnrichedData enrichedData,
            BigDecimal score,
            BigDecimal matchScore
    ) {
    }

    public record RecommendationScore(
            BigDecimal matchScore,
            List<String> matchReasons
    ) {
    }
}
