package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.config.DiscoveryProperties;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.modules.blog.port.BlogQueryPort;
import com.fptu.exe.skillswap.modules.blog.port.BlogMentorArticlePreview;
import com.fptu.exe.skillswap.modules.catalog.repository.AdministrativeProvinceRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.port.FeedbackQueryPort;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorPublicAvailability;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRatingState;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.CandidatePage;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryCandidateProvider;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryEnrichmentService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryKeywordSupport;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryMapper;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryRankingService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.MentorEnrichedData;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.MentorRecommendationFacade;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorDiscoveryServiceTest {

    private static final UUID USER_ID = UUID.fromString("018f3abf-0a22-7e92-9748-6cf000c47b6e");
    private static final UUID MENTOR_USER_ID = UUID.fromString("018f3abf-0a22-7f12-9748-6cf000c47b6e");
    private static final UUID SECOND_MENTOR_USER_ID = UUID.fromString("018f3abf-0a22-7f32-9748-6cf000c47b6e");

    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private UserQueryPort userQueryPort;
    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorAvailabilityService mentorAvailabilityService;
    @Mock
    private FeedbackQueryPort feedbackQueryPort;

    @Mock
    private PaymentProperties paymentProperties;
    @Mock
    private InternalTelemetryService internalTelemetryService;
    @Mock
    private DiscoveryKeywordSupport discoveryKeywordSupport;
    @Mock
    private DiscoveryEnrichmentService discoveryEnrichmentService;
    @Mock
    private DiscoveryCandidateProvider discoveryCandidateProvider;
    @Mock
    private DiscoveryRankingService discoveryRankingService;
    @Mock
    private DiscoveryMapper discoveryMapper;
    @Mock
    private MentorBookingPolicyService mentorBookingPolicyService;
    @Mock
    private BlogQueryPort blogQueryPort;
    @Mock
    private AdministrativeProvinceRepository administrativeProvinceRepository;
    @Mock
    private EducationalInstitutionRepository educationalInstitutionRepository;
    @Mock
    private EducationFieldGroupRepository educationFieldGroupRepository;

    private MentorDiscoveryService mentorDiscoveryService;

    private StudentProfile studentProfile;
    private MentorProfile mentorProfile;

    private MentorRecommendationFacade mentorRecommendationFacade;

    @BeforeEach
    void setUp() {
        mentorRecommendationFacade = new MentorRecommendationFacade(
                userQueryPort,
                discoveryCandidateProvider,
                discoveryEnrichmentService,
                discoveryRankingService,
                discoveryMapper,
                new DiscoveryProperties(100, "structured-v1")
        );
        mentorDiscoveryService = new MentorDiscoveryService(
                mentorProfileRepository,
                userQueryPort,
                mentorServiceRepository,
                mentorAvailabilityService,
                feedbackQueryPort,
                paymentProperties,
                internalTelemetryService,
                discoveryKeywordSupport,
                discoveryEnrichmentService,
                discoveryCandidateProvider,
                discoveryMapper,
                new DiscoveryProperties(100, "structured-v1"),
                mentorRecommendationFacade,
                mentorBookingPolicyService,
                blogQueryPort,
                administrativeProvinceRepository,
                educationalInstitutionRepository,
                educationFieldGroupRepository
        );
        studentProfile = StudentProfile.builder().userId(USER_ID).profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(UUID.randomUUID()).fieldGroupId(UUID.randomUUID()).build();

        User mentorUser = new User();
        mentorUser.setId(MENTOR_USER_ID);
        mentorUser.setFullName("Mentor Full Name");
        mentorUser.setAvatarUrl("avatar.png");
        mentorUser.setStatus(UserStatus.ACTIVE);
        mentorUser.setRoles(Set.of(RoleCode.MENTOR));

        mentorProfile = MentorProfile.builder()
                .userId(MENTOR_USER_ID)
                .status(MentorStatus.ACTIVE)
                .headline("Java mentor")
                .expertiseDescription("Java backend mentoring")
                .phoneNumber("0900000000")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .isAvailable(true)
                .verifiedAt(LocalDateTime.now().minusDays(2))
                .averageRating(BigDecimal.valueOf(4.5))
                .totalReviews(4)
                .totalCompletedSessions(5)
                .build();
        lenient().when(userQueryPort.findUserSummaryById(MENTOR_USER_ID)).thenReturn(Optional.of(
                new UserSummaryRecord(
                        MENTOR_USER_ID,
                        "mentor@example.com",
                        "Mentor Full Name",
                        "avatar.png",
                        Set.of(RoleCode.MENTOR),
                        "ACTIVE",
                        true
                )));
    }

    @Test
    void searchMentors_usesDatabasePageWithoutJavaRanking() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setPage(0);
        request.setSize(5);
        request.setKeyword("spring boot");
        when(discoveryKeywordSupport.normalizeSearchText("spring boot")).thenReturn("spring boot");
        when(discoveryKeywordSupport.toLikePattern("spring boot")).thenReturn("%spring boot%");
        MentorDiscoveryQueryRow row = discoveryRow(MENTOR_USER_ID, "Spring Boot mentor");
        when(discoveryCandidateProvider.searchPage(eq(request), eq("%spring boot%"), eq("%spring boot%"), anyList(), any()))
                .thenReturn(new CandidatePage(List.of(MENTOR_USER_ID), 1));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(MENTOR_USER_ID))).thenReturn(List.of(row));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        when(discoveryMapper.toCardResponseFromEnriched(eq(row), any(), isNull()))
                .thenReturn(MentorDiscoveryCardResponse.builder().mentorUserId(MENTOR_USER_ID).matchScore(null).build());

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(null, request);

        assertEquals(1, response.getContent().size());
        assertEquals(1, response.getTotalElements());
        assertNull(response.getContent().getFirst().matchScore());
        verify(discoveryCandidateProvider).searchPage(eq(request), eq("%spring boot%"), eq("%spring boot%"), anyList(), any());
        verifyNoInteractions(discoveryRankingService);
        verify(discoveryEnrichmentService).loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class));
    }

    @Test
    void searchMentors_capsPageSizeAtFiftyAndReportsDatabaseTotal() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setSize(999);
        when(discoveryKeywordSupport.normalizeSearchText(nullable(String.class))).thenReturn("");
        when(discoveryKeywordSupport.toLikePattern(nullable(String.class))).thenReturn(null);
        when(discoveryCandidateProvider.searchPage(eq(request), isNull(), isNull(), anyList(), any()))
                .thenReturn(new CandidatePage(List.of(), 101));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of()), any(LocalDateTime.class))).thenReturn(Map.of());

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(null, request);

        assertEquals(50, response.getSize());
        assertEquals(3, response.getTotalPages());
        assertEquals(101, response.getTotalElements());
    }

    @Test
    void getRecommendations_shouldUseProviderEnrichmentAndRanking() {
        stubSearchContext();

        MentorDiscoveryQueryRow row = discoveryRow(MENTOR_USER_ID, "Recommendation mentor");
        when(discoveryCandidateProvider.recallForRecommendation(eq(USER_ID), anyBoolean(), anyInt(), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        DiscoveryRankingService.RecommendationScore recScore = new DiscoveryRankingService.RecommendationScore(new BigDecimal("77.00"), List.of("Khớp nhu cầu mentoring"));
        when(discoveryRankingService.scoreRecommendation(eq(row), eq(MentorEnrichedData.empty()), eq(studentProfile), any(LocalDateTime.class)))
                .thenReturn(recScore);
        when(discoveryMapper.toRecommendation(any(), any(), any())).thenReturn(MentorRecommendationResponse.builder()
                .matchScore(new BigDecimal("77.00"))
                .mentor(MentorDiscoveryCardResponse.builder().completedSessions(5).build())
                .build());

        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(USER_ID, 3);

        assertEquals(1, recommendations.size());
        assertEquals(new BigDecimal("77.00"), recommendations.getFirst().matchScore());
        verify(discoveryCandidateProvider).recallForRecommendation(eq(USER_ID), anyBoolean(), anyInt(), any(LocalDateTime.class), anyInt());
        verify(discoveryRankingService).scoreRecommendation(eq(row), eq(MentorEnrichedData.empty()), eq(studentProfile), any(LocalDateTime.class));
    }

    @Test
    void getRecommendations_shouldKeepRankingTieBreakerIndependentFromNullableDisplayRating() {
        stubSearchContext();
        MentorDiscoveryQueryRow rated = discoveryRow(SECOND_MENTOR_USER_ID, "Rated mentor", new BigDecimal("4.80"), 10, 12);
        MentorDiscoveryQueryRow noReviews = discoveryRow(MENTOR_USER_ID, "New mentor", new BigDecimal("5.00"), 0, 12);
        DiscoveryRankingService.RecommendationScore score = new DiscoveryRankingService.RecommendationScore(new BigDecimal("77.00"), List.of());

        when(discoveryCandidateProvider.recallForRecommendation(eq(USER_ID), anyBoolean(), anyInt(), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(rated, noReviews));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(SECOND_MENTOR_USER_ID, MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(SECOND_MENTOR_USER_ID, MentorEnrichedData.empty(), MENTOR_USER_ID, MentorEnrichedData.empty()));
        when(discoveryRankingService.scoreRecommendation(eq(rated), eq(MentorEnrichedData.empty()), eq(studentProfile), any(LocalDateTime.class)))
                .thenReturn(score);
        when(discoveryRankingService.scoreRecommendation(eq(noReviews), eq(MentorEnrichedData.empty()), eq(studentProfile), any(LocalDateTime.class)))
                .thenReturn(score);
        when(discoveryMapper.toRecommendation(eq(rated), any(), any())).thenReturn(MentorRecommendationResponse.builder()
                .matchScore(score.matchScore())
                .mentor(MentorDiscoveryCardResponse.builder().mentorUserId(SECOND_MENTOR_USER_ID).ratingAverage(new BigDecimal("4.80")).build())
                .build());
        when(discoveryMapper.toRecommendation(eq(noReviews), any(), any())).thenReturn(MentorRecommendationResponse.builder()
                .matchScore(score.matchScore())
                .mentor(MentorDiscoveryCardResponse.builder().mentorUserId(MENTOR_USER_ID).ratingState(MentorRatingState.NO_REVIEWS).ratingAverage(null).build())
                .build());

        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(USER_ID, 2);

        assertEquals(MENTOR_USER_ID, recommendations.getFirst().mentor().mentorUserId());
    }

    @Test
    void getMentorDetail_shouldMapServicesAndDisplayRating() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(userQueryPort.findStudentProfileWithDetailsByUserId(MENTOR_USER_ID)).thenReturn(Optional.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, new MentorEnrichedData(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        false
                )));
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(MENTOR_USER_ID))
                .thenReturn(List.of(MentorService.builder()
                        .id(UUID.fromString("018f3abf-0a22-7fd2-9748-6cf000c47b6e"))
                        .mentorProfile(mentorProfile)
                        .title("CV Review")
                        .description("Review CV")
                        .durationMinutes(60)
                        .isFree(true)
                        .priceScoin(0)
                        .build()));
        when(discoveryMapper.toServiceResponse(any())).thenReturn(MentorServiceResponse.builder()
                .title("CV Review")
                .active(true)
                .build());
        when(mentorBookingPolicyService.isPublicBookingOfferAvailable(eq(mentorProfile), eq(true), any(LocalDateTime.class)))
                .thenReturn(true);
        when(blogQueryPort.findMentorPublicProfilePreviews(eq(MENTOR_USER_ID), anyInt())).thenReturn(List.of());

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID);

        assertEquals("Mentor Full Name", response.identity().displayName());
        assertEquals(1, response.services().size());
        assertEquals(MentorRatingState.RATED, response.reputation().ratingState());
        assertEquals(0, response.reputation().ratingAverage().compareTo(new BigDecimal("4.50")));
        assertTrue(response.evidence().authorityContent().recentPublicArticles().isEmpty());
        assertTrue(response.availability().canRequestBooking());
        verify(mentorBookingPolicyService).isPublicBookingOfferAvailable(eq(mentorProfile), eq(true), any(LocalDateTime.class));
    }

    @Test
    void getMentorDetail_currentSchoolProfileDoesNotProjectLegacyUniversityFields() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(userQueryPort.findStudentProfileWithDetailsByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(StudentProfile.builder()
                .userId(MENTOR_USER_ID).profileType(StudentProfileType.SCHOOL_STUDENT)
                .customInstitutionName("THPT Example").build()));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(MENTOR_USER_ID)).thenReturn(List.of());
        when(mentorBookingPolicyService.isPublicBookingOfferAvailable(eq(mentorProfile), eq(false), any(LocalDateTime.class)))
                .thenReturn(false);
        when(blogQueryPort.findMentorPublicProfilePreviews(eq(MENTOR_USER_ID), eq(3))).thenReturn(List.of());

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID);

    }

    @Test
    void getMentorDetail_noReviewsShouldExposeNullDisplayRating() {
        mentorProfile.setTotalReviews(0);
        mentorProfile.setAverageRating(new BigDecimal("5.00"));
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(userQueryPort.findStudentProfileWithDetailsByUserId(MENTOR_USER_ID)).thenReturn(Optional.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(MENTOR_USER_ID)).thenReturn(List.of());
        when(mentorBookingPolicyService.isPublicBookingOfferAvailable(eq(mentorProfile), eq(false), any(LocalDateTime.class)))
                .thenReturn(false);
        when(blogQueryPort.findMentorPublicProfilePreviews(eq(MENTOR_USER_ID), anyInt())).thenReturn(List.of());

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID);

        assertEquals(MentorRatingState.NO_REVIEWS, response.reputation().ratingState());
        assertNull(response.reputation().ratingAverage());
        assertEquals(0, response.reputation().reviewCount());
        assertEquals(5, response.reputation().completedSessions());
        assertTrue(response.evidence().authorityContent().recentPublicArticles().isEmpty());
        assertTrue(!response.availability().canRequestBooking());
    }

    @Test
    void getMentorDetail_shouldLoadAtMostThreePublicMentorArticlePreviews() {
        BlogMentorArticlePreview preview = new BlogMentorArticlePreview(
                UUID.randomUUID(), "Spring Boot guide", "spring-boot-guide", "Excerpt", null, 4, LocalDateTime.now());
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(userQueryPort.findStudentProfileWithDetailsByUserId(MENTOR_USER_ID)).thenReturn(Optional.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(MENTOR_USER_ID)).thenReturn(List.of());
        when(mentorBookingPolicyService.isPublicBookingOfferAvailable(eq(mentorProfile), eq(false), any(LocalDateTime.class)))
                .thenReturn(false);
        when(blogQueryPort.findMentorPublicProfilePreviews(eq(MENTOR_USER_ID), eq(3)))
                .thenReturn(List.of(preview));

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID);

        var article = response.evidence().authorityContent().recentPublicArticles().getFirst();
        assertEquals(preview.id(), article.id());
        assertEquals(preview.title(), article.title());
        assertEquals(preview.slug(), article.slug());
        assertEquals(preview.excerpt(), article.excerpt());
        verify(blogQueryPort).findMentorPublicProfilePreviews(eq(MENTOR_USER_ID), eq(3));
    }

    @Test
    void getMentorAvailability_availableMentor_shouldDelegateService() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilityService.getAvailableSlots(eq(MENTOR_USER_ID), any(), any()))
                .thenReturn(List.of(new MentorPublicAvailability(
                        UUID.fromString("018f3abf-0a22-7ff2-9748-6cf000c47b6e"),
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1),
                        "Asia/Ho_Chi_Minh", 60, 0, 0, 3, 3, List.of())));

        List<MentorAvailabilitySlotResponse> response =
                mentorDiscoveryService.getMentorAvailability(MENTOR_USER_ID, new AvailabilityQueryRequest());

        assertEquals(1, response.size());
        verify(mentorAvailabilityService).getAvailableSlots(eq(MENTOR_USER_ID), any(), any());
    }

    @Test
    void getMentorReviews_shouldReturnPagedPublicReviews() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(feedbackQueryPort.findPublicMentorReviews(eq(MENTOR_USER_ID), any(Integer.class), any(Integer.class)))
                .thenReturn(PageResponse.<com.fptu.exe.skillswap.modules.feedback.port.MentorReviewProjection>builder().content(List.of(
                        new com.fptu.exe.skillswap.modules.feedback.port.MentorReviewProjection(
                                UUID.fromString("018f3abf-0a22-7012-9748-6cf000c47b6e"),
                                UUID.fromString("018f3abf-0a22-7032-9748-6cf000c47b6e"),
                                "Reviewer",
                                "avatar",
                                5,
                                "Great",
                                LocalDateTime.now()
                        )
                )).page(0).size(20).totalElements(1).totalPages(1).last(true).build());
        when(discoveryMapper.toMentorReviewResponse(any())).thenReturn(MentorReviewResponse.builder()
                .reviewerDisplayName("Reviewer")
                .build());

        PageResponse<MentorReviewResponse> response =
                mentorDiscoveryService.getMentorReviews(MENTOR_USER_ID, new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("Reviewer", response.getContent().getFirst().reviewerDisplayName());
    }

    private void stubSearchContext() {
        lenient().when(userQueryPort.findStudentProfileWithDetailsByUserId(USER_ID)).thenReturn(Optional.of(studentProfile));
        lenient().when(discoveryKeywordSupport.normalizeSearchText(nullable(String.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return value == null ? "" : value.trim().toLowerCase();
        });
        lenient().when(discoveryKeywordSupport.toLikePattern(nullable(String.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toLowerCase();
            return normalized.isBlank() ? null : "%" + normalized + "%";
        });
        lenient().when(discoveryKeywordSupport.correctSpelling(nullable(String.class))).thenAnswer(invocation -> invocation.getArgument(0, String.class));
    }

    private MentorDiscoveryQueryRow discoveryRow(UUID id, String headline) {
        return discoveryRow(id, headline, BigDecimal.valueOf(4.6), 8, 12);
    }

    private MentorDiscoveryQueryRow discoveryRow(
            UUID id,
            String headline,
            BigDecimal ratingAverage,
            int reviewCount,
            int completedSessions
    ) {
        return new MentorDiscoveryQueryRow(
                id,
                "Mentor " + id,
                "avatar.png",
                headline,
                "Java backend coaching",
                "Bio",
                3,
                3,
                2,
                true,
                ratingAverage,
                reviewCount,
                completedSessions,
                LocalDateTime.now().minusDays(5),
                12,
                2,
                0,
                LocalDateTime.now().minusDays(3),
                null
        );
    }
}
