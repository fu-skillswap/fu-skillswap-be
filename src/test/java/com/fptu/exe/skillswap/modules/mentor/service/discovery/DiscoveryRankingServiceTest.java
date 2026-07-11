package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSubjectResultResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryRankingServiceTest {

    private static final UUID PROGRAM_ID = UUID.fromString("018f3abf-0a22-7292-9748-6cf000c47b6e");
    private static final UUID SPECIALIZATION_ID = UUID.fromString("018f3abf-0a22-72b2-9748-6cf000c47b6e");
    private static final UUID MENTOR_A = UUID.fromString("018f3abf-0a22-72d2-9748-6cf000c47b6e");
    private static final UUID MENTOR_B = UUID.fromString("018f3abf-0a22-72f2-9748-6cf000c47b6e");

    @Mock
    private DiscoveryKeywordSupport keywordSupport;

    private DiscoveryRankingService rankingService;
    private StudentProfile studentProfile;

    @BeforeEach
    void setUp() {
        rankingService = new DiscoveryRankingService(keywordSupport);
        AcademicProgram program = new AcademicProgram();
        program.setId(PROGRAM_ID);
        Specialization specialization = new Specialization();
        specialization.setId(SPECIALIZATION_ID);
        studentProfile = new StudentProfile();
        studentProfile.setProgram(program);
        studentProfile.setSpecialization(specialization);
        studentProfile.setSemester(5);

        lenient().when(keywordSupport.normalizeSearchText(nullable(String.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            return value == null ? "" : value.trim().toLowerCase();
        });
        lenient().when(keywordSupport.tokenizeSearchText(nullable(String.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(0, String.class);
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return Arrays.stream(value.trim().toLowerCase().split("\\s+"))
                    .distinct()
                    .toList();
        });
    }

    @Test
    void rankSearchCandidates_shouldPreferKeywordAndAvailabilitySignal() {
        when(keywordSupport.tokenizeSearchText("spring boot")).thenReturn(List.of("spring", "boot"));

        MentorDiscoveryQueryRow strong = row(MENTOR_A, "Spring Boot mentor", true, false, 4.8, 12);
        MentorDiscoveryQueryRow weak = row(MENTOR_B, "General mentor", true, false, 4.2, 4);
        MentorEnrichedData strongData = new MentorEnrichedData(
                List.of(),
                List.of(MentorSubjectResultResponse.builder().subjectCode("PRJ301").subjectName("Spring").build()),
                List.of(),
                List.of(),
                List.of(),
                true,
                true
        );
        MentorEnrichedData weakData = MentorEnrichedData.empty();

        List<DiscoveryRankingService.RankedSearchCandidate> ranked = rankingService.rankSearchCandidates(
                List.of(weak, strong),
                studentProfile,
                new MenteeMatchingFeatures(4, 2, 2, "MENTOR_FIT_SUBJECT_MATCH", "DURATION_30", LocalDateTime.now()),
                "spring boot",
                Map.of(MENTOR_A, strongData, MENTOR_B, weakData)
        );

        assertEquals(MENTOR_A, ranked.getFirst().row().mentorUserId());
        assertTrue(ranked.getFirst().score().compareTo(ranked.get(1).score()) > 0);
    }

    @Test
    void scoreRecommendation_recentAlumniIntent_shouldBoostAlumni() {
        MentorDiscoveryQueryRow alumni = row(MENTOR_A, "Alumni mentor", true, true, 4.7, 8);
        MentorDiscoveryQueryRow nonAlumni = row(MENTOR_B, "Non alumni mentor", true, false, 4.7, 8);

        DiscoveryRankingService.RecommendationScore alumniScore = rankingService.scoreRecommendation(
                alumni,
                MentorEnrichedData.empty(),
                studentProfile,
                new MenteeMatchingFeatures(2, 2, 4, "MENTOR_FIT_RECENT_ALUMNI", "DURATION_30", LocalDateTime.now())
        );
        DiscoveryRankingService.RecommendationScore nonAlumniScore = rankingService.scoreRecommendation(
                nonAlumni,
                MentorEnrichedData.empty(),
                studentProfile,
                new MenteeMatchingFeatures(2, 2, 4, "MENTOR_FIT_RECENT_ALUMNI", "DURATION_30", LocalDateTime.now())
        );

        assertTrue(alumniScore.matchScore().compareTo(nonAlumniScore.matchScore()) > 0);
    }

    @Test
    void scoreRecommendation_staleMatching_shouldReduceScore() {
        MentorDiscoveryQueryRow candidate = row(MENTOR_A, "Stable mentor", true, false, 4.7, 8);

        DiscoveryRankingService.RecommendationScore recent = rankingService.scoreRecommendation(
                candidate,
                MentorEnrichedData.empty(),
                studentProfile,
                new MenteeMatchingFeatures(3, 3, 3, "MENTOR_FIT_SUBJECT_MATCH", "DURATION_30", LocalDateTime.now().minusDays(5))
        );
        DiscoveryRankingService.RecommendationScore stale = rankingService.scoreRecommendation(
                candidate,
                MentorEnrichedData.empty(),
                studentProfile,
                new MenteeMatchingFeatures(3, 3, 3, "MENTOR_FIT_SUBJECT_MATCH", "DURATION_30", LocalDateTime.now().minusDays(120))
        );

        assertTrue(recent.matchScore().compareTo(stale.matchScore()) > 0);
    }

    @Test
    void sortRowsForRequestedSort_ratingAverage_shouldSortDescending() {
        MentorDiscoveryQueryRow low = row(MENTOR_A, "Low", true, false, 4.1, 3);
        MentorDiscoveryQueryRow high = row(MENTOR_B, "High", true, false, 4.9, 9);

        List<MentorDiscoveryQueryRow> sorted = rankingService.sortRowsForRequestedSort(
                List.of(low, high),
                "ratingAverage",
                Sort.Direction.DESC
        );

        assertEquals(MENTOR_B, sorted.getFirst().mentorUserId());
    }

    private MentorDiscoveryQueryRow row(UUID mentorId, String headline, boolean available, boolean alumni, double rating, int completedSessions) {
        return new MentorDiscoveryQueryRow(
                mentorId,
                "Mentor " + mentorId,
                "avatar.png",
                headline,
                "Backend mentoring",
                "Bio",
                4,
                3,
                3,
                available,
                BigDecimal.valueOf(rating),
                8,
                completedSessions,
                LocalDateTime.now().minusDays(2),
                null,
                null,
                PROGRAM_ID,
                "SE",
                SPECIALIZATION_ID,
                "Backend",
                8,
                alumni,
                15,
                1,
                0,
                LocalDateTime.now().minusDays(1),
                null
        );
    }
}
