package com.fptu.exe.skillswap.modules.demo.seeder;

import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DevDemoDataSeederIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OauthAccountRepository oauthAccountRepository;
    @Autowired
    private CampusRepository campusRepository;
    @Autowired
    private AcademicProgramRepository academicProgramRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
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
    private TagRepository tagRepository;
    @Autowired
    private MentorTagRepository mentorTagRepository;
    @Autowired
    private MentorDiscoveryService mentorDiscoveryService;

    private DevDemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DevDemoDataSeeder(
                userRepository,
                oauthAccountRepository,
                campusRepository,
                academicProgramRepository,
                specializationRepository,
                studentProfileRepository,
                mentorProfileRepository,
                mentorVerificationRequestRepository,
                mentorVerificationRequestEventRepository,
                mentorVerificationDocumentRepository,
                storedFileRepository,
                mentorServiceRepository,
                mentorAvailabilityRuleRepository,
                mentorAvailabilitySlotRepository,
                tagRepository,
                mentorTagRepository
        );
    }

    @Test
    void run_shouldSeedAtLeastThirtyQualifiedHcmMentorsAndStayIdempotent() throws Exception {
        seeder.run();
        long firstRunQualifiedHcmMentors = countQualifiedHcmMentors();

        assertTrue(firstRunQualifiedHcmMentors >= 30, "Expected at least 30 qualified HCM mentors after first seed run");
        assertFalse(searchByKeyword("spring").isEmpty());
        assertFalse(searchByKeyword("react").isEmpty());
        assertFalse(searchByKeyword("database").isEmpty());
        assertFalse(searchByKeyword("SWP391").isEmpty());
        assertFalse(searchByKeyword("OJT").isEmpty());

        long mentorUserCountAfterFirstRun = countMentorUsers();
        long mentorProfileCountAfterFirstRun = mentorProfileRepository.count();
        long mentorServiceCountAfterFirstRun = mentorServiceRepository.count();

        seeder.run();

        assertEquals(firstRunQualifiedHcmMentors, countQualifiedHcmMentors());
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

    private long countQualifiedHcmMentors() {
        Map<UUID, StudentProfile> studentProfiles = studentProfileRepository.findAll().stream()
                .filter(profile -> profile.getUser() != null && profile.getUser().getId() != null)
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Function.identity(), (left, right) -> left));

        return mentorProfileRepository.findByStatus(MentorStatus.ACTIVE).stream()
                .filter(profile -> profile.getUser() != null && profile.getUser().getStatus() == UserStatus.ACTIVE)
                .filter(profile -> profile.getVerifiedAt() != null)
                .filter(MentorProfile::isAvailable)
                .filter(profile -> profile.getTeachingMode() != null && profile.getSessionDuration() != null)
                .filter(profile -> hasText(profile.getHeadline()) && hasText(profile.getExpertiseDescription()) && hasText(profile.getSupportingSubjects()))
                .filter(profile -> {
                    StudentProfile studentProfile = studentProfiles.get(profile.getUserId());
                    return studentProfile != null
                            && studentProfile.getCampus() != null
                            && studentProfile.getCampus().getCode() == CampusCode.HCM;
                })
                .filter(profile -> !mentorTagRepository.findByIdMentorUserIdAndIdTagTypeIn(
                        profile.getUserId(),
                        List.of(com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType.HELP_TOPIC)
                ).isEmpty())
                .count();
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
                    studentProfile.setStudentCode("SEEDCHECK001");
                    studentProfile.setCampus(campusRepository.findByCode(CampusCode.HCM).orElseThrow());
                    studentProfile.setProgram(academicProgramRepository.findByCode("CNTT").orElseThrow());
                    studentProfile.setSpecialization(specializationRepository.findByCode("CNTT_KTPM").orElseThrow());
                    studentProfile.setSemester(6);
                    studentProfile.setIntakeYear(2022);
                    studentProfile.setBio("Mentee dùng để kiểm tra discovery search.");
                    studentProfileRepository.save(studentProfile);
                    return user;
                });
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
