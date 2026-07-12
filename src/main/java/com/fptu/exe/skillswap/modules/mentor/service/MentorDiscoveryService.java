package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatureProvider;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.CandidateWindow;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryCandidateProvider;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryEnrichmentService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryKeywordSupport;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryRankingService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.MentorEnrichedData;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryMapper;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MentorDiscoveryService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityService mentorAvailabilityService;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final MenteeMatchingFeatureProvider menteeMatchingFeatureProvider;
    private final PaymentProperties paymentProperties;
    private final InternalTelemetryService internalTelemetryService;
    private final DiscoveryKeywordSupport discoveryKeywordSupport;
    private final DiscoveryEnrichmentService discoveryEnrichmentService;
    private final DiscoveryCandidateProvider discoveryCandidateProvider;
    private final DiscoveryRankingService discoveryRankingService;
    private final DiscoveryMapper discoveryMapper;

    @Value("${application.discovery.recall-window-size:100}")
    private int defaultRecallWindowSize = 100;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        MenteeMatchingFeatures menteeFeatures = currentUserId == null ? null : menteeMatchingFeatureProvider.getLatestFeatures(currentUserId);
        boolean hasKeyword = safeRequest.getKeyword() != null && !safeRequest.getKeyword().isBlank();
        String normalizedKeyword = discoveryKeywordSupport.normalizeSearchText(safeRequest.getKeyword());
        String keywordPattern = discoveryKeywordSupport.toLikePattern(safeRequest.getKeyword());
        String normalizedKeywordPattern = discoveryKeywordSupport.toLikePattern(normalizedKeyword);

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
        CandidateWindow candidateWindow = discoveryCandidateProvider.recallForSearch(
                safeRequest,
                normalizedKeyword,
                keywordPattern,
                normalizedKeywordPattern,
                relevanceSort,
                orders,
                currentTime(),
                defaultRecallWindowSize
        );

        if (hasKeyword && candidateWindow.isEmpty()) {
            String corrected = discoveryKeywordSupport.correctSpelling(normalizedKeyword);
            if (!corrected.equals(normalizedKeyword)) {
                log.info("Original search keyword '{}' produced 0 results. Fallback fuzzy search using corrected spelling: '{}'", normalizedKeyword, corrected);
                candidateWindow = discoveryCandidateProvider.recallForSearch(
                        safeRequest,
                        corrected,
                        discoveryKeywordSupport.toLikePattern(corrected),
                        discoveryKeywordSupport.toLikePattern(corrected),
                        relevanceSort,
                        orders,
                        currentTime(),
                        defaultRecallWindowSize
                );
            }
        }
        if (hasKeyword && candidateWindow.isEmpty()) {
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

        List<UUID> candidateIds = candidateWindow.candidateIds();
        List<MentorDiscoveryQueryRow> rows = loadDiscoveryRowsInPageOrder(candidateIds);
        if (rows.isEmpty()) {
            return PageResponse.<MentorDiscoveryCardResponse>builder()
                    .content(List.of())
                    .page(requestedPage)
                    .size(requestedSize)
                    .totalElements(candidateWindow.totalCount())
                    .totalPages(totalPages(candidateWindow.totalCount(), requestedSize))
                    .last(isLastPage(requestedPage, requestedSize, candidateWindow.totalCount()))
                    .build();
        }

        List<MentorDiscoveryCardResponse> content;
        if (relevanceSort) {
            Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(candidateIds, menteeFeatures, currentTime());
            List<DiscoveryRankingService.RankedSearchCandidate> rankedCandidates = discoveryRankingService.rankSearchCandidates(
                    rows,
                    menteeProfile,
                    menteeFeatures,
                    normalizedKeyword,
                    enrichedDataByMentor
            );

            int fromIndex = Math.min(requestedPage * requestedSize, rankedCandidates.size());
            int toIndex = Math.min(fromIndex + requestedSize, rankedCandidates.size());
            content = rankedCandidates.subList(fromIndex, toIndex).stream()
                    .map(candidate -> discoveryMapper.toCardResponseFromEnriched(candidate.row(), candidate.enrichedData(), candidate.matchScore()))
                    .toList();
        } else {
            List<MentorDiscoveryQueryRow> sortedRows = discoveryRankingService.sortRowsForRequestedSort(rows, sortBy, direction);
            int fromIndex = Math.min(requestedPage * requestedSize, sortedRows.size());
            int toIndex = Math.min(fromIndex + requestedSize, sortedRows.size());
            List<MentorDiscoveryQueryRow> pageRows = sortedRows.subList(fromIndex, toIndex);
            List<UUID> pageMentorIds = pageRows.stream()
                    .map(MentorDiscoveryQueryRow::mentorUserId)
                    .toList();

            Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(pageMentorIds, menteeFeatures, currentTime());

            List<DiscoveryRankingService.RankedSearchCandidate> rankedPageRows = discoveryRankingService.rankSearchCandidates(
                    pageRows,
                    menteeProfile,
                    menteeFeatures,
                    normalizedKeyword,
                    enrichedDataByMentor
            );
            content = rankedPageRows.stream()
                    .map(candidate -> discoveryMapper.toCardResponseFromEnriched(candidate.row(), candidate.enrichedData(), candidate.matchScore()))
                    .toList();
        }

        return PageResponse.<MentorDiscoveryCardResponse>builder()
                .content(content)
                .page(requestedPage)
                .size(requestedSize)
                .totalElements(candidateWindow.totalCount())
                .totalPages(totalPages(candidateWindow.totalCount(), requestedSize))
                .last(isLastPage(requestedPage, requestedSize, candidateWindow.totalCount()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);
        MenteeMatchingFeatures menteeFeatures = menteeMatchingFeatureProvider.getLatestFeatures(currentUserId);
        LocalDateTime now = currentTime();
        boolean richProfile = menteeProfile != null
                && menteeProfile.getProgram() != null
                && menteeProfile.getSpecialization() != null;
        List<MentorDiscoveryQueryRow> candidates = discoveryCandidateProvider.recallForRecommendation(
                currentUserId,
                richProfile,
                safeLimit,
                now,
                defaultRecallWindowSize
        );

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList();
        Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(candidateIds, menteeFeatures, now);

        return candidates.stream()
                .map(candidate -> {
                    MentorEnrichedData enrichedData = enrichedDataByMentor.getOrDefault(candidate.mentorUserId(), MentorEnrichedData.empty());
                    DiscoveryRankingService.RecommendationScore recommendationScore = discoveryRankingService.scoreRecommendation(
                            candidate,
                            enrichedData,
                            menteeProfile,
                            menteeFeatures
                    );
                    return discoveryMapper.toRecommendation(candidate, enrichedData, recommendationScore);
                })
                .sorted(Comparator
                        .comparing(MentorRecommendationResponse::matchScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
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
        MentorEnrichedData enrichedData = discoveryEnrichmentService.loadMentorEnrichedData(List.of(mentorUserId), null, currentTime())
                .getOrDefault(mentorUserId, MentorEnrichedData.empty());
        List<MentorTagResponse> mentorTags = enrichedData.helpTopics();
        List<MentorSubjectResultResponse> subjectResults = enrichedData.subjectResults();
        List<MentorFeaturedProjectResponse> featuredProjects = enrichedData.featuredProjects();
        List<MentorAchievementResponse> achievements = enrichedData.achievements();
        List<MentorServiceResponse> services = mentorServiceRepository
                .findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .map(discoveryMapper::toServiceResponse)
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
                .helpTopicTags(discoveryMapper.filterTagsByType(mentorTags, MentorTagType.HELP_TOPIC))
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
                .content(page.getContent().stream().map(discoveryMapper::toMentorReviewResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private StudentProfile loadStudentProfileSafely(UUID currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);
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

    private boolean isBookingSuspended(MentorProfile mentorProfile) {
        return mentorProfile != null
                && mentorProfile.getBookingSuspendedUntil() != null
                && mentorProfile.getBookingSuspendedUntil().isAfter(currentTime());
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(APP_ZONE);
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
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
