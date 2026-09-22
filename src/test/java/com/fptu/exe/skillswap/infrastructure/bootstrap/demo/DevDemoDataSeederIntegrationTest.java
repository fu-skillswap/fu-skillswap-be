package com.fptu.exe.skillswap.infrastructure.bootstrap.demo;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.AdministrativeProvinceRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class DevDemoDataSeederIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OauthAccountRepository oauthAccountRepository;
    @Autowired
    private AdministrativeProvinceRepository provinceRepository;
    @Autowired
    private EducationalInstitutionRepository institutionRepository;
    @Autowired
    private EducationFieldGroupRepository fieldGroupRepository;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private MentorProfileRepository mentorProfileRepository;
    @Autowired
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;
    @Autowired
    private MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    @Autowired
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    @Autowired
    private StoredFileRepository storedFileRepository;
    @Autowired
    private MentorServiceRepository mentorServiceRepository;
    @Autowired
    private MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Autowired
    private MentorDiscoveryService mentorDiscoveryService;

    private DevDemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDemoDataSeeder(
                userRepository,
                oauthAccountRepository,
                provinceRepository,
                institutionRepository,
                fieldGroupRepository,
                studentProfileRepository,
                mentorProfileRepository,
                mentorVerificationRequestRepository,
                mentorVerificationRequestEventRepository,
                mentorVerificationDocumentRepository,
                storedFileRepository,
                mentorServiceRepository,
                mentorAvailabilityRuleRepository,
                mentorAvailabilitySlotRepository
        );
        seeder.setSeederEnabled(true);
    }

    @Test
    void run_shouldSeedCanonicalProfilesAndRemainIdempotent() throws Exception {
        seeder.run();
        List<StudentProfile> profilesAfterFirstRun = studentProfileRepository.findAll();
        assertTrue(profilesAfterFirstRun.stream().anyMatch(p -> p.getProfileType() == StudentProfileType.SCHOOL_STUDENT));
        assertTrue(profilesAfterFirstRun.stream().anyMatch(p -> p.getProfileType() == StudentProfileType.UNIVERSITY_STUDENT));
        assertTrue(profilesAfterFirstRun.stream().anyMatch(p -> p.getProfileType() == StudentProfileType.ALUMNI));
        assertTrue(profilesAfterFirstRun.stream().filter(p -> p.getProfileType() == StudentProfileType.SCHOOL_STUDENT)
                .allMatch(p -> p.getCustomInstitutionName() != null && p.getCustomInstitutionProvinceId() != null));
        assertTrue(profilesAfterFirstRun.stream().filter(p -> p.getProfileType() != StudentProfileType.SCHOOL_STUDENT)
                .allMatch(p -> p.getInstitutionId() != null && p.getFieldGroupId() != null));
        assertFalse(searchByKeyword("spring").isEmpty());
        assertFalse(searchByKeyword("react").isEmpty());
        assertFalse(searchByKeyword("database").isEmpty());
        assertFalse(searchByKeyword("SWP391").isEmpty());
        assertFalse(searchByKeyword("OJT").isEmpty());
        long profileCountAfterSearchSetup = studentProfileRepository.count();

        long mentorUserCountAfterFirstRun = countMentorUsers();
        long mentorProfileCountAfterFirstRun = mentorProfileRepository.count();
        long mentorServiceCountAfterFirstRun = mentorServiceRepository.count();

        seeder.run();

        assertEquals(profileCountAfterSearchSetup, studentProfileRepository.count());
        assertEquals(mentorUserCountAfterFirstRun, countMentorUsers());
        assertEquals(mentorProfileCountAfterFirstRun, mentorProfileRepository.count());
        assertEquals(mentorServiceCountAfterFirstRun, mentorServiceRepository.count());
    }

    private List<?> searchByKeyword(String keyword) {
        User mentee = ensureSearchMentee();
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword(keyword);
        request.setSize(10);
        return mentorDiscoveryService.searchMentors(mentee.getId(), request).getContent();
    }

    private long countMentorUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null && user.getEmail().endsWith("@skillswap.local"))
                .filter(user -> user.getRoles() != null && user.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR))
                .count();
    }

    private User ensureSearchMentee() {
        return userRepository.findByEmailIncludingDeleted("seed-check-mentee@skillswap.local")
                .orElseGet(() -> {
                    User user = userRepository.save(User.builder()
                            .email("seed-check-mentee@skillswap.local")
                            .fullName("Nguyen Gia Khang")
                            .status(UserStatus.ACTIVE)
                            .build());

                    StudentProfile studentProfile = new StudentProfile();
                    studentProfile.setUser(user);
                    studentProfile.setProfileType(StudentProfileType.UNIVERSITY_STUDENT);
                    studentProfile.setInstitutionId(institutionRepository.findAll().stream().filter(i -> i.isActive()).findFirst().orElseThrow().getId());
                    studentProfile.setFieldGroupId(fieldGroupRepository.findAll().stream().filter(g -> g.isActive()).findFirst().orElseThrow().getId());
                    studentProfile.setMajorName("Computer science");
                    studentProfile.setEnrollmentYear(2022);
                    studentProfile.setBio("Mentee dùng để kiểm tra discovery search.");
                    studentProfileRepository.save(studentProfile);
                    return user;
                });
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
