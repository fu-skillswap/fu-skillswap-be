package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.infrastructure.storage.CloudinaryService;
import com.fptu.exe.skillswap.infrastructure.storage.R2DocumentStorageService;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class MentorVerificationFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorVerificationService mentorVerificationService;

    @Autowired
    private AdminMentorVerificationService adminMentorVerificationService;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

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
    private MentorProfileService mentorProfileService;

    @MockBean
    private CloudinaryService cloudinaryService;

    @MockBean
    private R2DocumentStorageService r2DocumentStorageService;

    private User mentorUser;
    private User adminUser;

    @BeforeEach
    void setUp() throws IOException {
        // Mock Storage responses
        when(cloudinaryService.upload(any(), any())).thenReturn(
                new CloudinaryService.CloudinaryUploadResult("public_id", "https://cloudinary.com/test.jpg")
        );
        when(r2DocumentStorageService.upload(any(), any())).thenReturn(
                new R2DocumentStorageService.R2UploadResult("object_key", "https://r2.com/test.pdf")
        );

        // Setup admin and mentor users
        adminUser = userRepository.save(User.builder()
                .email("admin-flow@test.com")
                .fullName("System Admin")
                .status(UserStatus.ACTIVE)
                .build());

        mentorUser = userRepository.save(User.builder()
                .email("mentor-flow@test.com")
                .fullName("Mentor Candidate")
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void testCompleteMentorVerificationFlow() {
        UUID mentorId = mentorUser.getId();

        // 1. Initiate Verification Request (Draft)
        var actionResult = mentorVerificationService.requestToBecomeMentor(mentorId);
        assertNotNull(actionResult);
        assertTrue(actionResult.created() || actionResult.data() != null);
        MentorVerificationRequestResponse draft = actionResult.data();
        assertEquals(VerificationStatus.DRAFT, draft.status());

        // 2. Mock completing StudentProfile and MentorProfile to allow submission
        Campus campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        AcademicProgram program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        Specialization specialization = specializationRepository.findAll().stream().findFirst().orElseThrow();

        studentProfileRepository.save(StudentProfile.builder()
                .user(mentorUser)
                .studentCode("SE123456")
                .campus(campus)
                .program(program)
                .specialization(specialization)
                .semester(5)
                .intakeYear(2022)
                .build());

        Tag activeTag = tagRepository.save(Tag.builder()
                .code("TEST_HELP_TOPIC")
                .nameVi("Chủ đề test")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build());

        mentorProfileService.upsertProfile(mentorId, new MentorProfileUpsertRequest(
                "Super Mentor headline",
                "I am an expert in java",
                "Spring, Java",
                true,
                List.of(activeTag.getId()),
                TeachingMode.ONLINE,
                60,
                "https://linkedin.com",
                "https://github.com",
                "https://portfolio.com"
        ));

        // 3. Upload documents
        MockMultipartFile imageFile = new MockMultipartFile(
                "file", "proof.png", "image/png", "proof data".getBytes()
        );
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "proof.pdf", "application/pdf", "pdf data".getBytes()
        );

        mentorVerificationService.uploadDocument(
                mentorId, VerificationDocumentType.FPTU_AFFILIATION_PROOF, imageFile
        );
        mentorVerificationService.uploadDocument(
                mentorId, VerificationDocumentType.EXPERTISE_PROOF, pdfFile
        );

        // 4. Submit verification
        MentorVerificationRequestResponse pending = mentorVerificationService.submit(
                mentorId, new MentorVerificationSubmitRequest("Please review my request", true)
        );
        assertEquals(VerificationStatus.PENDING_REVIEW, pending.status());

        // Verify MentorProfile state went PENDING_VERIFICATION
        MentorProfile profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertEquals(MentorStatus.PENDING_VERIFICATION, profile.getStatus());

        // 5. Admin Detail lock + revision request
        AdminMentorVerificationRequestResponse adminDetail = adminMentorVerificationService.getRequestDetail(
                adminUser.getId(), pending.requestId()
        );
        assertNotNull(adminDetail);

        AdminMentorVerificationRequestResponse revisionRequest = adminMentorVerificationService.requestRevision(
                adminUser.getId(), pending.requestId(), "Please provide clearer documents"
        );
        assertEquals(VerificationStatus.NEEDS_REVISION, revisionRequest.status());

        // MentorProfile should return to DRAFT
        profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertEquals(MentorStatus.DRAFT, profile.getStatus());

        // 6. Resubmit
        MentorVerificationRequestResponse resubmitted = mentorVerificationService.submit(
                mentorId, new MentorVerificationSubmitRequest("Updated documents", true)
        );
        assertEquals(VerificationStatus.PENDING_REVIEW, resubmitted.status());

        // 7. Admin Approve
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), resubmitted.requestId());
        AdminMentorVerificationRequestResponse approved = adminMentorVerificationService.approve(
                adminUser.getId(), resubmitted.requestId(), "Approved!"
        );
        assertEquals(VerificationStatus.APPROVED, approved.status());

        // MentorProfile should go ACTIVE
        profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertEquals(MentorStatus.ACTIVE, profile.getStatus());
        assertNotNull(profile.getVerifiedAt());
    }
}
