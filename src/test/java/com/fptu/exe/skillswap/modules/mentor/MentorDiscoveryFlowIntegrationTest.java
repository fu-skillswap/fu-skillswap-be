package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MentorDiscoveryFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorTagRepository mentorTagRepository;

    @Autowired
    private MentorDiscoveryService mentorDiscoveryService;

    private User menteeUser;
    private User mentor1User;
    private User mentor2User;

    private Campus campus1;
    private AcademicProgram program1;
    private Specialization spec1;
    private Tag helpTopicTag;

    @BeforeEach
    void setUp() {
        campus1 = campusRepository.findAll().stream().findFirst().orElseThrow();
        program1 = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        spec1 = specializationRepository.findAll().stream().findFirst().orElseThrow();

        // Ensure tag of type HELP_TOPIC exists
        helpTopicTag = tagRepository.save(Tag.builder()
                .code("DISC_SPRING")
                .nameVi("Spring Boot")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build());

        // Setup Mentee
        menteeUser = userRepository.save(User.builder()
                .email("mentee-disc@test.com")
                .fullName("Mentee Explorer")
                .status(UserStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(menteeUser)
                .studentCode("SE2001")
                .campus(campus1)
                .program(program1)
                .specialization(spec1)
                .semester(5)
                .intakeYear(2022)
                .build());

        // Setup Mentor 1 (Matches Specialization & Campus)
        mentor1User = userRepository.save(User.builder()
                .email("mentor1-disc@test.com")
                .fullName("Expert Java Mentor")
                .status(UserStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(mentor1User)
                .studentCode("SE1001")
                .campus(campus1)
                .program(program1)
                .specialization(spec1)
                .semester(8)
                .intakeYear(2021)
                .build());
        MentorProfile profile1 = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentor1User)
                .status(MentorStatus.ACTIVE)
                .headline("Java, Spring Boot developer")
                .expertiseDescription("Teaching Spring Framework and Java programming")
                .isAvailable(true)
                .sessionDuration(60)
                .teachingMode(TeachingMode.HYBRID)
                .verifiedAt(LocalDateTime.now().minusDays(10))
                .build());
        mentorTagRepository.save(MentorTag.builder()
                .id(new MentorTagId(mentor1User.getId(), helpTopicTag.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(profile1)
                .tag(helpTopicTag)
                .build());

        // Setup Mentor 2 (Matches Program but different Specialization & Campus)
        mentor2User = userRepository.save(User.builder()
                .email("mentor2-disc@test.com")
                .fullName("General Tech Mentor")
                .status(UserStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(mentor2User)
                .studentCode("SE1002")
                .campus(campus1)
                .program(program1)
                .semester(7)
                .intakeYear(2021)
                .build());
        MentorProfile profile2 = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentor2User)
                .status(MentorStatus.ACTIVE)
                .headline("Software Engineer Generalist")
                .expertiseDescription("Algorithm design and web systems")
                .isAvailable(true)
                .sessionDuration(60)
                .teachingMode(TeachingMode.HYBRID)
                .verifiedAt(LocalDateTime.now().minusDays(5))
                .build());
        mentorTagRepository.save(MentorTag.builder()
                .id(new MentorTagId(mentor2User.getId(), helpTopicTag.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(profile2)
                .tag(helpTopicTag)
                .build());
    }

    @Test
    void testDiscoverySearchAndRecommendationFlow() {
        // 1. Search with Keyword matching Mentor 1
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("Java");
        request.setSortBy("relevance");

        PageResponse<MentorDiscoveryCardResponse> searchResults = mentorDiscoveryService.searchMentors(
                menteeUser.getId(), request
        );

        assertNotNull(searchResults);
        assertFalse(searchResults.getContent().isEmpty());
        // Mentor 1 should be first because of keyword and specialization match
        assertEquals("Expert Java Mentor", searchResults.getContent().getFirst().displayName());

        // 2. Fetch recommendations
        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(
                menteeUser.getId(), 5
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());

        // Mentor 1 has specialization match (+40) and program match (+18) and campus match (+10) -> higher score
        // Mentor 2 has only program match (+18) and campus match (+10)
        double score1 = recommendations.stream()
                .filter(r -> r.mentor().mentorUserId().equals(mentor1User.getId()))
                .map(r -> r.matchScore().doubleValue())
                .findFirst().orElse(0.0);

        double score2 = recommendations.stream()
                .filter(r -> r.mentor().mentorUserId().equals(mentor2User.getId()))
                .map(r -> r.matchScore().doubleValue())
                .findFirst().orElse(0.0);

        assertTrue(score1 > score2, "Mentor 1 match score (" + score1 + ") should be higher than Mentor 2 (" + score2 + ")");
    }
}
