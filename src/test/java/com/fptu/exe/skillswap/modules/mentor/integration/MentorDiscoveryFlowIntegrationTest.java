package com.fptu.exe.skillswap.modules.mentor.integration;

import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
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
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.test.context.ActiveProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.test.database.replace=none"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MentorDiscoveryFlowIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private MentorTagRepository mentorTagRepository;

    @Autowired
    private MentorDiscoveryService mentorDiscoveryService;

    @Autowired private EducationalInstitutionRepository institutionRepository;
    @Autowired private EducationFieldGroupRepository fieldGroupRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User menteeUser;
    private User mentor1User;
    private User mentor2User;

    private Tag helpTopicTag;

    @BeforeEach
    void setUp() {
        var institution = institutionRepository.findAll().stream().filter(i -> i.isActive()).findFirst().orElseThrow();
        var fieldGroup = fieldGroupRepository.findAll().stream().filter(g -> g.isActive()).findFirst().orElseThrow();

        // Ensure tag of type HELP_TOPIC exists
        helpTopicTag = tagRepository.save(Tag.builder()
                .code("DISC_SPRING")
                .nameVi("Spring Boot")
                .type(TagType.TECH_SKILL)
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
                .profileType(StudentProfileType.UNIVERSITY_STUDENT)
                .institutionId(institution.getId())
                .fieldGroupId(fieldGroup.getId())
                .majorName("Computer science")
                .onboardingCompleted(true)
                .build());

        // Setup school-type mentor.
        mentor1User = userRepository.save(User.builder()
                .email("mentor1-disc@test.com")
                .fullName("Expert Java Mentor")
                .roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE, com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR))
                .status(UserStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(mentor1User)
                .profileType(StudentProfileType.SCHOOL_STUDENT)
                .customInstitutionName("Demo Secondary School")
                .customInstitutionProvinceId(institution.getProvince().getId())
                .onboardingCompleted(true)
                .build());
        MentorProfile profile1 = mentorProfileRepository.save(MentorProfile.builder()
                .userId(mentor1User.getId())
                .status(MentorStatus.ACTIVE)
                .headline("Java, Spring Boot developer")
                .expertiseDescription("Teaching Spring Framework and Java programming")
                .isAvailable(true)
                .sessionDuration(60)
                .teachingMode(TeachingMode.HYBRID)
                .verifiedAt(LocalDateTime.now().minusDays(10))
                .build());
        mentorTagRepository.save(MentorTag.builder()
                .id(new MentorTagId(mentor1User.getId(), helpTopicTag.getId(), MentorTagType.EXPERTISE))
                .mentorProfile(profile1)
                .tag(helpTopicTag)
                .build());
        mentorServiceRepository.save(activeOneToOneService(profile1, "Java va Spring Boot 1:1"));

        // Setup alumni mentor with canonical catalog relations.
        mentor2User = userRepository.save(User.builder()
                .email("mentor2-disc@test.com")
                .fullName("General Tech Mentor")
                .roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE, com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR))
                .status(UserStatus.ACTIVE)
                .build());
        studentProfileRepository.save(StudentProfile.builder()
                .user(mentor2User)
                .profileType(StudentProfileType.ALUMNI)
                .institutionId(institution.getId())
                .fieldGroupId(fieldGroup.getId())
                .majorName("Information systems")
                .graduationYear(2023)
                .onboardingCompleted(true)
                .build());
        MentorProfile profile2 = mentorProfileRepository.save(MentorProfile.builder()
                .userId(mentor2User.getId())
                .status(MentorStatus.ACTIVE)
                .headline("Software Engineer Generalist")
                .expertiseDescription("Algorithm design and web systems")
                .isAvailable(true)
                .sessionDuration(60)
                .teachingMode(TeachingMode.HYBRID)
                .verifiedAt(LocalDateTime.now().minusDays(5))
                .build());
        mentorTagRepository.save(MentorTag.builder()
                .id(new MentorTagId(mentor2User.getId(), helpTopicTag.getId(), MentorTagType.EXPERTISE))
                .mentorProfile(profile2)
                .tag(helpTopicTag)
                .build());
        mentorServiceRepository.save(activeOneToOneService(profile2, "Tu van ky thuat 1:1"));
    }

    private MentorService activeOneToOneService(MentorProfile profile, String title) {
        return MentorService.builder()
                .mentorProfile(profile)
                .title(title)
                .description("Dich vu mentoring 1:1 dang mo")
                .expectedOutcome("Nguoi hoc co huong giai quyet ro rang")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
                .isActive(true)
                .deliveryMode(MentorServiceDeliveryMode.ONE_TO_ONE)
                .build();
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
        // Mentor 1 is first because its mentoring content matches the keyword.
        assertEquals("Expert Java Mentor", searchResults.getContent().getFirst().displayName());

        // 2. Fetch recommendations
        List<MentorRecommendationResponse> recommendations = mentorDiscoveryService.getRecommendations(
                menteeUser.getId(), 5
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());        assertTrue(recommendations.stream().allMatch(recommendation -> recommendation.matchScore() != null));
    }

    @Test
    void unfilteredDiscoveryIncludesCanonicalTypesAndOptionalProfiles() throws Exception {
        var institution = institutionRepository.findAll().stream().findFirst().orElseThrow();
        var fieldGroup = fieldGroupRepository.findAll().stream().findFirst().orElseThrow();
        User canonicalUser = userRepository.save(User.builder().email("mentor-canonical-disc@test.com")
                .fullName("Canonical Mentor").roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE,
                        com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)).status(UserStatus.ACTIVE).build());
        studentProfileRepository.save(StudentProfile.builder().user(canonicalUser)
                .profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(institution.getId())
                .fieldGroupId(fieldGroup.getId()).majorName("Computer Science").onboardingCompleted(true).build());
        MentorProfile canonicalMentor = mentorProfileRepository.save(MentorProfile.builder().userId(canonicalUser.getId())
                .status(MentorStatus.ACTIVE).headline("Canonical education mentor")
                .expertiseDescription("Mentoring with canonical education data").isAvailable(true)
                .sessionDuration(60).teachingMode(TeachingMode.HYBRID).verifiedAt(LocalDateTime.now().minusDays(2)).build());
        mentorServiceRepository.save(activeOneToOneService(canonicalMentor, "Canonical mentor service"));

        User schoolUser = userRepository.save(User.builder().email("mentor-school-disc@test.com")
                .fullName("School Mentor").roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE,
                        com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)).status(UserStatus.ACTIVE).build());
        studentProfileRepository.save(StudentProfile.builder().user(schoolUser)
                .profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("Hanoi High School")
                .customInstitutionProvinceId(institution.getProvince().getId()).onboardingCompleted(true).build());
        MentorProfile schoolMentor = mentorProfileRepository.save(MentorProfile.builder().userId(schoolUser.getId())
                .status(MentorStatus.ACTIVE).headline("School mentor")
                .expertiseDescription("School mentoring with canonical school data").isAvailable(true)
                .sessionDuration(60).teachingMode(TeachingMode.HYBRID).verifiedAt(LocalDateTime.now().minusDays(3)).build());
        mentorServiceRepository.save(activeOneToOneService(schoolMentor, "School mentor service"));

        User alumniUser = userRepository.save(User.builder().email("mentor-alumni-disc@test.com")
                .fullName("Alumni Mentor").roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE,
                        com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)).status(UserStatus.ACTIVE).build());
        studentProfileRepository.save(StudentProfile.builder().user(alumniUser).profileType(StudentProfileType.ALUMNI)
                .institutionId(institution.getId()).fieldGroupId(fieldGroup.getId()).majorName("Computer Science")
                .graduationYear(2024).onboardingCompleted(true).build());
        MentorProfile alumniMentor = mentorProfileRepository.save(MentorProfile.builder().userId(alumniUser.getId())
                .status(MentorStatus.ACTIVE).headline("Alumni mentor")
                .expertiseDescription("Mentoring from canonical alumni data").isAvailable(true)
                .sessionDuration(60).teachingMode(TeachingMode.HYBRID).verifiedAt(LocalDateTime.now().minusDays(4)).build());
        mentorServiceRepository.save(activeOneToOneService(alumniMentor, "Alumni mentor service"));

        User noEducationUser = userRepository.save(User.builder().email("mentor-no-education-disc@test.com")
                .fullName("Mentor Without Education").roles(java.util.Set.of(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE,
                        com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)).status(UserStatus.ACTIVE).build());
        MentorProfile noEducationMentor = mentorProfileRepository.save(MentorProfile.builder().userId(noEducationUser.getId())
                .status(MentorStatus.ACTIVE).headline("Mentor with optional education omitted")
                .expertiseDescription("No education information provided").isAvailable(true)
                .sessionDuration(60).teachingMode(TeachingMode.HYBRID).verifiedAt(LocalDateTime.now().minusDays(1)).build());
        mentorServiceRepository.save(activeOneToOneService(noEducationMentor, "Mentor without education service"));

        MentorDiscoverySearchRequest allRequest = new MentorDiscoverySearchRequest();
        allRequest.setSize(50);
        var all = mentorDiscoveryService.searchMentors(menteeUser.getId(), allRequest);
        assertTrue(all.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(canonicalUser.getId())));
        assertTrue(all.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(schoolUser.getId())));
        assertTrue(all.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(alumniUser.getId())));
        assertTrue(all.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(noEducationUser.getId())));
        JsonNode schoolEducation = detailJson(schoolUser.getId()).path("education");
        assertEquals("SCHOOL_STUDENT", schoolEducation.path("type").asText());
        assertEquals("Hanoi High School", schoolEducation.path("schoolName").asText());
        assertEquals(institution.getProvince().getId().toString(), schoolEducation.path("province").path("id").asText());
        assertTrue(schoolEducation.path("institution").isNull());
        assertTrue(schoolEducation.path("fieldGroup").isNull());
        assertTrue(schoolEducation.path("major").isNull());

        JsonNode universityEducation = detailJson(canonicalUser.getId()).path("education");
        assertEquals("UNIVERSITY_STUDENT", universityEducation.path("type").asText());
        assertTrue(universityEducation.path("schoolName").isNull());
        assertTrue(universityEducation.path("province").isNull());
        assertEquals(institution.getId().toString(), universityEducation.path("institution").path("id").asText());
        assertEquals(fieldGroup.getId().toString(), universityEducation.path("fieldGroup").path("id").asText());
        assertEquals("Computer Science", universityEducation.path("major").asText());

        JsonNode alumniEducation = detailJson(alumniUser.getId()).path("education");
        assertEquals("ALUMNI", alumniEducation.path("type").asText());
        assertTrue(alumniEducation.path("schoolName").isNull());
        assertTrue(alumniEducation.path("province").isNull());
        assertEquals(institution.getId().toString(), alumniEducation.path("institution").path("id").asText());
        assertEquals(fieldGroup.getId().toString(), alumniEducation.path("fieldGroup").path("id").asText());
        assertEquals("Computer Science", alumniEducation.path("major").asText());

        JsonNode privateEducation = detailJson(canonicalUser.getId());
