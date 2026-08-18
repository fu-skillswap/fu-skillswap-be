package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DiscoveryEnrichmentServiceTest {

    @Mock private MentorSubjectResultRepository mentorSubjectResultRepository;
    @Mock private MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    @Mock private MentorAchievementRepository mentorAchievementRepository;
    @Mock private MentorServiceRepository mentorServiceRepository;
    @Mock private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Mock private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @Test
    void loadMentorEnrichedData_emptyInput_doesNotQueryRepositories() {
        DiscoveryEnrichmentService service = new DiscoveryEnrichmentService(
                mentorSubjectResultRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository,
                mentorServiceRepository,
                mentorAvailabilitySlotRepository,
                availabilitySlotServiceRepository);

        assertTrue(service.loadMentorEnrichedData(List.of(), LocalDateTime.now()).isEmpty());
    }
}
