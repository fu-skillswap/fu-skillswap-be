package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryKeywordSupportTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorSubjectResultRepository mentorSubjectResultRepository;
    @Mock
    private MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    @Mock
    private MentorAchievementRepository mentorAchievementRepository;

    private DiscoveryKeywordSupport keywordSupport;

    @BeforeEach
    void setUp() {
        keywordSupport = new DiscoveryKeywordSupport(
                tagRepository,
                mentorServiceRepository,
                mentorSubjectResultRepository,
                mentorFeaturedProjectRepository,
                mentorAchievementRepository
        );
        lenient().when(tagRepository.findAll()).thenReturn(List.of());
        lenient().when(mentorServiceRepository.findAllActiveServiceTitles()).thenReturn(List.of());
        lenient().when(mentorSubjectResultRepository.findAll()).thenReturn(List.of());
        lenient().when(mentorFeaturedProjectRepository.findAll()).thenReturn(List.of());
        lenient().when(mentorAchievementRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void normalizeSearchText_shouldStripAccentsAndLowercase() {
        assertEquals("lap trinh java", keywordSupport.normalizeSearchText(" Lập trình Java "));
    }

    @Test
    void tokenizeSearchText_shouldReturnDistinctTokens() {
        assertEquals(List.of("java", "backend"), keywordSupport.tokenizeSearchText("Java backend java"));
    }

    @Test
    void toLikePattern_blank_shouldReturnNull() {
        assertNull(keywordSupport.toLikePattern("   "));
    }

    @Test
    void refreshKeywordsCache_thenCorrectSpelling_shouldUseSnapshotCache() {
        Tag tag = Tag.builder()
                .id(UUID.fromString("018f3abf-0a22-7112-9748-6cf000c47b6e"))
                .code("JAVA")
                .nameVi("Java")
                .type(TagType.HELP_TOPIC)
                .build();
        MentorSubjectResult subject = MentorSubjectResult.builder()
                .id(UUID.fromString("018f3abf-0a22-7132-9748-6cf000c47b6e"))
                .subjectCode("PRJ301")
                .subjectName("Java Web")
                .build();
        MentorFeaturedProject project = MentorFeaturedProject.builder()
                .id(UUID.fromString("018f3abf-0a22-7152-9748-6cf000c47b6e"))
                .title("Backend API")
                .content("RESTful Java")
                .projectDescription("Spring Boot service")
                .build();
        MentorAchievement achievement = MentorAchievement.builder()
                .id(UUID.fromString("018f3abf-0a22-7172-9748-6cf000c47b6e"))
                .title("Java Award")
                .awardDescription("Excellent Java")
                .productHeader("Java Product")
                .productDescription("Java backend")
                .build();

        when(tagRepository.findAll()).thenReturn(List.of(tag));
        when(mentorServiceRepository.findAllActiveServiceTitles()).thenReturn(List.of("Java mentoring"));
        when(mentorSubjectResultRepository.findAll()).thenReturn(List.of(subject));
        when(mentorFeaturedProjectRepository.findAll()).thenReturn(List.of(project));
        when(mentorAchievementRepository.findAll()).thenReturn(List.of(achievement));

        keywordSupport.refreshKeywordsCache();

        assertEquals("java", keywordSupport.correctSpelling("jvaa"));
        assertTrue(keywordSupport.correctSpelling("spring").startsWith("spring"));
    }
}