assertEquals("Canonical Mentor", objectMapper.treeToValue(privateEducation, com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse.class).identity().displayName());
        assertTrue(detailJson(noEducationUser.getId()).path("education").isNull());

        MentorDiscoverySearchRequest unfiltered = new MentorDiscoverySearchRequest();
        unfiltered.setSize(50);
        var allEducationTypes = mentorDiscoveryService.searchMentors(menteeUser.getId(), unfiltered);
        assertTrue(allEducationTypes.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(mentor1User.getId())));
        assertTrue(allEducationTypes.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(canonicalUser.getId())));
        assertTrue(allEducationTypes.getContent().stream().anyMatch(card -> card.identity().mentorUserId().equals(alumniUser.getId())));
        assertTrue(allEducationTypes.getTotalElements() >= 4);
    }


    @Test
    void discoveryQueryCountDoesNotGrowPerReturnedMentor() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        MentorDiscoverySearchRequest smallPageRequest = new MentorDiscoverySearchRequest();
        smallPageRequest.setSize(1);
        statistics.clear();
        mentorDiscoveryService.searchMentors(null, smallPageRequest);
        long oneRowQueryCount = statistics.getQueryExecutionCount();

        MentorDiscoverySearchRequest fullPageRequest = new MentorDiscoverySearchRequest();
        fullPageRequest.setSize(50);
        statistics.clear();
        mentorDiscoveryService.searchMentors(null, fullPageRequest);
        long allRowsQueryCount = statistics.getQueryExecutionCount();

        assertTrue(oneRowQueryCount > 0);
        assertTrue(allRowsQueryCount <= oneRowQueryCount + 2,
                "query count must remain bounded as the page result grows; one row=" + oneRowQueryCount + ", full page=" + allRowsQueryCount);
    }

    private JsonNode detailJson(UUID mentorUserId) {
        return objectMapper.valueToTree(mentorDiscoveryService.getMentorDetail(mentorUserId));
    }

    @Test
    void discoveryPaginationUsesStablePagesAndExactTotals() {
        MentorDiscoverySearchRequest firstRequest = new MentorDiscoverySearchRequest();
        firstRequest.setSize(1);
        firstRequest.setPage(0);
        MentorDiscoverySearchRequest secondRequest = new MentorDiscoverySearchRequest();
        secondRequest.setSize(1);
        secondRequest.setPage(1);
        var first = mentorDiscoveryService.searchMentors(menteeUser.getId(), firstRequest);
        var repeatedFirstPage = mentorDiscoveryService.searchMentors(menteeUser.getId(), firstRequest);
        var second = mentorDiscoveryService.searchMentors(menteeUser.getId(), secondRequest);
        assertEquals(first.getContent().getFirst().identity().mentorUserId(),
                repeatedFirstPage.getContent().getFirst().identity().mentorUserId());
        assertEquals(2, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(1, first.getContent().size());
        assertEquals(1, second.getContent().size());
        assertNotEquals(first.getContent().getFirst().identity().mentorUserId(), second.getContent().getFirst().identity().mentorUserId());
        secondRequest.setPage(99);
        var outOfRange = mentorDiscoveryService.searchMentors(menteeUser.getId(), secondRequest);
        assertTrue(outOfRange.getContent().isEmpty());
        assertEquals(2, outOfRange.getTotalElements());
    }
}
