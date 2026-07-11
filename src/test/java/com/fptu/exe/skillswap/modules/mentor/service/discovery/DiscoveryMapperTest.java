package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.feedback.dto.response.MentorReviewResponse;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryMapperTest {

    @Mock
    private PaymentProperties paymentProperties;

    @InjectMocks
    private DiscoveryMapper discoveryMapper;

    private MentorDiscoveryQueryRow mockRow;

    @BeforeEach
    void setUp() {
        mockRow = new MentorDiscoveryQueryRow(
                UUID.randomUUID(),
                "Display Name",
                "avatar.jpg",
                "Java Expert",
                "Backend",
                "Bio",
                3,
                3,
                3,
                true,
                new BigDecimal("4.80"),
                10,
                50,
                LocalDateTime.now(),
                UUID.randomUUID(),
                "HCM",
                UUID.randomUUID(),
                "SE",
                UUID.randomUUID(),
                "Backend",
                8,
                false,
                15,
                10,
                0,
                LocalDateTime.now(),
                0.0
        );
    }

    @Test
    void toCardResponse_shouldMapAllFields() {
        MentorDiscoveryCardResponse response = discoveryMapper.toCardResponse(
                mockRow,
                List.of(),
                new BigDecimal("95.00"),
                List.of(),
                List.of(),
                List.of()
        );

        assertNotNull(response);
        assertEquals(mockRow.mentorUserId(), response.mentorUserId());
        assertEquals("Display Name", response.displayName());
        assertEquals(new BigDecimal("4.80"), response.ratingAverage());
        assertEquals(new BigDecimal("95.00"), response.matchScore());
    }

    @Test
    void toCardResponse_withZeroReviews_shouldReturnDefaultRating() {
        MentorDiscoveryQueryRow zeroReviewRow = new MentorDiscoveryQueryRow(
                mockRow.mentorUserId(),
                mockRow.displayName(),
                mockRow.avatarUrl(),
                mockRow.headline(),
                mockRow.expertiseDescription(),
                mockRow.bio(),
                3, 3, 3, true,
                new BigDecimal("0.00"),
                0,
                0,
                LocalDateTime.now(),
                mockRow.campusId(), mockRow.campusName(),
                mockRow.programId(), mockRow.programName(),
                mockRow.specializationId(), mockRow.specializationName(),
                8, false, 0, 0, 0, LocalDateTime.now(), 0.0
        );

        MentorDiscoveryCardResponse response = discoveryMapper.toCardResponse(
                zeroReviewRow,
                List.of(),
                new BigDecimal("95.00"),
                List.of(),
                List.of(),
                List.of()
        );

        assertEquals(new BigDecimal("5.00"), response.ratingAverage());
    }

    @Test
    void toRecommendation_shouldMapCardAndScore() {
        DiscoveryRankingService.RecommendationScore score = new DiscoveryRankingService.RecommendationScore(
                new BigDecimal("88.50"),
                List.of("Khớp chuyên ngành")
        );

        MentorRecommendationResponse response = discoveryMapper.toRecommendation(mockRow, MentorEnrichedData.empty(), score);

        assertNotNull(response);
        assertEquals(new BigDecimal("88.50"), response.matchScore());
        assertEquals(1, response.matchReasons().size());
        assertEquals("Khớp chuyên ngành", response.matchReasons().getFirst());
        assertNotNull(response.mentor());
    }

    @Test
    void toServiceResponse_shouldCalculatePriceWithSurcharge() {
        when(paymentProperties.getMenteeSurchargeBps()).thenReturn(1000); // 10%

        MentorService service = MentorService.builder()
                .id(UUID.randomUUID())
                .title("Code Review")
                .isFree(false)
                .priceScoin(100)
                .helpTopics(Set.of())
                .build();

        MentorServiceResponse response = discoveryMapper.toServiceResponse(service);

        assertNotNull(response);
        assertEquals("Code Review", response.title());
        assertFalse(response.free());
        assertEquals(110, response.priceScoin());
    }

    @Test
    void filterTagsByType_shouldOnlyReturnMatchingTypes() {
        MentorTagResponse tag1 = MentorTagResponse.builder()
                .type(TagType.HELP_TOPIC)
                .nameVi("Topic")
                .build();
        MentorTagResponse tag2 = MentorTagResponse.builder()
                .type(TagType.SOFT_SKILL)
                .nameVi("Skill")
                .build();

        List<MentorTagResponse> result = discoveryMapper.filterTagsByType(List.of(tag1, tag2), MentorTagType.HELP_TOPIC);

        assertEquals(1, result.size());
        assertEquals(TagType.HELP_TOPIC, result.getFirst().type());
    }
}
