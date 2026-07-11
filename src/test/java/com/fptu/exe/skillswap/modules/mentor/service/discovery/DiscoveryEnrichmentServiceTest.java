package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorTagResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import com.fptu.exe.skillswap.modules.matching.service.MenteeMatchingFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryEnrichmentServiceTest {

    private static final UUID MENTOR_ID = UUID.fromString("018f3abf-0a22-71b2-9748-6cf000c47b6e");

    @Mock
    private MentorTagRepository mentorTagRepository;
    @Mock
    private MentorSubjectResultRepository mentorSubjectResultRepository;
    @Mock
    private MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    @Mock
    private MentorAchievementRepository mentorAchievementRepository;
    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Mock
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    private DiscoveryEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new DiscoveryEnrichmentService(
                mentorTagRepository,
                mentorSubjectResultRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository,
                mentorServiceRepository,
                mentorAvailabilitySlotRepository,
                availabilitySlotServiceRepository
        );
    }

    @Test
    void loadMentorEnrichedData_emptyInput_shouldReturnEmptyMap() {
        assertTrue(enrichmentService.loadMentorEnrichedData(List.of(), null, LocalDateTime.now()).isEmpty());
    }

    @Test
    void loadMentorEnrichedData_shouldGroupBatchDataAndAvailabilityFlags() {
        MentorProfile profile = MentorProfile.builder().userId(MENTOR_ID).build();
        Tag helpTopic = Tag.builder()
                .id(UUID.fromString("018f3abf-0a22-71d2-9748-6cf000c47b6e"))
                .code("HELP_QA")
                .nameVi("Q&A")
                .type(TagType.HELP_TOPIC)
                .build();
        MentorTag mentorTag = MentorTag.builder()
                .id(new MentorTagId(MENTOR_ID, helpTopic.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(profile)
                .tag(helpTopic)
                .isPrimary(true)
                .build();
        MentorSubjectResult subjectResult = MentorSubjectResult.builder()
                .id(UUID.fromString("018f3abf-0a22-71f2-9748-6cf000c47b6e"))
                .mentorProfile(profile)
                .subjectCode("PRJ301")
                .subjectName("Java Web")
                .scoreValue(new BigDecimal("8.50"))
                .displayOrder(1)
                .build();
        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(UUID.fromString("018f3abf-0a22-7212-9748-6cf000c47b6e"))
                .mentorProfile(profile)
                .title("Booking App")
                .content("Backend")
                .projectDescription("Spring Boot")
                .displayOrder(1)
                .build();
        MentorAchievement achievement = MentorAchievement.builder()
                .id(UUID.fromString("018f3abf-0a22-7232-9748-6cf000c47b6e"))
                .mentorProfile(profile)
                .title("SE Award")
                .awardDescription("Top student")
                .achievedAt(LocalDate.of(2026, 1, 1))
                .displayOrder(1)
                .build();
        MentorService service = MentorService.builder()
                .id(UUID.fromString("018f3abf-0a22-7252-9748-6cf000c47b6e"))
                .mentorProfile(profile)
                .title("Review CV")
                .isActive(true)
                .build();

        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(List.of(MENTOR_ID), Set.of(MentorTagType.HELP_TOPIC)))
                .thenReturn(List.of(mentorTag));
        when(mentorSubjectResultRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(List.of(MENTOR_ID)))
                .thenReturn(List.of(subjectResult));
        when(mentorFeaturedProjectRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(List.of(MENTOR_ID)))
                .thenReturn(List.of(project));
        when(mentorAchievementRepository.findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(List.of(MENTOR_ID)))
                .thenReturn(List.of(achievement));
        when(mentorServiceRepository.findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(List.of(MENTOR_ID)))
                .thenReturn(List.of(service));
        when(mentorAvailabilitySlotRepository.findMentorUserIdsWithActiveSlotsInFuture(List.of(MENTOR_ID), LocalDateTime.of(2026, 7, 9, 10, 0)))
                .thenReturn(List.of(MENTOR_ID));
        when(availabilitySlotServiceRepository.findMentorUserIdsWithFutureActiveSlotServiceDuration(List.of(MENTOR_ID), 30, LocalDateTime.of(2026, 7, 9, 10, 0)))
                .thenReturn(List.of(MENTOR_ID));

        Map<UUID, MentorEnrichedData> result = enrichmentService.loadMentorEnrichedData(
                List.of(MENTOR_ID),
                new MenteeMatchingFeatures(3, 2, 2, "MENTOR_FIT_SUBJECT_MATCH", "DURATION_30", LocalDateTime.now()),
                LocalDateTime.of(2026, 7, 9, 10, 0)
        );

        MentorEnrichedData enrichedData = result.get(MENTOR_ID);
        assertEquals(1, enrichedData.helpTopics().size());
        assertEquals(1, enrichedData.subjectResults().size());
        assertEquals(1, enrichedData.featuredProjects().size());
        assertEquals(1, enrichedData.achievements().size());
        assertEquals(1, enrichedData.services().size());
        assertTrue(enrichedData.hasAvailability());
        assertTrue(enrichedData.hasPreferredDurationAvailability());
    }

    @Test
    void loadHelpTopicTags_shouldReturnOnlyMappedHelpTopics() {
        MentorProfile profile = MentorProfile.builder().userId(MENTOR_ID).build();
        Tag helpTopic = Tag.builder()
                .id(UUID.fromString("018f3abf-0a22-7272-9748-6cf000c47b6e"))
                .code("HELP_OJT")
                .nameVi("OJT")
                .type(TagType.HELP_TOPIC)
                .build();
        MentorTag mentorTag = MentorTag.builder()
                .id(new MentorTagId(MENTOR_ID, helpTopic.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(profile)
                .tag(helpTopic)
                .build();
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(List.of(MENTOR_ID), Set.of(MentorTagType.HELP_TOPIC)))
                .thenReturn(List.of(mentorTag));

        List<MentorTagResponse> result = enrichmentService.loadHelpTopicTags(MENTOR_ID);

        assertEquals(1, result.size());
        assertEquals("HELP_OJT", result.getFirst().code());
        assertFalse(result.getFirst().primary());
    }
}
