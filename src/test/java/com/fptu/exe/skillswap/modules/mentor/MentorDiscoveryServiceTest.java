package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.dto.request.AvailabilityQueryRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorDiscoveryServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private MentorTagRepository mentorTagRepository;

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private MentorAvailabilityService mentorAvailabilityService;

    @Mock
    private SessionFeedbackRepository sessionFeedbackRepository;

    @InjectMocks
    private MentorDiscoveryService mentorDiscoveryService;

    private UUID userId;
    private UUID mentorUserId;
    private UUID fallbackMentorUserId;
    private StudentProfile studentProfile;
    private MentorProfile mentorProfile;
    private User mentorUser;
    private Campus campus;
    private AcademicProgram academicProgram;
    private Specialization specialization;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mentorUserId = UUID.randomUUID();
        fallbackMentorUserId = UUID.randomUUID();
        campus = new Campus();
        campus.setId(UUID.randomUUID());
        campus.setName("Hanoi");
        academicProgram = new AcademicProgram();
        academicProgram.setId(UUID.randomUUID());
        academicProgram.setNameVi("CNTT");
        specialization = new Specialization();
        specialization.setId(UUID.randomUUID());
        specialization.setNameVi("Backend");
        studentProfile = new StudentProfile();
        studentProfile.setUserId(userId);
        studentProfile.setCampus(campus);
        studentProfile.setProgram(academicProgram);
        studentProfile.setSpecialization(specialization);
        studentProfile.setSemester(8);

        mentorUser = new User();
        mentorUser.setId(mentorUserId);
        mentorUser.setFullName("Mentor Full Name");
        mentorUser.setAvatarUrl("avatar.png");
        mentorUser.setStatus(UserStatus.ACTIVE);
        mentorUser.setRoles(Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));

        mentorProfile = MentorProfile.builder()
                .userId(mentorUserId)
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .headline("headline")
                .expertiseDescription("expertise")
                .supportingSubjects("subjects")
                .isAvailable(true)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .verifiedAt(LocalDateTime.now().minusDays(4))
                .averageRating(BigDecimal.valueOf(4.5))
                .totalReviews(4)
                .totalCompletedSessions(5)
                .build();
    }

    @Test
    void searchMentors_unauthenticated_shouldThrow() {
        BaseException exception = assertThrows(BaseException.class, () ->
                mentorDiscoveryService.searchMentors(null, new MentorDiscoverySearchRequest())
        );

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    void searchMentors_relevanceSort_shouldUseSortedQuery() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("Java");
        request.setSortBy("relevance");

        when(mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                any(), any(), any(), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.singletonList(mentorUserId)));

        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(mentorUserId)))
                .thenReturn(List.of(discoveryRow(mentorUserId, BigDecimal.ZERO, 0, 3.0)));
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(), any()))
                .thenReturn(Collections.emptyList());
        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(userId, request);

        assertEquals(1, response.getContent().size());
        assertEquals("Mentor Full Name", response.getContent().getFirst().displayName());
        assertEquals(new BigDecimal("5.00"), response.getContent().getFirst().ratingAverage());
    }

    @Test
    void searchMentors_keywordShouldNotHardFilterByMenteeAcademicProfile() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("spring boot");

        UUID otherProgramId = UUID.randomUUID();
        UUID otherSpecializationId = UUID.randomUUID();
        MentorDiscoveryQueryRow otherMajorMentor = discoveryRow(
                mentorUserId, "Spring Boot mentor", "REST API Spring Boot", "PRJ301", "Backend",
                campus.getId(), otherProgramId, otherSpecializationId, 6, false
        );

        when(mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC),
                eq(null), eq(null), eq(null), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(mentorUserId)));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(mentorUserId)))
                .thenReturn(List.of(otherMajorMentor));
        stubEmptyCandidateRelations();

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(userId, request);

        assertEquals(1, response.getContent().size());
        verify(mentorProfileRepository).findDiscoverableCandidateIdsWithKeyword(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC),
                eq(null), eq(null), eq(null), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class)
        );
    }

    @Test
    void searchMentors_unsignedKeywordShouldMatchVietnameseText() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("huong dan mon hoc");

        MentorDiscoveryQueryRow accentedMentor = discoveryRow(
                mentorUserId, "Hướng dẫn môn học", "Hỗ trợ sinh viên", "EXE101", "Định hướng học tập",
                campus.getId(), academicProgram.getId(), specialization.getId(), 8, false
        );
        stubSearchCandidates(request, List.of(accentedMentor));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(userId, request);

        assertEquals(1, response.getContent().size());
        assertEquals(mentorUserId, response.getContent().getFirst().mentorUserId());
    }

    @Test
    void searchMentors_relevanceSort_shouldRankKeywordAndPersonalizationHigher() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("spring boot");
        request.setSortBy("relevance");

        StudentProfile menteeProfile = new StudentProfile();
        menteeProfile.setUserId(userId);
        menteeProfile.setCampus(campus);
        menteeProfile.setProgram(academicProgram);
        menteeProfile.setSpecialization(specialization);
        menteeProfile.setSemester(5);
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.of(menteeProfile));

        User fallbackUser = new User();
        fallbackUser.setId(fallbackMentorUserId);
        fallbackUser.setFullName("Fallback Mentor");
        fallbackUser.setAvatarUrl("fallback.png");
        fallbackUser.setStatus(UserStatus.ACTIVE);
        fallbackUser.setRoles(Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));

        MentorProfile fallbackProfile = MentorProfile.builder()
                .userId(fallbackMentorUserId)
                .user(fallbackUser)
                .status(MentorStatus.ACTIVE)
                .headline("Java mentor")
                .expertiseDescription("General Java support")
                .supportingSubjects("Java basics")
                .isAvailable(true)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .verifiedAt(LocalDateTime.now().minusDays(2))
                .averageRating(BigDecimal.valueOf(5.0))
                .totalReviews(9)
                .totalCompletedSessions(40)
                .build();

        MentorDiscoveryQueryRow keywordStrongMentor = discoveryRow(
                mentorUserId,
                "Spring Boot mentor",
                "Spring Boot backend coaching",
                "SWP391, Spring Boot",
                "Mentor hỗ trợ Spring Boot",
                campus.getId(),
                academicProgram.getId(),
                specialization.getId(),
                8,
                false
        );
        MentorDiscoveryQueryRow weakerMentor = discoveryRow(
                fallbackMentorUserId,
                "Java mentor",
                "General Java backend coaching",
                "Java basics",
                "Support for backend topics",
                campus.getId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                false
        );

        MentorService strongService = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Spring Boot deep dive")
                .description("Làm dự án Spring Boot thực tế")
                .expectedOutcome("Spring Boot")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .build();
        MentorService weakService = MentorService.builder()
                .mentorProfile(fallbackProfile)
                .title("Java fundamentals")
                .description("Cơ bản Java")
                .expectedOutcome("Spring Boot")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .build();

        when(mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                any(), any(), any(), anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(fallbackMentorUserId, mentorUserId)));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(fallbackMentorUserId, mentorUserId)))
                .thenReturn(List.of(weakerMentor, keywordStrongMentor));
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(), any()))
                .thenReturn(Collections.emptyList());
        when(mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(weakService, strongService));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(userId, request);

        assertEquals(2, response.getContent().size());
        assertEquals(mentorUserId, response.getContent().getFirst().mentorUserId());
    }

    @Test
    void searchMentors_selectedCampusShouldRemainHardFilter() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setCampusId(campus.getId());

        when(mentorProfileRepository.findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC), eq(campus.getId()),
                eq(null), eq(null), anyBoolean(), anyList(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        mentorDiscoveryService.searchMentors(userId, request);

        verify(mentorProfileRepository).findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC), eq(campus.getId()),
                eq(null), eq(null), anyBoolean(), anyList(), any(), any(Pageable.class)
        );
    }

    @Test
    void searchMentors_withoutKeywordShouldUseNonKeywordQuery() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setSortBy("relevance");
        request.setSize(24);

        when(mentorProfileRepository.findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC),
                eq(null), eq(null), eq(null), anyBoolean(), anyList(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(mentorUserId)));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(List.of(mentorUserId)))
                .thenReturn(List.of(discoveryRow(mentorUserId, BigDecimal.ZERO, 0, 3.0)));
        stubEmptyCandidateRelations();

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(userId, request);

        assertEquals(1, response.getContent().size());
        verify(mentorProfileRepository).findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC),
                eq(null), eq(null), eq(null), anyBoolean(), anyList(), any(), any(Pageable.class)
        );
    }

    @Test
    void getRecommendations_emptyCandidates_shouldReturnEmptyList() {
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.of(studentProfile));
        when(mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                eq(userId),
                any(), any(Pageable.class)
        )).thenReturn(List.of());

        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(userId, 5);

        assertTrue(recommendations.isEmpty());
    }

    @Test
    void getRecommendations_shouldMapScoreAndReasons() {
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.of(studentProfile));
        when(mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                eq(userId),
                any(), any(Pageable.class)
        )).thenReturn(List.of(discoveryRow(mentorUserId, BigDecimal.valueOf(4.8), 5, 0.0)));
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(), any()))
                .thenReturn(Collections.emptyList());

        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(userId, 5);

        assertEquals(1, recommendations.size());
        // Phase S3: matchScore now converted to percentage of max theoretical score (118.00).
        // Raw breakdown: campus(+10) + program(+40) + spec(+30) + equalSemester(+10)
        //          + highRating≥4.5(+8) + reviews≥5(+5) = 103
        // Percentage: 103 * 100 / 118 = 87.29%
        assertEquals(new BigDecimal("87.29"), recommendations.getFirst().matchScore());
        assertFalse(recommendations.getFirst().matchReasons().isEmpty());
    }

    @Test
    void getMentorDetail_nonDiscoverable_shouldThrowNotFound() {
        mentorProfile.setHeadline(null);
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorDiscoveryService.getMentorDetail(mentorUserId));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getMentorDetail_shouldMapServicesAndDisplayRating() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        when(studentProfileRepository.findWithDetailsByUserId(mentorUserId)).thenReturn(Optional.empty());
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(), any())).thenReturn(Collections.emptyList());
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId))
                .thenReturn(List.of(MentorService.builder()
                        .id(UUID.randomUUID())
                        .mentorProfile(mentorProfile)
                        .title("CV Review")
                        .description("Review CV")
                        .durationMinutes(60)
                        .isFree(true)
                        .priceScoin(0)
                        .build()));

        MentorDiscoveryDetailResponse response = mentorDiscoveryService.getMentorDetail(mentorUserId);

        assertEquals("Mentor Full Name", response.displayName());
        assertEquals(1, response.services().size());
        assertEquals(0, response.ratingAverage().compareTo(new BigDecimal("4.50")));
    }

    @Test
    void getMentorAvailability_suspendedMentor_shouldThrowNotFound() {
        mentorProfile.setBookingSuspendedUntil(LocalDateTime.now().plusDays(1));
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorDiscoveryService.getMentorAvailability(
                mentorUserId,
                new AvailabilityQueryRequest()
        ));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getMentorDetail_bannedUser_shouldThrowNotFound() {
        mentorUser.setStatus(com.fptu.exe.skillswap.modules.identity.domain.UserStatus.BANNED);
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        BaseException exception = assertThrows(BaseException.class, () -> mentorDiscoveryService.getMentorDetail(mentorUserId));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void getMentorAvailability_availableMentor_shouldDelegateService() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilityService.getAvailableSlots(eq(mentorProfile), any(), any()))
                .thenReturn(List.of(MentorAvailabilitySlotResponse.builder().slotId(UUID.randomUUID()).durationMinutes(60).build()));

        List<MentorAvailabilitySlotResponse> response = mentorDiscoveryService.getMentorAvailability(
                mentorUserId,
                new AvailabilityQueryRequest()
        );

        assertEquals(1, response.size());
        verify(mentorAvailabilityService).getAvailableSlots(eq(mentorProfile), any(), any());
    }

    @Test
    void getMentorReviews_shouldReturnPagedPublicReviews() {
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));
        when(sessionFeedbackRepository.findPublicMentorReviews(eq(mentorUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        new MentorReviewQueryRow(UUID.randomUUID(), UUID.randomUUID(), "Reviewer", "avatar", 5, "Great", LocalDateTime.now())
                )));

        PageResponse<MentorReviewResponse> response = mentorDiscoveryService.getMentorReviews(mentorUserId, new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("Reviewer", response.getContent().getFirst().reviewerDisplayName());
    }

    private MentorDiscoveryQueryRow discoveryRow(UUID id, BigDecimal rating, Integer reviewCount, Double matchScore) {
        return new MentorDiscoveryQueryRow(
                id,
                "Mentor Full Name",
                "avatar.png",
                "Java headline",
                "Java expertise",
                "Java subjects",
                "Java mentor bio",
                true,
                rating,
                reviewCount,
                5,
                TeachingMode.ONLINE,
                LocalDateTime.now().minusDays(10),
                campus.getId(),
                campus.getName(),
                academicProgram.getId(),
                academicProgram.getNameVi(),
                specialization.getId(),
                specialization.getNameVi(),
                8,
                false,
                matchScore
        );
    }

    private MentorDiscoveryQueryRow discoveryRow(
            UUID id,
            String headline,
            String expertise,
            String subjects,
            String bio,
            UUID campusId,
            UUID programId,
            UUID specializationId,
            Integer semester,
            boolean alumni
    ) {
        return new MentorDiscoveryQueryRow(
                id, "Mentor " + id, "avatar.png", headline, expertise, subjects, bio, true,
                BigDecimal.valueOf(4.5), 3, 5, TeachingMode.ONLINE, LocalDateTime.now().minusDays(10),
                campusId, "Campus", programId, "Program", specializationId, "Specialization",
                semester, alumni, null
        );
    }

    private void stubSearchCandidates(
            MentorDiscoverySearchRequest request,
            List<MentorDiscoveryQueryRow> rows
    ) {
        List<UUID> ids = rows.stream().map(MentorDiscoveryQueryRow::mentorUserId).toList();
        when(mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC),
                eq(request.getCampusId()), eq(request.getSpecializationId()), eq(request.getTeachingMode()),
                anyBoolean(), anyList(), anyString(), anyString(), anyString(), anyString(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(ids));
        when(mentorProfileRepository.findDiscoveryRowsByMentorUserIds(ids)).thenReturn(rows);
        stubEmptyCandidateRelations();
    }

    private void stubEmptyCandidateRelations() {
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(), any()))
                .thenReturn(Collections.emptyList());
    }
}

