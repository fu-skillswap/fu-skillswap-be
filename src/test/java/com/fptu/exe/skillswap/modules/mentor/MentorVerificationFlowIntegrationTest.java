package com.fptu.exe.skillswap.modules.mentor;

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
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
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
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository mentorVerificationRequestRepository;

    private User mentorUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
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
                "https://portfolio.com",
                "0912345678"
        ));

        var savedProfile = mentorProfileService.getMyProfile(mentorId);
        assertEquals("0912345678", savedProfile.phoneNumber());
        assertTrue(savedProfile.requiredFieldsCompleted());

        // 3. Save documents as Cloudinary URLs sent from FE
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                        "https://res.cloudinary.com/demo/image/upload/v123/proof.png",
                        "mentor-verification/integration/fptu-proof",
                        "proof.png",
                        "image/png",
                        1024L
                )
        );
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.EXPERTISE_PROOF,
                        "https://res.cloudinary.com/demo/image/upload/v123/expertise-proof.png",
                        "mentor-verification/integration/expertise-proof",
                        "expertise-proof.png",
                        "image/png",
                        2048L
                )
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
        assertEquals(VerificationStatus.APPROVED, mentorVerificationService.getMyRequest(mentorId).status());

        // MentorProfile should go ACTIVE
        profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertEquals(MentorStatus.ACTIVE, profile.getStatus());
        assertNotNull(profile.getVerifiedAt());
    }

    @Test
    void requestToBecomeMentor_existingDraftShouldReturnSameRequest() {
        UUID mentorId = mentorUser.getId();

        var first = mentorVerificationService.requestToBecomeMentor(mentorId);
        var second = mentorVerificationService.requestToBecomeMentor(mentorId);

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.data().requestId(), second.data().requestId());
    }

    @Test
    void getMyRequest_afterWithdrawShouldStillReturnLatestFinalStatus() {
        UUID mentorId = mentorUser.getId();

        MentorVerificationRequestResponse draft = mentorVerificationService.requestToBecomeMentor(mentorId).data();
        MentorVerificationRequestResponse withdrawn = mentorVerificationService.withdraw(mentorId);

        assertEquals(VerificationStatus.WITHDRAWN, withdrawn.status());
        assertEquals(draft.requestId(), mentorVerificationService.getMyRequest(mentorId).requestId());
        assertEquals(VerificationStatus.WITHDRAWN, mentorVerificationService.getMyRequest(mentorId).status());
        assertFalse(mentorVerificationService.getTimeline(mentorId).isEmpty());
    }

    @Test
    void requestToBecomeMentor_afterRejectedShouldCreateNewRequestWithPreviousRequest() {
        UUID mentorId = mentorUser.getId();

        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        AdminMentorVerificationRequestResponse rejected = adminMentorVerificationService.reject(
                adminUser.getId(),
                pending.requestId(),
                "Minh chứng chưa đạt"
        );

        assertEquals(VerificationStatus.REJECTED, rejected.status());
        assertEquals(VerificationStatus.REJECTED, mentorVerificationService.getMyRequest(mentorId).status());

        MentorVerificationRequestResponse reopened = mentorVerificationService.requestToBecomeMentor(mentorId).data();
        assertEquals(VerificationStatus.DRAFT, reopened.status());

        var latest = mentorVerificationRequestRepository.findById(reopened.requestId()).orElseThrow();
        assertNotNull(latest.getPreviousRequest());
        assertEquals(rejected.requestId(), latest.getPreviousRequest().getId());
    }

    private MentorVerificationRequestResponse createSubmittedRequest(UUID mentorId) {
        mentorVerificationService.requestToBecomeMentor(mentorId);

        Campus campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        AcademicProgram program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        Specialization specialization = specializationRepository.findAll().stream().findFirst().orElseThrow();

        studentProfileRepository.save(StudentProfile.builder()
                .user(mentorUser)
                .studentCode("SE" + Math.abs(mentorId.hashCode()))
                .campus(campus)
                .program(program)
                .specialization(specialization)
                .semester(5)
                .intakeYear(2022)
                .build());

        Tag activeTag = tagRepository.save(Tag.builder()
                .code("TEST_HELP_TOPIC_" + Math.abs(mentorId.hashCode()))
                .nameVi("Chủ đề test " + mentorId)
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
                "https://linkedin.com/in/test",
                "https://github.com/test",
                "https://portfolio.com/test",
                "0912345678"
        ));

        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                        "https://res.cloudinary.com/demo/image/upload/v123/proof.png",
                        "mentor-verification/integration/" + mentorId + "/fptu-proof",
                        "proof.png",
                        "image/png",
                        1024L
                )
        );
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.EXPERTISE_PROOF,
                        "https://res.cloudinary.com/demo/image/upload/v123/expertise-proof.png",
                        "mentor-verification/integration/" + mentorId + "/expertise-proof",
                        "expertise-proof.png",
                        "image/png",
                        2048L
                )
        );

        return mentorVerificationService.submit(
                mentorId,
                new MentorVerificationSubmitRequest("Please review my request", true)
        );
    }
}
