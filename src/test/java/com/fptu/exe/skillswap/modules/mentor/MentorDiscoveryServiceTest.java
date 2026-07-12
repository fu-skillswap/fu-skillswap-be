package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
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
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.CandidateWindow;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryCandidateProvider;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryEnrichmentService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryKeywordSupport;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryRankingService;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.MentorEnrichedData;
import com.fptu.exe.skillswap.modules.mentor.service.discovery.DiscoveryMapper;
import com.fptu.exe.skillswap.modules.system.service.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorDiscoveryServiceTest {

    private static final UUID USER_ID = UUID.fromString("018f3abf-0a22-7e92-9748-6cf000c47b6e");
    private static final UUID MENTOR_USER_ID = UUID.fromString("018f3abf-0a22-7f12-9748-6cf000c47b6e");
    private static final UUID SECOND_MENTOR_USER_ID = UUID.fromString("018f3abf-0a22-7f32-9748-6cf000c47b6e");

    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorAvailabilityService mentorAvailabilityService;
    @Mock
    private SessionFeedbackRepository sessionFeedbackRepository;
    @Mock
    private MenteeMatchingFeatureProvider menteeMatchingFeatureProvider;
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

    private MentorDiscoveryService mentorDiscoveryService;

    private StudentProfile studentProfile;
    private Campus campus;
    private AcademicProgram program;
    private Specialization specialization;
    private MentorProfile mentorProfile;

    @BeforeEach
    void setUp() {
        mentorDiscoveryService = new MentorDiscoveryService(
                mentorProfileRepository,
                studentProfileRepository,
                mentorServiceRepository,
                mentorAvailabilityService,
                sessionFeedbackRepository,
                menteeMatchingFeatureProvider,
                paymentProperties,
                internalTelemetryService,
                discoveryKeywordSupport,
                discoveryEnrichmentService,
                discoveryCandidateProvider,
                discoveryRankingService,
                discoveryMapper
        );
        campus = new Campus();
        campus.setId(UUID.fromString("018f3abf-0a22-7f52-9748-6cf000c47b6e"));
        campus.setName("HCM");

        program = new AcademicProgram();
        program.setId(UUID.fromString("018f3abf-0a22-7f72-9748-6cf000c47b6e"));
        program.setNameVi("Software Engineering");

        specialization = new Specialization();
        specialization.setId(UUID.fromString("018f3abf-0a22-7f92-9748-6cf000c47b6e"));
        specialization.setNameVi("Backend");

        studentProfile = new StudentProfile();
        studentProfile.setUserId(USER_ID);
        studentProfile.setCampus(campus);
        studentProfile.setProgram(program);
        studentProfile.setSpecialization(specialization);
        studentProfile.setSemester(5);

        User mentorUser = new User();
        mentorUser.setId(MENTOR_USER_ID);
        mentorUser.setFullName("Mentor Full Name");
        mentorUser.setAvatarUrl("avatar.png");
        mentorUser.setStatus(UserStatus.ACTIVE);
        mentorUser.setRoles(Set.of(RoleCode.MENTOR));

        mentorProfile = MentorProfile.builder()
                .userId(MENTOR_USER_ID)
                .user(mentorUser)
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
    }

    @Test
    void searchMentors_anonymous_shouldUseNonPersonalizedDiscovery() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setPage(0);
        request.setSize(5);
        when(discoveryKeywordSupport.normalizeSearchText(nullable(String.class))).thenReturn("");
        when(discoveryKeywordSupport.toLikePattern(nullable(String.class))).thenReturn(null);
        when(discoveryCandidateProvider.recallForSearch(eq(request), eq(""), isNull(), isNull(), eq(true), anyList(), any(), anyInt()))
                .thenReturn(new CandidateWindow(List.of(), 0));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(null, request);

        assertTrue(response.getContent().isEmpty());
        verify(menteeMatchingFeatureProvider, never()).getLatestFeatures(any());
        verify(studentProfileRepository, never()).findWithDetailsByUserId(any());
    }

    @Test
    void searchMentors_relevanceSort_shouldDelegateToCollaborators() {
        stubSearchContext();

        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("spring boot");
        request.setSortBy("relevance");
        request.setPage(0);
        request.setSize(5);

        MentorDiscoveryQueryRow row = discoveryRow(MENTOR_USER_ID, "Spring Boot mentor");
        when(discoveryCandidateProvider.recallForSearch(eq(request), eq("spring boot"), eq("%spring boot%"), eq("%spring boot%"), eq(true), anyList(), any(), anyInt()))
                .thenReturn(new CandidateWindow(List.of(MENTOR_USER_ID), 1));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(MENTOR_USER_ID))).thenReturn(List.of(row));
        Map<UUID, MentorEnrichedData> enriched = Map.of(MENTOR_USER_ID, MentorEnrichedData.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(MenteeMatchingFeatures.class), any(LocalDateTime.class)))
                .thenReturn(enriched);
        when(discoveryRankingService.rankSearchCandidates(eq(List.of(row)), eq(studentProfile), any(MenteeMatchingFeatures.class), eq("spring boot"), eq(enriched)))
                .thenReturn(List.of(new DiscoveryRankingService.RankedSearchCandidate(row, MentorEnrichedData.empty(), new BigDecimal("80.00"), new BigDecimal("88.00"))));
        when(discoveryMapper.toCardResponseFromEnriched(any(), any(), any())).thenReturn(MentorDiscoveryCardResponse.builder()
                .mentorUserId(MENTOR_USER_ID)
                .matchScore(new BigDecimal("88.00"))
                .build());

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(USER_ID, request);

        assertEquals(1, response.getContent().size());
        assertEquals(MENTOR_USER_ID, response.getContent().getFirst().mentorUserId());
        assertEquals(new BigDecimal("88.00"), response.getContent().getFirst().matchScore());
        verify(discoveryKeywordSupport).normalizeSearchText("spring boot");
        verify(discoveryCandidateProvider).recallForSearch(eq(request), eq("spring boot"), eq("%spring boot%"), eq("%spring boot%"), eq(true), anyList(), any(), anyInt());
        verify(discoveryEnrichmentService).loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(MenteeMatchingFeatures.class), any(LocalDateTime.class));
        verify(discoveryRankingService).rankSearchCandidates(eq(List.of(row)), eq(studentProfile), any(MenteeMatchingFeatures.class), eq("spring boot"), eq(enriched));
    }

    @Test
    void searchMentors_zeroResultShouldUseCorrectedKeywordAndRecordTelemetry() {
        stubSearchContext();

        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("springbot");
        request.setSortBy("relevance");

        when(discoveryKeywordSupport.normalizeSearchText("springbot")).thenReturn("springbot");
        when(discoveryKeywordSupport.toLikePattern("springbot")).thenReturn("%springbot%");
        when(discoveryKeywordSupport.correctSpelling("springbot")).thenReturn("spring boot");
        when(discoveryKeywordSupport.toLikePattern("spring boot")).thenReturn("%spring boot%");
        when(discoveryCandidateProvider.recallForSearch(eq(request), eq("springbot"), eq("%springbot%"), eq("%springbot%"), eq(true), anyList(), any(), anyInt()))
                .thenReturn(new CandidateWindow(List.of(), 0));
        when(discoveryCandidateProvider.recallForSearch(eq(request), eq("spring boot"), eq("%spring boot%"), eq("%spring boot%"), eq(true), anyList(), any(), anyInt()))
                .thenReturn(new CandidateWindow(List.of(), 0));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(USER_ID, request);

        assertTrue(response.getContent().isEmpty());
        verify(discoveryKeywordSupport).correctSpelling("springbot");
        verify(internalTelemetryService).record(eq("MENTOR_SEARCH_ZERO_RESULT"), eq(USER_ID), eq("MENTOR_SEARCH"), isNull(), any());
    }

    @Test
    void searchMentors_nonRelevanceSort_shouldEnrichOnlyPageRows() {
        stubSearchContext();

        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setSortBy("ratingAverage");
        request.setDirection(Sort.Direction.DESC);
        request.setPage(0);
        request.setSize(1);

        MentorDiscoveryQueryRow first = discoveryRow(MENTOR_USER_ID, "First mentor");
        MentorDiscoveryQueryRow second = discoveryRow(SECOND_MENTOR_USER_ID, "Second mentor");
        when(discoveryCandidateProvider.recallForSearch(eq(request), eq(""), isNull(), isNull(), eq(false), anyList(), any(), anyInt()))
                .thenReturn(new CandidateWindow(List.of(MENTOR_USER_ID, SECOND_MENTOR_USER_ID), 2));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(MENTOR_USER_ID, SECOND_MENTOR_USER_ID))).thenReturn(List.of(first, second));
        when(discoveryRankingService.sortRowsForRequestedSort(eq(List.of(first, second)), eq("ratingAverage"), eq(Sort.Direction.DESC)))
                .thenReturn(List.of(second, first));
        Map<UUID, MentorEnrichedData> enriched = Map.of(SECOND_MENTOR_USER_ID, MentorEnrichedData.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(SECOND_MENTOR_USER_ID)), any(MenteeMatchingFeatures.class), any(LocalDateTime.class)))
                .thenReturn(enriched);
        when(discoveryRankingService.rankSearchCandidates(eq(List.of(second)), eq(studentProfile), any(MenteeMatchingFeatures.class), eq(""), eq(enriched)))
                .thenReturn(List.of(new DiscoveryRankingService.RankedSearchCandidate(second, MentorEnrichedData.empty(), new BigDecimal("50.00"), new BigDecimal("61.00"))));
        when(discoveryMapper.toCardResponseFromEnriched(any(), any(), any())).thenReturn(MentorDiscoveryCardResponse.builder()
                .mentorUserId(SECOND_MENTOR_USER_ID)
                .matchScore(new BigDecimal("61.00"))
                .build());

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(USER_ID, request);

        assertEquals(1, response.getContent().size());
        assertEquals(SECOND_MENTOR_USER_ID, response.getContent().getFirst().mentorUserId());
        verify(discoveryEnrichmentService).loadMentorEnrichedData(eq(List.of(SECOND_MENTOR_USER_ID)), any(MenteeMatchingFeatures.class), any(LocalDateTime.class));
        verify(discoveryRankingService).sortRowsForRequestedSort(eq(List.of(first, second)), eq("ratingAverage"), eq(Sort.Direction.DESC));
    }

    @Test
    void getRecommendations_shouldUseProviderEnrichmentAndRanking() {
        stubSearchContext();

        MentorDiscoveryQueryRow row = discoveryRow(MENTOR_USER_ID, "Recommendation mentor");
        when(discoveryCandidateProvider.recallForRecommendation(eq(USER_ID), eq(true), eq(3), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(row));
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), any(MenteeMatchingFeatures.class), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, MentorEnrichedData.empty()));
        DiscoveryRankingService.RecommendationScore recScore = new DiscoveryRankingService.RecommendationScore(new BigDecimal("77.00"), List.of("Khớp nhu cầu mentoring"));
        when(discoveryRankingService.scoreRecommendation(eq(row), eq(MentorEnrichedData.empty()), eq(studentProfile), any(MenteeMatchingFeatures.class)))
                .thenReturn(recScore);
        when(discoveryMapper.toRecommendation(any(), any(), any())).thenReturn(MentorRecommendationResponse.builder()
                .matchScore(new BigDecimal("77.00"))
                .mentor(MentorDiscoveryCardResponse.builder().completedSessions(5).build())
                .build());

        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(USER_ID, 3);

        assertEquals(1, recommendations.size());
        assertEquals(new BigDecimal("77.00"), recommendations.getFirst().matchScore());
        verify(discoveryCandidateProvider).recallForRecommendation(eq(USER_ID), eq(true), eq(3), any(LocalDateTime.class), anyInt());
        verify(discoveryRankingService).scoreRecommendation(eq(row), eq(MentorEnrichedData.empty()), eq(studentProfile), any(MenteeMatchingFeatures.class));
    }

    @Test
    void getMentorDetail_shouldMapServicesAndDisplayRating() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(studentProfileRepository.findWithDetailsByUserId(MENTOR_USER_ID)).thenReturn(Optional.empty());
        when(discoveryEnrichmentService.loadMentorEnrichedData(eq(List.of(MENTOR_USER_ID)), isNull(), any(LocalDateTime.class)))
                .thenReturn(Map.of(MENTOR_USER_ID, new MentorEnrichedData(
                        List.of(MentorTagResponse.builder().id(UUID.fromString("018f3abf-0a22-7fb2-9748-6cf000c47b6e")).code("HELP_QA").nameVi("Q&A").build()),
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
        when(discoveryMapper.filterTagsByType(anyList(), any())).thenReturn(List.of());

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID);

        assertEquals("Mentor Full Name", response.displayName());
        assertEquals(1, response.services().size());
        assertEquals(0, response.ratingAverage().compareTo(new BigDecimal("4.50")));
    }

    @Test
    void getMentorAvailability_availableMentor_shouldDelegateService() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilityService.getAvailableSlots(eq(mentorProfile), any(), any()))
                .thenReturn(List.of(MentorAvailabilitySlotResponse.builder()
                        .slotId(UUID.fromString("018f3abf-0a22-7ff2-9748-6cf000c47b6e"))
                        .durationMinutes(60)
                        .build()));

        List<MentorAvailabilitySlotResponse> response =
                mentorDiscoveryService.getMentorAvailability(MENTOR_USER_ID, new AvailabilityQueryRequest());

        assertEquals(1, response.size());
        verify(mentorAvailabilityService).getAvailableSlots(eq(mentorProfile), any(), any());
    }

    @Test
    void getMentorReviews_shouldReturnPagedPublicReviews() {
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));
        when(sessionFeedbackRepository.findPublicMentorReviews(eq(MENTOR_USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new MentorReviewQueryRow(
                                UUID.fromString("018f3abf-0a22-7012-9748-6cf000c47b6e"),
                                UUID.fromString("018f3abf-0a22-7032-9748-6cf000c47b6e"),
                                "Reviewer",
                                "avatar",
                                5,
                                "Great",
                                LocalDateTime.now()
                        )
                )));
        when(discoveryMapper.toMentorReviewResponse(any())).thenReturn(MentorReviewResponse.builder()
                .reviewerDisplayName("Reviewer")
                .build());

        PageResponse<MentorReviewResponse> response =
                mentorDiscoveryService.getMentorReviews(MENTOR_USER_ID, new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("Reviewer", response.getContent().getFirst().reviewerDisplayName());
    }

    @Test
    void getMentorDetail_nonDiscoverable_shouldThrowNotFound() {
        mentorProfile.setHeadline(null);
        when(mentorProfileRepository.findWithUserByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorDiscoveryService.getMentorDetail(MENTOR_USER_ID));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
        verify(discoveryEnrichmentService, never()).loadMentorEnrichedData(anyList(), any(), any(LocalDateTime.class));
    }

    private void stubSearchContext() {
        lenient().when(paymentProperties.getMenteeSurchargeBps()).thenReturn(1000);
        lenient().when(studentProfileRepository.findWithDetailsByUserId(USER_ID)).thenReturn(Optional.of(studentProfile));
        lenient().when(menteeMatchingFeatureProvider.getLatestFeatures(USER_ID))
                .thenReturn(new MenteeMatchingFeatures(3, 2, 2, "MENTOR_FIT_SUBJECT_MATCH", "DURATION_30", LocalDateTime.now()));
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
                BigDecimal.valueOf(4.6),
                8,
                12,
                LocalDateTime.now().minusDays(5),
                campus.getId(),
                campus.getName(),
                program.getId(),
                program.getNameVi(),
                specialization.getId(),
                specialization.getNameVi(),
                8,
                false,
                12,
                2,
                0,
                LocalDateTime.now().minusDays(3),
                null
        );
    }
}
