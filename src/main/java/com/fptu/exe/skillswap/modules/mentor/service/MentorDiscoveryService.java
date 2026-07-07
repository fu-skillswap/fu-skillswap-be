package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import com.fptu.exe.skillswap.modules.matching.service.MentoringMatchProfileService;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class MentorDiscoveryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID EMPTY_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";
    private static final BigDecimal SAME_PROGRAM_SCORE = decimal(40);
    private static final BigDecimal SAME_SPECIALIZATION_SCORE = decimal(30);
    private static final BigDecimal SAME_CAMPUS_SCORE = decimal(10);
    private static final BigDecimal MENTOR_ALUMNI_SCORE = decimal(20);
    private static final BigDecimal MENTOR_HIGHER_SEMESTER_SCORE = decimal(15);
    private static final BigDecimal MENTOR_EQUAL_SEMESTER_SCORE = decimal(10);
    private static final BigDecimal HEADLINE_EXACT_BONUS = decimal(60);
    private static final BigDecimal SUBJECTS_PHRASE_BONUS = decimal(40);
    private static final BigDecimal VERIFIED_RECENT_7D_BONUS = decimal(10);
    private static final BigDecimal VERIFIED_RECENT_30D_BONUS = decimal(5);
    private static final BigDecimal SESSION_VELOCITY_HIGH_BONUS = decimal(10);
    private static final BigDecimal SESSION_VELOCITY_MED_BONUS = decimal(5);
    private static final BigDecimal QUALITY_HIGH_RATING_BONUS = decimal(8);
    private static final BigDecimal QUALITY_CREDIBLE_REVIEWS_BONUS = decimal(5);
    private static final BigDecimal QUALITY_EXPERIENCED_SESSIONS_BONUS = decimal(5);
    private static final BigDecimal ACTIVE_SERVICE_BONUS_SCORE = decimal(5);
    private static final BigDecimal HAS_AVAILABILITY_BONUS_SCORE = decimal(15);
    private static final BigDecimal CAPABILITY_MATCH_MULTIPLIER = decimal("8.00");
    private static final BigDecimal MENTOR_FIT_SUBJECT_BONUS = decimal("12.00");
    private static final BigDecimal MENTOR_FIT_ALUMNI_BONUS = decimal("12.00");
    private static final BigDecimal DURATION_PREFERENCE_MATCH_BONUS = decimal("6.00");
    private static final BigDecimal MAX_SEARCH_PERSONALIZATION_SCORE = decimal(100);
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

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorTagRepository mentorTagRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityService mentorAvailabilityService;
    private final AvailabilitySlotServiceRepository availabilitySlotServiceRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    private final DataSource dataSource;
    private final TagRepository tagRepository;
    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;
    private final MentoringMatchProfileService mentoringMatchProfileService;
    private final PaymentProperties paymentProperties;
    private final InternalTelemetryService internalTelemetryService;

    private volatile List<String> cachedKeywords = new java.util.ArrayList<>();
    private final Object cacheLock = new Object();

    private volatile Boolean postgresDetected = null;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        MenteeMatchingFeatures menteeFeatures = mentoringMatchProfileService.latestFeatures(currentUserId);
        List<UUID> tagIds = normalizedTagIds(safeRequest.getTagIds());

        boolean hasKeyword = safeRequest.getKeyword() != null && !safeRequest.getKeyword().isBlank();
        String normalizedKeyword = normalizeSearchText(safeRequest.getKeyword());
        String keywordPattern = toLikePattern(safeRequest.getKeyword());
        String normalizedKeywordPattern = toLikePattern(normalizedKeyword);

        int requestedPage = Math.max(safeRequest.getPage(), 0);
        int requestedSize = Math.min(Math.max(safeRequest.getSize(), 1), 30);
        org.springframework.data.domain.Sort.Direction direction = safeRequest.getDirection() == org.springframework.data.domain.Sort.Direction.ASC ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        String sortBy = safeRequest.getSortBy() == null ? "relevance" : safeRequest.getSortBy().trim();

        List<org.springframework.data.domain.Sort.Order> orders = new java.util.ArrayList<>();
        switch (sortBy) {
            case "ratingAverage" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "reviewCount" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "totalReviews"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "completedSessions" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            case "updatedAt" -> {
                orders.add(new org.springframework.data.domain.Sort.Order(direction, "verifiedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
            default -> {
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "averageRating"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "totalCompletedSessions"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"));
                orders.add(new org.springframework.data.domain.Sort.Order(org.springframework.data.domain.Sort.Direction.ASC, "userId"));
            }
        }
        boolean relevanceSort = "relevance".equals(sortBy);
        Pageable searchPageable = relevanceSort
                ? PageRequest.of(0, relevanceWindowSize(requestedPage, requestedSize), org.springframework.data.domain.Sort.by(orders))
                : PageRequest.of(requestedPage, requestedSize, org.springframework.data.domain.Sort.by(orders));

        Page<UUID> candidatePage;
        if (hasKeyword && isPostgresDataSource()) {
            candidatePage = findCandidatesByFts(
                    normalizedKeyword,
                    safeRequest,
                    tagIds,
                    requestedPage,
                    requestedSize,
                    relevanceSort
            );
        } else if (hasKeyword) {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    keywordPattern,
                    normalizedKeywordPattern,
                    ACCENTED_CHARACTERS,
                    PLAIN_CHARACTERS,
                    currentTime(),
                    searchPageable
            );
        } else {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIds(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    safeRequest.getCampusId(),
                    safeRequest.getSpecializationId(),
                    hasTagFilter(safeRequest.getTagIds()),
                    tagIds,
                    currentTime(),
                    searchPageable
            );
        }

        if (hasKeyword && candidatePage.isEmpty()) {
            String corrected = correctSpelling(normalizedKeyword);
            if (!corrected.equals(normalizedKeyword)) {
                log.info("Original search keyword '{}' produced 0 results. Fallback fuzzy search using corrected spelling: '{}'", normalizedKeyword, corrected);
                String fallbackKeywordPattern = toLikePattern(corrected);
                String fallbackNormalizedKeywordPattern = toLikePattern(corrected);
                
                if (isPostgresDataSource()) {
                    candidatePage = findCandidatesByFts(
                            corrected,
                            safeRequest,
                            tagIds,
                            requestedPage,
                            requestedSize,
                            relevanceSort
                    );
                } else {
                    candidatePage = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                            MentorStatus.ACTIVE,
                            MentorTagType.HELP_TOPIC,
                            safeRequest.getCampusId(),
                            safeRequest.getSpecializationId(),
                            hasTagFilter(safeRequest.getTagIds()),
                            tagIds,
                            fallbackKeywordPattern,
                            fallbackNormalizedKeywordPattern,
                            ACCENTED_CHARACTERS,
                            PLAIN_CHARACTERS,
                            currentTime(),
                            searchPageable
                    );
                }
            }
        }
        if (hasKeyword && candidatePage.isEmpty()) {
            internalTelemetryService.record(
                    "MENTOR_SEARCH_ZERO_RESULT",
                    currentUserId,
                    "MENTOR_SEARCH",
                    null,
                    Map.of(
                            "keyword", normalizedKeyword,
                            "campusId", String.valueOf(safeRequest.getCampusId()),
                            "specializationId", String.valueOf(safeRequest.getSpecializationId())
                    )
            );
        }

        List<UUID> candidateIds = candidatePage.getContent();
        List<MentorDiscoveryQueryRow> rows = relevanceSort
                ? loadDiscoveryRowsByMentorIds(candidateIds)
                : loadDiscoveryRowsInPageOrder(candidateIds);
        if (rows.isEmpty()) {
            return PageResponse.<MentorDiscoveryCardResponse>builder()
                    .content(List.of())
                    .page(requestedPage)
                    .size(requestedSize)
                    .totalElements(candidatePage.getTotalElements())
                    .totalPages(totalPages(candidatePage.getTotalElements(), requestedSize))
                    .last(isLastPage(requestedPage, requestedSize, candidatePage.getTotalElements()))
                    .build();
        }

        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                candidateIds,
                Set.of(MentorTagType.HELP_TOPIC)
        );
        Map<UUID, List<MentorSubjectResultResponse>> subjectResultsByMentor = loadSubjectResultsByMentor(candidateIds);
        Map<UUID, List<MentorFeaturedProjectResponse>> featuredProjectsByMentor = loadFeaturedProjectsByMentor(candidateIds);
        Map<UUID, List<MentorAchievementResponse>> achievementsByMentor = loadAchievementsByMentor(candidateIds);

        List<MentorService> activeServices = loadActiveServicesByMentorIds(candidateIds);
        Map<UUID, List<MentorService>> servicesByMentor = groupServicesByMentor(activeServices);
        Set<UUID> mentorsWithAvailability = loadMentorsWithAvailability(candidateIds, currentTime());
        Set<UUID> mentorsWithPreferredDurationAvailability = loadMentorsWithPreferredDurationAvailability(candidateIds, menteeFeatures, currentTime());

        List<MentorDiscoveryCardResponse> content;
        if (relevanceSort) {
            List<RankedSearchCandidate> rankedCandidates = rows.stream()
                    .map(row -> {
                        List<MentorTagResponse> helpTopics = helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of());
                        List<MentorService> services = servicesByMentor.getOrDefault(row.mentorUserId(), List.of());
                        BigDecimal rawScore = calculateSearchScore(
                                row,
                                menteeProfile,
                                menteeFeatures,
                                normalizedKeyword,
                                helpTopics,
                                subjectResultsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                featuredProjectsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                achievementsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                services,
                                mentorsWithAvailability.contains(row.mentorUserId()),
                                mentorsWithPreferredDurationAvailability.contains(row.mentorUserId())
                        );
                        return new RankedSearchCandidate(
                                row,
                                helpTopics,
                                rawScore,
                                calculateSearchScorePercentage(rawScore, normalizedKeyword, services.size(), menteeFeatures != null && menteeFeatures.durationPreferenceCode() != null)
                        );
                    })
                    .sorted(Comparator
                            .comparing(RankedSearchCandidate::score).reversed()
                            .thenComparing(candidate -> defaultDecimal(candidate.row().ratingAverage()), Comparator.reverseOrder())
                            .thenComparing(candidate -> defaultInteger(candidate.row().completedSessions()), Comparator.reverseOrder())
                            .thenComparing(candidate -> candidate.row().verifiedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(candidate -> candidate.row().mentorUserId()))
                    .toList();

            int fromIndex = Math.min(requestedPage * requestedSize, rankedCandidates.size());
            int toIndex = Math.min(fromIndex + requestedSize, rankedCandidates.size());
            content = rankedCandidates.subList(fromIndex, toIndex).stream()
                    .map(candidate -> toCardResponse(
                            candidate.row(),
                            candidate.helpTopics(),
                            candidate.matchScore(),
                            subjectResultsByMentor.getOrDefault(candidate.row().mentorUserId(), List.of()),
                            featuredProjectsByMentor.getOrDefault(candidate.row().mentorUserId(), List.of()),
                            achievementsByMentor.getOrDefault(candidate.row().mentorUserId(), List.of())
                    ))
                    .toList();
        } else {
            content = rows.stream()
                    .map(row -> {
                        List<MentorTagResponse> helpTopics = helpTopicsByMentor.getOrDefault(row.mentorUserId(), List.of());
                        List<MentorService> services = servicesByMentor.getOrDefault(row.mentorUserId(), List.of());
                        BigDecimal rawScore = calculateSearchScore(
                                row,
                                menteeProfile,
                                menteeFeatures,
                                normalizedKeyword,
                                helpTopics,
                                subjectResultsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                featuredProjectsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                achievementsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                services,
                                mentorsWithAvailability.contains(row.mentorUserId()),
                                mentorsWithPreferredDurationAvailability.contains(row.mentorUserId())
                        );
                        BigDecimal matchScore = calculateSearchScorePercentage(rawScore, normalizedKeyword, services.size(), menteeFeatures != null && menteeFeatures.durationPreferenceCode() != null);
                        return toCardResponse(
                                row,
                                helpTopics,
                                matchScore,
                                subjectResultsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                featuredProjectsByMentor.getOrDefault(row.mentorUserId(), List.of()),
                                achievementsByMentor.getOrDefault(row.mentorUserId(), List.of())
                        );
                    })
                    .toList();
        }

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(content)
                .page(requestedPage)
                .size(requestedSize)
                .totalElements(candidatePage.getTotalElements())
                .totalPages(totalPages(candidatePage.getTotalElements(), requestedSize))
                .last(isLastPage(requestedPage, requestedSize, candidatePage.getTotalElements()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        MenteeMatchingFeatures menteeFeatures = mentoringMatchProfileService.latestFeatures(currentUserId);
        LocalDateTime now = currentTime();
        boolean richProfile = menteeProfile != null
                && menteeProfile.getProgram() != null
                && menteeProfile.getSpecialization() != null;
        int candidateFetchSize = richProfile ? Math.min(200, Math.max(safeLimit * 10, 60)) : Math.min(120, Math.max(safeLimit * 5, safeLimit));

        List<MentorDiscoveryQueryRow> candidates = mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                MentorStatus.ACTIVE,
                MentorTagType.HELP_TOPIC,
                currentUserId,
                now,
                PageRequest.of(0, candidateFetchSize)
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList();
        Map<UUID, List<MentorTagResponse>> helpTopicsByMentor = loadTagsByMentor(
                candidateIds,
                Set.of(MentorTagType.HELP_TOPIC)
        );
        Map<UUID, List<MentorSubjectResultResponse>> subjectResultsByMentor = loadSubjectResultsByMentor(candidateIds);
        Map<UUID, List<MentorFeaturedProjectResponse>> featuredProjectsByMentor = loadFeaturedProjectsByMentor(candidateIds);
        Map<UUID, List<MentorAchievementResponse>> achievementsByMentor = loadAchievementsByMentor(candidateIds);

        List<MentorService> activeServices = loadActiveServicesByMentorIds(candidateIds);
        Map<UUID, List<MentorService>> servicesByMentor = groupServicesByMentor(activeServices);
        Set<UUID> mentorsWithAvailability = loadMentorsWithAvailability(candidateIds, now);
        Set<UUID> mentorsWithPreferredDurationAvailability = loadMentorsWithPreferredDurationAvailability(candidateIds, menteeFeatures, now);

        return candidates.stream()
                .map(candidate -> toRecommendation(
                        candidate,
                        helpTopicsByMentor.getOrDefault(candidate.mentorUserId(), List.of()),
                        subjectResultsByMentor.getOrDefault(candidate.mentorUserId(), List.of()),
                        featuredProjectsByMentor.getOrDefault(candidate.mentorUserId(), List.of()),
                        achievementsByMentor.getOrDefault(candidate.mentorUserId(), List.of()),
                        servicesByMentor.getOrDefault(candidate.mentorUserId(), List.of()),
                        mentorsWithAvailability.contains(candidate.mentorUserId()),
                        mentorsWithPreferredDurationAvailability.contains(candidate.mentorUserId()),
                        menteeProfile,
                        menteeFeatures
                ))
                .sorted(Comparator
                        .comparing(MentorRecommendationResponse::matchScore).reversed()
                        .thenComparing(response -> defaultInteger(response.mentor().completedSessions()), Comparator.reverseOrder())
                        .thenComparing(response -> defaultDecimal(response.mentor().ratingAverage()), Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    @Transactional(readOnly = true)
    public MentorDiscoveryDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        internalTelemetryService.record("MENTOR_DETAIL_OPENED", null, "MENTOR", mentorUserId, Map.of());
        StudentProfile studentProfile = studentProfileRepository.findWithDetailsByUserId(mentorUserId).orElse(null);
        Map<UUID, List<MentorTagResponse>> tagsByMentor = loadTagsByMentor(
                List.of(mentorUserId),
                Set.of(MentorTagType.HELP_TOPIC)
        );
        List<MentorTagResponse> mentorTags = tagsByMentor.getOrDefault(mentorUserId, List.of());
        List<MentorSubjectResultResponse> subjectResults = loadSubjectResultsByMentor(List.of(mentorUserId)).getOrDefault(mentorUserId, List.of());
        List<MentorFeaturedProjectResponse> featuredProjects = loadFeaturedProjectsByMentor(List.of(mentorUserId)).getOrDefault(mentorUserId, List.of());
        List<MentorAchievementResponse> achievements = loadAchievementsByMentor(List.of(mentorUserId)).getOrDefault(mentorUserId, List.of());
        List<MentorServiceResponse> services = mentorServiceRepository
                .findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .map(this::toServiceResponse)
                .toList();

        Campus campus = studentProfile == null ? null : studentProfile.getCampus();
        AcademicProgram program = studentProfile == null ? null : studentProfile.getProgram();
        Specialization specialization = studentProfile == null ? null : studentProfile.getSpecialization();

        BigDecimal rating = defaultDecimal(mentorProfile.getAverageRating());
        int reviews = defaultInteger(mentorProfile.getTotalReviews());
        BigDecimal displayRating = reviews == 0 ? BigDecimal.valueOf(5.0).setScale(2, RoundingMode.HALF_UP) : rating;

        boolean hasCompletedProfile = hasCompletedPeerMentorProfile(mentorProfile, mentorTags, subjectResults);
        boolean hasActiveServices = services.stream().anyMatch(MentorServiceResponse::active);
        boolean canRequestBooking = mentorProfile.isAvailable() && mentorProfile.getVerifiedAt() != null
                && !isBookingSuspended(mentorProfile)
                && hasActiveServices;

        return MentorDiscoveryDetailResponse.builder()
                .mentorUserId(mentorProfile.getUserId())
                .displayName(mentorProfile.getUser().getFullName())
                .avatarUrl(mentorProfile.getUser().getAvatarUrl())
                .headline(mentorProfile.getHeadline())
                .bio(studentProfile == null ? null : studentProfile.getBio())
                .expertiseDescription(mentorProfile.getExpertiseDescription())
                .subjectResults(subjectResults)
                .foundationSupportLevel(mentorProfile.getFoundationSupportLevel())
                .outputReviewSupportLevel(mentorProfile.getOutputReviewSupportLevel())
                .directionSupportLevel(mentorProfile.getDirectionSupportLevel())
                .featuredProjects(featuredProjects)
                .achievements(achievements)
                .isAvailable(mentorProfile.isAvailable())
                .bookingSuspendedUntil(mentorProfile.getBookingSuspendedUntil())
                .ratingAverage(displayRating)
                .reviewCount(reviews)
                .completedSessions(defaultInteger(mentorProfile.getTotalCompletedSessions()))
                .verifiedAt(mentorProfile.getVerifiedAt())
                .campusId(campus == null ? null : campus.getId())
                .campusName(campus == null ? null : campus.getName())
                .programId(program == null ? null : program.getId())
                .programName(program == null ? null : program.getNameVi())
                .specializationId(specialization == null ? null : specialization.getId())
                .specializationName(specialization == null ? null : specialization.getNameVi())
                .semester(studentProfile == null ? null : studentProfile.getSemester())
                .alumni(studentProfile != null && studentProfile.isAlumni())
                .portfolioUrl(mentorProfile.getPortfolioUrl())
                .githubUrl(mentorProfile.getGithubUrl())
                .helpTopicTags(filterTagsByType(mentorTags, MentorTagType.HELP_TOPIC))
                .services(services)
                .canRequestBooking(canRequestBooking)
                .hasCompletedProfile(hasCompletedProfile)
                .hasActiveServices(hasActiveServices)
                .build();
    }

    @Transactional
    public List<MentorAvailabilitySlotResponse> getMentorAvailability(UUID mentorUserId, AvailabilityQueryRequest request) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            return List.of();
        }
        AvailabilityQueryRequest safeRequest = request == null ? new AvailabilityQueryRequest() : request;
        return mentorAvailabilityService.getAvailableSlots(mentorProfile, safeRequest.getFromDate(), safeRequest.getToDate());
    }

    @Transactional(readOnly = true)
    public ServiceSlotCandidatesResponse getMentorAvailabilityCandidates(UUID mentorUserId, UUID slotId, UUID serviceId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Mentor hiện đang tạm khóa nhận booking mới");
        }
        return mentorAvailabilityService.getServiceSlotCandidates(mentorUserId, slotId, serviceId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MentorReviewResponse> getMentorReviews(UUID mentorUserId, BasePageRequest pageRequest) {
        getDiscoverableMentorProfile(mentorUserId);

        BasePageRequest safeRequest = pageRequest == null ? new BasePageRequest() : pageRequest;
        Page<MentorReviewQueryRow> page = sessionFeedbackRepository.findPublicMentorReviews(
                mentorUserId,
                reviewPageable(safeRequest)
        );

        return PageResponse.<MentorReviewResponse>builder()
                .content(page.getContent().stream().map(this::toMentorReviewResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private MentorRecommendationResponse toRecommendation(
            MentorDiscoveryQueryRow candidate,
            List<MentorTagResponse> helpTopicTags,
            List<MentorSubjectResultResponse> subjectResults,
            List<MentorFeaturedProjectResponse> featuredProjects,
            List<MentorAchievementResponse> achievements,
            List<MentorService> services,
            boolean hasAvailability,
            boolean hasPreferredDurationAvailability,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures
    ) {
        List<String> reasons = new ArrayList<>();
        BigDecimal score = calculateMatchScore(candidate, menteeProfile, menteeFeatures, services, hasAvailability, hasPreferredDurationAvailability, reasons);

        BigDecimal maxScore = SAME_PROGRAM_SCORE
                .add(SAME_SPECIALIZATION_SCORE)
                .add(SAME_CAMPUS_SCORE)
                .add(MENTOR_ALUMNI_SCORE)
                .add(MAX_RECOMMENDATION_QUALITY_SCORE)
                .add(HAS_AVAILABILITY_BONUS_SCORE)
                .add(serviceBonusScore(MAX_SEARCH_SERVICE_BONUS_COUNT));
        if (menteeFeatures != null && menteeFeatures.durationPreferenceCode() != null) {
            maxScore = maxScore.add(DURATION_PREFERENCE_MATCH_BONUS);
        }

        BigDecimal percentageScore = BigDecimal.ZERO;
        if (maxScore.compareTo(BigDecimal.ZERO) > 0) {
            percentageScore = score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
        }

        if (defaultInteger(candidate.completedSessions()) > 0 && reasons.stream().noneMatch("Đã có phiên mentoring hoàn thành"::equals)) {
            reasons.add("Đã có phiên mentoring hoàn thành");
        }

        if (reasons.isEmpty()) {
            reasons.add("Phù hợp với các tiêu chí discovery hiện tại");
        }

        return MentorRecommendationResponse.builder()
                .mentor(toCardResponse(candidate, helpTopicTags, null, subjectResults, featuredProjects, achievements))
                .matchScore(percentageScore)
                .matchReasons(reasons.stream().limit(3).toList())
                .build();
    }

    private BigDecimal calculateMatchScore(
            MentorDiscoveryQueryRow candidate,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            List<MentorService> services,
            boolean hasAvailability,
            boolean hasPreferredDurationAvailability,
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
            if (Boolean.TRUE.equals(candidate.alumni())) {
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
        baseScore = baseScore.add(calculateSearchQualityScore(candidate, menteeProfile));
        BigDecimal capabilityScore = calculateCapabilityScore(candidate, menteeFeatures, reasons);
        if (capabilityScore.compareTo(BigDecimal.ZERO) > 0) {
            baseScore = baseScore.add(capabilityScore);
        }

        if (services != null && !services.isEmpty()) {
            baseScore = baseScore.add(serviceBonusScore(services.size()));
            reasons.add("Có " + services.size() + " dịch vụ đang hoạt động");
        }
        if (hasAvailability) {
            baseScore = baseScore.add(HAS_AVAILABILITY_BONUS_SCORE);
            reasons.add("Có lịch rảnh khả dụng");
        }
        if (hasPreferredDurationAvailability) {
            baseScore = baseScore.add(DURATION_PREFERENCE_MATCH_BONUS);
            reasons.add("Có slot phù hợp đúng thời lượng mentee muốn book");
        }

        return baseScore.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<UUID, List<MentorTagResponse>> loadTagsByMentor(Collection<UUID> mentorUserIds, Set<MentorTagType> tagTypes) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<MentorTagResponse>> result = new HashMap<>();
        List<MentorTag> mentorTags = Optional.ofNullable(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(mentorUserIds, tagTypes))
                .orElse(List.of());
        mentorTags
                .stream()
                .sorted(Comparator
                        .comparing((MentorTag mentorTag) -> mentorTag.getId().getMentorUserId())
                        .thenComparing(mentorTag -> mentorTag.getTag().getNameVi()))
                .forEach(mentorTag -> result.computeIfAbsent(mentorTag.getId().getMentorUserId(), ignored -> new ArrayList<>())
                        .add(toTagResponse(mentorTag)));
        return result;
    }

    private Set<UUID> loadMentorsWithAvailability(Collection<UUID> mentorUserIds, LocalDateTime now) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new java.util.HashSet<>(Optional.ofNullable(
                mentorAvailabilitySlotRepository.findMentorUserIdsWithActiveSlotsInFuture(mentorUserIds, now)
        ).orElse(List.of()));
    }

    private Set<UUID> loadMentorsWithPreferredDurationAvailability(
            Collection<UUID> mentorUserIds,
            MenteeMatchingFeatures menteeFeatures,
            LocalDateTime now
    ) {
        Integer preferredDuration = toPreferredDurationMinutes(menteeFeatures == null ? null : menteeFeatures.durationPreferenceCode());
        if (mentorUserIds == null || mentorUserIds.isEmpty() || preferredDuration == null) {
            return Collections.emptySet();
        }
        return new java.util.HashSet<>(Optional.ofNullable(
                availabilitySlotServiceRepository.findMentorUserIdsWithFutureActiveSlotServiceDuration(mentorUserIds, preferredDuration, now)
        ).orElse(List.of()));
    }

    private List<MentorService> loadActiveServicesByMentorIds(Collection<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }
        return Optional.ofNullable(mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(new ArrayList<>(mentorUserIds)))
                .orElse(List.of());
    }

    private Map<UUID, List<MentorService>> groupServicesByMentor(List<MentorService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }

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
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
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
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MentorFeaturedProjectResponse>> result = new HashMap<>();
        mentorFeaturedProjectRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(mentorUserIds)
                .forEach(project -> result.computeIfAbsent(project.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(MentorFeaturedProjectResponse.builder()
                                .id(project.getId())
                                .title(project.getTitle())
                                .pictureUrl(project.getPictureFile() == null ? null : project.getPictureFile().getPublicUrl())
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
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MentorAchievementResponse>> result = new HashMap<>();
        mentorAchievementRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(mentorUserIds)
                .forEach(achievement -> result.computeIfAbsent(achievement.getMentorProfile().getUserId(), ignored -> new ArrayList<>())
                        .add(MentorAchievementResponse.builder()
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
                                .build()));
        return result;
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private List<String> tokenizeSearchText(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .toList();
    }

    private BigDecimal calculateSearchScore(
            MentorDiscoveryQueryRow row,
            StudentProfile menteeProfile,
            MenteeMatchingFeatures menteeFeatures,
            String normalizedKeyword,
            List<MentorTagResponse> helpTopics,
            List<MentorSubjectResultResponse> subjectResults,
            List<MentorFeaturedProjectResponse> featuredProjects,
            List<MentorAchievementResponse> achievements,
            List<MentorService> services,
            boolean hasAvailability,
            boolean hasPreferredDurationAvailability
    ) {
        BigDecimal extraBonus = ZERO;
        if (services != null && !services.isEmpty()) {
            extraBonus = extraBonus.add(serviceBonusScore(services.size()));
        }
        if (hasAvailability) {
            extraBonus = extraBonus.add(HAS_AVAILABILITY_BONUS_SCORE);
        }
        if (hasPreferredDurationAvailability) {
            extraBonus = extraBonus.add(DURATION_PREFERENCE_MATCH_BONUS);
        }

        List<String> tokens = tokenizeSearchText(normalizedKeyword);
        if (tokens.isEmpty()) {
            return calculatePersonalizationScore(row, menteeProfile)
                    .add(calculateSearchQualityScore(row, menteeProfile))
                    .add(calculateCapabilityScore(row, menteeFeatures, null))
                    .add(extraBonus)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal score = ZERO;
        String exactKeyword = normalizeSearchText(normalizedKeyword);

        String normalizedHeadline = normalizeSearchText(row.headline());
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
        List<String> tagFields = helpTopics == null ? List.of() : helpTopics.stream()
                .flatMap(tag -> Arrays.stream(new String[]{tag.nameVi(), tag.nameEn(), tag.code()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> subjectFields = subjectResults == null ? List.of() : subjectResults.stream()
                .flatMap(subject -> Arrays.stream(new String[]{
                        subject.subjectCode(),
                        subject.subjectName(),
                        subject.scoreValue() == null ? null : subject.scoreValue().toPlainString()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> projectFields = featuredProjects == null ? List.of() : featuredProjects.stream()
                .flatMap(project -> Arrays.stream(new String[]{
                        project.title(),
                        project.content(),
                        project.projectDescription(),
                        project.liveDemoUrl()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> achievementFields = achievements == null ? List.of() : achievements.stream()
                .flatMap(achievement -> Arrays.stream(new String[]{
                        achievement.title(),
                        achievement.awardDescription(),
                        achievement.productHeader(),
                        achievement.productDescription(),
                        achievement.demoUrl()
                }))
                .filter(value -> value != null && !value.isBlank())
                .toList();
        List<String> serviceFields = services == null ? List.of() : services.stream()
                .flatMap(service -> Arrays.stream(new String[]{service.getTitle(), service.getDescription(), service.getExpectedOutcome()}))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        if (containsPhrase(profileFields, exactKeyword)
                || containsPhrase(tagFields, exactKeyword)
                || containsPhrase(subjectFields, exactKeyword)
                || containsPhrase(projectFields, exactKeyword)
                || containsPhrase(achievementFields, exactKeyword)
                || containsPhrase(serviceFields, exactKeyword)) {
            score = score.add(decimal(50));
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

        score = score.add(calculatePersonalizationScore(row, menteeProfile));
        score = score.add(calculateSearchQualityScore(row, menteeProfile));
        score = score.add(calculateCapabilityScore(row, menteeFeatures, null));
        score = score.add(extraBonus);
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCapabilityScore(
            MentorDiscoveryQueryRow candidate,
            MenteeMatchingFeatures menteeFeatures,
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

    private BigDecimal calculatePersonalizationScore(MentorDiscoveryQueryRow candidate, StudentProfile menteeProfile) {
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
        if (Boolean.TRUE.equals(candidate.alumni())) {
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

    private BigDecimal calculateSearchQualityScore(MentorDiscoveryQueryRow row, StudentProfile menteeProfile) {
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

    private BigDecimal calculateSearchScorePercentage(BigDecimal rawScore, String normalizedKeyword, int activeServiceCount, boolean hasDurationPreference) {
        BigDecimal maxScore = calculateSearchScoreMax(normalizedKeyword, activeServiceCount, hasDurationPreference);
        if (maxScore.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

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

    private BigDecimal calculateSearchScoreMax(String normalizedKeyword, int activeServiceCount, boolean hasDurationPreference) {
        int tokenCount = tokenizeSearchText(normalizedKeyword).size();
        int cappedServiceCount = Math.min(Math.max(activeServiceCount, 0), MAX_SEARCH_SERVICE_BONUS_COUNT);

        BigDecimal maxScore = MAX_SEARCH_PERSONALIZATION_SCORE
                .add(MAX_SEARCH_QUALITY_SCORE)
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
                .add(decimal(50))
                .add(decimal(tokenCount * 8))
                .add(decimal(tokenCount * 10))
                .add(decimal(tokenCount * 12))
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
                .map(this::normalizeSearchText)
                .anyMatch(normalized -> normalized.contains(normalizedPhrase));
    }

    private boolean containsToken(String field, String token) {
        if (field == null || field.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        return normalizeSearchText(field).contains(token);
    }

    private StudentProfile loadStudentProfileSafely(UUID userId) {
        Optional<StudentProfile> studentProfile = studentProfileRepository.findWithDetailsByUserId(userId);
        return studentProfile == null ? null : studentProfile.orElse(null);
    }

    private MentorDiscoveryCardResponse toCardResponse(MentorDiscoveryQueryRow row, List<MentorTagResponse> helpTopicTags) {
        return toCardResponse(row, helpTopicTags, null, List.of(), List.of(), List.of());
    }

    private MentorDiscoveryCardResponse toCardResponse(
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

    private MentorTagResponse toTagResponse(MentorTag mentorTag) {
        return MentorTagResponse.builder()
                .id(mentorTag.getTag().getId())
                .code(mentorTag.getTag().getCode())
                .nameVi(mentorTag.getTag().getNameVi())
                .nameEn(mentorTag.getTag().getNameEn())
                .type(mentorTag.getTag().getType())
                .primary(mentorTag.isPrimary())
                .build();
    }

    private MentorServiceResponse toServiceResponse(MentorService mentorService) {
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

    private MentorReviewResponse toMentorReviewResponse(MentorReviewQueryRow row) {
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

    private MentorProfile getDiscoverableMentorProfile(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã mentor không hợp lệ");
        }

        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy mentor"));
        if (!isDiscoverableMentor(mentorProfile)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Mentor hiện chưa sẵn sàng hiển thị trên discovery");
        }
        List<MentorTagResponse> mentorTags = loadTagsByMentor(List.of(mentorUserId), Set.of(MentorTagType.HELP_TOPIC))
                .getOrDefault(mentorUserId, List.of());
        List<MentorSubjectResultResponse> subjectResults = loadSubjectResultsByMentor(List.of(mentorUserId))
                .getOrDefault(mentorUserId, List.of());
        return mentorProfile;
    }

    private boolean isDiscoverableMentor(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getStatus() == MentorStatus.ACTIVE
                && mentorProfile.getUser() != null
                && mentorProfile.getUser().getStatus() == com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
                && mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)
                && !mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN)
                && !mentorProfile.getUser().getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN)
                && mentorProfile.getVerifiedAt() != null
                && hasText(mentorProfile.getHeadline())
                && hasText(mentorProfile.getExpertiseDescription())
                && mentorProfile.isAvailable()
                && !isBookingSuspended(mentorProfile);
    }

    private Integer toPreferredDurationMinutes(String durationPreferenceCode) {
        if (durationPreferenceCode == null || durationPreferenceCode.isBlank()) {
            return null;
        }
        return switch (durationPreferenceCode) {
            case "DURATION_15" -> 15;
            case "DURATION_30" -> 30;
            case "DURATION_60" -> 60;
            case "DURATION_90" -> 90;
            default -> null;
        };
    }

    private boolean isBookingSuspended(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getBookingSuspendedUntil() != null
                && mentorProfile.getBookingSuspendedUntil().isAfter(currentTime());
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(APP_ZONE);
    }

    private List<MentorTagResponse> filterTagsByType(List<MentorTagResponse> tags, MentorTagType tagType) {
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

    private List<MentorDiscoveryQueryRow> loadDiscoveryRowsByMentorIds(List<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }
        return mentorProfileRepository.findDiscoveryRowsByMentorUserIds(mentorUserIds);
    }

    private List<MentorDiscoveryQueryRow> loadDiscoveryRowsInPageOrder(List<UUID> mentorUserIds) {
        if (mentorUserIds == null || mentorUserIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> orderByMentorId = new LinkedHashMap<>();
        for (int index = 0; index < mentorUserIds.size(); index++) {
            orderByMentorId.putIfAbsent(mentorUserIds.get(index), index);
        }

        return mentorProfileRepository.findDiscoveryRowsByMentorUserIds(mentorUserIds).stream()
                .sorted(Comparator.comparingInt(row -> orderByMentorId.getOrDefault(row.mentorUserId(), Integer.MAX_VALUE)))
                .toList();
    }

    private int relevanceWindowSize(int requestedPage, int requestedSize) {
        int minWindow = Math.max(requestedSize * 5, requestedSize);
        int requestedWindow = Math.max((requestedPage + 1) * requestedSize * 5, minWindow);
        return Math.min(200, requestedWindow);
    }

    private int totalPages(long totalElements, int pageSize) {
        if (pageSize <= 0 || totalElements <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    private boolean isLastPage(int page, int size, long totalElements) {
        int totalPages = totalPages(totalElements, size);
        return totalPages == 0 || page + 1 >= totalPages;
    }

    private Pageable reviewPageable(BasePageRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 20);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = request.getSortBy() == null ? "createdAt" : request.getSortBy().trim();

        List<Sort.Order> orders = switch (sortBy) {
            case "rating" -> List.of(
                    new Sort.Order(direction, "rating"),
                    new Sort.Order(Sort.Direction.DESC, "createdAt")
            );
            default -> List.of(
                    new Sort.Order(direction, "createdAt"),
                    new Sort.Order(Sort.Direction.DESC, "rating")
            );
        };
        return PageRequest.of(page, size, Sort.by(orders));
    }

    private List<UUID> normalizedTagIds(List<UUID> tagIds) {
        if (!hasTagFilter(tagIds)) {
            return List.of(EMPTY_TAG_ID);
        }
        return tagIds.stream().distinct().toList();
    }

    private boolean hasTagFilter(List<UUID> tagIds) {
        return tagIds != null && !tagIds.isEmpty();
    }

    private boolean isPostgresDataSource() {
        if (postgresDetected != null) {
            return postgresDetected;
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            postgresDetected = productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception ex) {
            postgresDetected = false;
        }
        return postgresDetected;
    }

    private org.springframework.data.domain.Page<UUID> findCandidatesByFts(
            String normalizedKeyword,
            MentorDiscoverySearchRequest safeRequest,
            List<UUID> tagIds,
            int requestedPage,
            int requestedSize,
            boolean relevanceSort
    ) {
        boolean hasTagFilter = hasTagFilter(safeRequest.getTagIds());
        String tagIdsArray = tagIds.isEmpty()
                ? "{00000000-0000-0000-0000-000000000000}"
                : "{" + tagIds.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";
        LocalDateTime now = currentTime();

        int fetchLimit = relevanceSort ? Math.min(200, (requestedPage + 1) * requestedSize * 5 + requestedSize) : requestedSize;
        int fetchOffset = relevanceSort ? 0 : requestedPage * requestedSize;

        List<UUID> ids = mentorProfileRepository.findDiscoverableCandidateIdsByFts(
                normalizedKeyword,
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                hasTagFilter,
                tagIdsArray,
                now,
                fetchLimit,
                fetchOffset
        );

        long totalCount = mentorProfileRepository.countDiscoverableCandidatesByFts(
                normalizedKeyword,
                safeRequest.getCampusId(),
                safeRequest.getSpecializationId(),
                hasTagFilter,
                tagIdsArray,
                now
        );

        return new org.springframework.data.domain.PageImpl<>(
                ids,
                org.springframework.data.domain.PageRequest.of(relevanceSort ? 0 : requestedPage, relevanceSort ? Math.max(fetchLimit, 1) : requestedSize),
                totalCount
        );
    }

    private String toLikePattern(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return null;
        }
        return "%" + normalized + "%";
    }

    private boolean sameUuid(UUID left, UUID right) {
        return left != null && left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasCompletedPeerMentorProfile(
            MentorProfile mentorProfile,
            List<MentorTagResponse> helpTopics,
            List<MentorSubjectResultResponse> subjectResults
    ) {
        return mentorProfile != null
                && hasText(mentorProfile.getHeadline())
                && hasText(mentorProfile.getExpertiseDescription())
                && hasText(mentorProfile.getPhoneNumber())
                && mentorProfile.getFoundationSupportLevel() != null
                && mentorProfile.getOutputReviewSupportLevel() != null
                && mentorProfile.getDirectionSupportLevel() != null
                && helpTopics != null
                && !helpTopics.isEmpty()
                && subjectResults != null
                && !subjectResults.isEmpty();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? ZERO : value;
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

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    @jakarta.annotation.PostConstruct
    public void initCache() {
        refreshKeywordsCache();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000)
    public void refreshKeywordsCache() {
        try {
            if (tagRepository == null || mentorServiceRepository == null) {
                return;
            }
            List<String> tags = tagRepository.findAll().stream()
                    .flatMap(tag -> Arrays.stream(new String[]{tag.getNameVi(), tag.getNameEn()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();

            List<String> serviceTitles = mentorServiceRepository.findAllActiveServiceTitles().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> subjects = mentorSubjectResultRepository.findAll().stream()
                    .flatMap(subject -> Arrays.stream(new String[]{subject.getSubjectCode(), subject.getSubjectName()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> projectTexts = mentorFeaturedProjectRepository.findAll().stream()
                    .flatMap(project -> Arrays.stream(new String[]{project.getTitle(), project.getContent(), project.getProjectDescription()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> achievementTexts = mentorAchievementRepository.findAll().stream()
                    .flatMap(achievement -> Arrays.stream(new String[]{achievement.getTitle(), achievement.getAwardDescription(), achievement.getProductHeader(), achievement.getProductDescription()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();

            List<String> merged = new ArrayList<>();
            merged.addAll(tags);
            merged.addAll(serviceTitles);
            merged.addAll(subjects);
            merged.addAll(projectTexts);
            merged.addAll(achievementTexts);
            List<String> deduplicated = merged.stream().distinct().collect(Collectors.toList());

            synchronized (cacheLock) {
                this.cachedKeywords = deduplicated;
            }
            log.info("Refreshed search keywords cache: {} keywords", deduplicated.size());
        } catch (Exception ex) {
            log.warn("Failed to refresh search keywords cache", ex);
        }
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[] dp = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            dp[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int temp = dp[j];
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = Math.min(Math.min(dp[j - 1], dp[j]), prev) + 1;
                }
                prev = temp;
            }
        }
        return dp[s2.length()];
    }

    private String correctSpelling(String normalizedKeyword) {
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return normalizedKeyword;
        }
        List<String> tokens = tokenizeSearchText(normalizedKeyword);
        if (tokens.isEmpty()) {
            return normalizedKeyword;
        }

        List<String> candidates;
        synchronized (cacheLock) {
            candidates = new ArrayList<>(this.cachedKeywords);
        }
        if (candidates.isEmpty()) {
            return normalizedKeyword;
        }

        List<String> correctedTokens = new ArrayList<>();
        boolean modified = false;

        for (String token : tokens) {
            if (token.length() > 30) {
                correctedTokens.add(token);
                continue;
            }

            String bestMatch = token;
            int bestDistance = Integer.MAX_VALUE;

            for (String candidate : candidates) {
                if (Math.abs(token.length() - candidate.length()) > 2) {
                    continue;
                }
                int distance = calculateLevenshteinDistance(token, candidate);
                int dynamicThreshold = Math.max(2, candidate.length() / 6);
                if (distance <= dynamicThreshold && distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = candidate;
                }
            }

            if (!bestMatch.equals(token)) {
                modified = true;
            }
            correctedTokens.add(bestMatch);
        }

        if (modified) {
            return String.join(" ", correctedTokens);
        }
        return normalizedKeyword;
    }



    private record RankedSearchCandidate(
            MentorDiscoveryQueryRow row,
            List<MentorTagResponse> helpTopics,
            BigDecimal score,
            BigDecimal matchScore
    ) {
    }
}
