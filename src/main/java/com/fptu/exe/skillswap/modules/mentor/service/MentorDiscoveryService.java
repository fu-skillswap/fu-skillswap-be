package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.identity.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.identity.domain.Campus;
import com.fptu.exe.skillswap.modules.identity.domain.Specialization;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilitySlotServiceBasicResponse;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.booking.service.BookingEligibilityPolicy;
import com.fptu.exe.skillswap.modules.blog.domain.BlogAuthorType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogPostRepository;
import com.fptu.exe.skillswap.modules.blog.service.BlogMapper;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAuthorityContentResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;

import com.fptu.exe.skillswap.infrastructure.config.DiscoveryProperties;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorPublicAvailabilityPreviewResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorFeaturedProjectResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAchievementResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilityResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorEducationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorEvidenceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorIdentityResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorMentoringResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRatingState;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorReputationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSupportLevelsResponse;
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
import com.fptu.exe.skillswap.modules.mentor.service.discovery.MentorRecommendationFacade;
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
    private final BlogPostRepository blogPostRepository;
    private final BlogMapper blogMapper;
    private final BookingEligibilityPolicy bookingEligibilityPolicy;

    private final PaymentProperties paymentProperties;
    private final InternalTelemetryService internalTelemetryService;
    private final DiscoveryKeywordSupport discoveryKeywordSupport;
    private final DiscoveryEnrichmentService discoveryEnrichmentService;
    private final DiscoveryCandidateProvider discoveryCandidateProvider;
    private final DiscoveryRankingService discoveryRankingService;
    private final DiscoveryMapper discoveryMapper;

    private final DiscoveryProperties discoveryProperties;
    private final MentorRecommendationFacade mentorRecommendationFacade;

    @Transactional(readOnly = true)
    public PageResponse<MentorDiscoveryCardResponse> searchMentors(UUID currentUserId, MentorDiscoverySearchRequest request) {
        MentorDiscoverySearchRequest safeRequest = request == null ? new MentorDiscoverySearchRequest() : request;
        StudentProfile menteeProfile = loadStudentProfileSafely(currentUserId);

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
                discoveryProperties.recallWindowSize()
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
                        discoveryProperties.recallWindowSize()
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

        LocalDateTime evaluatedAt = currentTime();
        List<MentorDiscoveryCardResponse> content;
        if (relevanceSort) {
            Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(candidateIds, evaluatedAt);
            List<DiscoveryRankingService.RankedSearchCandidate> rankedCandidates = discoveryRankingService.rankSearchCandidates(
                    rows,
                    menteeProfile,

                    normalizedKeyword,
                    enrichedDataByMentor,
                    evaluatedAt
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

            Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(pageMentorIds, evaluatedAt);

            List<DiscoveryRankingService.RankedSearchCandidate> rankedPageRows = discoveryRankingService.rankSearchCandidates(
                    pageRows,
                    menteeProfile,

                    normalizedKeyword,
                    enrichedDataByMentor,
                    evaluatedAt
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

    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        return mentorRecommendationFacade.getRecommendations(currentUserId, limit);
    }

    @Transactional(readOnly = true)
    public MentorDiscoveryDetailResponse getMentorDetail(UUID mentorUserId) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        internalTelemetryService.record("MENTOR_VIEWED", null, "MENTOR", mentorUserId, Map.of());
        StudentProfile studentProfile = studentProfileRepository.findWithDetailsByUserId(mentorUserId).orElse(null);
        MentorEnrichedData enrichedData = discoveryEnrichmentService.loadMentorEnrichedData(List.of(mentorUserId), currentTime())
                .getOrDefault(mentorUserId, MentorEnrichedData.empty());
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

        int reviews = defaultInteger(mentorProfile.getTotalReviews());
        boolean hasActiveServices = services.stream().anyMatch(MentorServiceResponse::isActive);
        boolean canRequestBooking = bookingEligibilityPolicy.isPublicBookingOfferAvailable(
                mentorProfile, hasActiveServices, currentTime());
        BlogPostRepository.MentorPublicAuthorityProjection authority = blogPostRepository.getMentorPublicAuthority(mentorUserId);
        List<com.fptu.exe.skillswap.modules.mentor.dto.response.MentorPublicArticlePreviewResponse> recentPublicArticles =
                blogPostRepository.findMentorPublicProfilePreviews(
                                mentorUserId,
                                BlogAuthorType.MENTOR,
                                BlogPostStatus.PUBLISHED,
                                BlogVisibility.PUBLIC,
                                PageRequest.of(0, 3))
                        .stream()
                        .map(blogMapper::toMentorPublicArticlePreview)
                        .toList();

        return MentorDiscoveryDetailResponse.builder()
                .identity(new MentorIdentityResponse(
                        mentorProfile.getUserId(), mentorProfile.getUser().getFullName(), mentorProfile.getUser().getAvatarUrl(),
                        mentorProfile.getHeadline(), mentorProfile.getVerifiedAt() != null, mentorProfile.getVerifiedAt()))
                .mentoring(new MentorMentoringResponse(
                        studentProfile == null ? null : studentProfile.getBio(),
                        mentorProfile.getExpertiseDescription(),
                        new MentorSupportLevelsResponse(
                                mentorProfile.getFoundationSupportLevel(), mentorProfile.getOutputReviewSupportLevel(),
                                mentorProfile.getDirectionSupportLevel())))
                .services(services)
                .evidence(new MentorEvidenceResponse(
                        new MentorEducationResponse(
                                campus == null ? null : campus.getId(), campus == null ? null : campus.getName(),
                                program == null ? null : program.getId(), program == null ? null : program.getNameVi(),
                                specialization == null ? null : specialization.getId(), specialization == null ? null : specialization.getNameVi(),
                                studentProfile == null ? null : studentProfile.getSemester(),
                                studentProfile != null && studentProfile.isAlumni()),
                        subjectResults, featuredProjects, achievements, mentorProfile.getPortfolioUrl(), mentorProfile.getGithubUrl(),
                        new MentorAuthorityContentResponse(
                                authority == null ? 0L : authority.getPublishedArticleCount(),
                                authority == null ? null : authority.getLatestPublishedAt(), recentPublicArticles)))
                .reputation(new MentorReputationResponse(
                        reviews == 0 ? MentorRatingState.NO_REVIEWS : MentorRatingState.RATED,
                        reviews == 0 ? null : mentorProfile.getAverageRating(),
                        reviews, defaultInteger(mentorProfile.getTotalCompletedSessions())))
                .availability(new MentorAvailabilityResponse(
                        mentorProfile.isAvailable(), mentorProfile.getBookingSuspendedUntil(), canRequestBooking))
                .build();
    }

    @Transactional
    public List<MentorAvailabilitySlotResponse> getMentorAvailability(UUID mentorUserId, AvailabilityQueryRequest request) {
        internalTelemetryService.record("AVAILABILITY_OPENED", null, "MENTOR", mentorUserId, Map.of());
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            return List.of();
        }
        AvailabilityQueryRequest safeRequest = request == null ? new AvailabilityQueryRequest() : request;
        return mentorAvailabilityService.getAvailableSlots(mentorProfile, safeRequest.getFromDate(), safeRequest.getToDate());
    }

    /**
     * Bản xem trước lịch an toàn cho khách. Không trả slot ID hay các bộ đếm booking;
     * thông tin ứng viên chi tiết chỉ được mở sau khi đăng nhập.
     */
    @Transactional(readOnly = true)
    public MentorPublicAvailabilityPreviewResponse getPublicAvailabilityPreview(
            UUID mentorUserId,
            AvailabilityQueryRequest request
    ) {
        MentorProfile mentorProfile = getDiscoverableMentorProfile(mentorUserId);
        if (isBookingSuspended(mentorProfile)) {
            return new MentorPublicAvailabilityPreviewResponse("Asia/Ho_Chi_Minh", false, null, List.of());
        }

        AvailabilityQueryRequest safeRequest = request == null ? new AvailabilityQueryRequest() : request;
        List<MentorAvailabilitySlotResponse> slots = mentorAvailabilityService.getAvailableSlots(
                mentorProfile, safeRequest.getFromDate(), safeRequest.getToDate());
        boolean hasActiveService = mentorServiceRepository
                .findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId)
                .stream()
                .anyMatch(service -> service.getDeliveryMode() == com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE);
        boolean offerAvailable = bookingEligibilityPolicy.isPublicBookingOfferAvailable(
                mentorProfile, hasActiveService, currentTime()) && !slots.isEmpty();

        List<MentorPublicAvailabilityPreviewResponse.Slot> previewSlots = slots.stream()
                .map(slot -> new MentorPublicAvailabilityPreviewResponse.Slot(
                        slot.startTime(),
                        slot.endTime(),
                        slot.services().stream()
                                .map(this::toPublicAvailabilityPreviewService)
                                .toList()))
                .toList();
        LocalDateTime nextAvailableAt = previewSlots.isEmpty() ? null : previewSlots.getFirst().startTime();
        return new MentorPublicAvailabilityPreviewResponse(
                "Asia/Ho_Chi_Minh", offerAvailable, nextAvailableAt, previewSlots);
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

    private MentorPublicAvailabilityPreviewResponse.Service toPublicAvailabilityPreviewService(
            AvailabilitySlotServiceBasicResponse service
    ) {
        return new MentorPublicAvailabilityPreviewResponse.Service(
                service.serviceId(), service.title(), service.durationMinutes(), service.isFree(), service.priceScoin());
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

    private Integer defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
