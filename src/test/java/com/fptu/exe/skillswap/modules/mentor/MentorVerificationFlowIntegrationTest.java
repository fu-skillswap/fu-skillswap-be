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
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
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
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.admin.service.AdminMentorVerificationModerationService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
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
    private AdminMentorVerificationModerationService adminMentorVerificationService;

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
    private MentorTagRepository mentorTagRepository;

    @Autowired
    private MentorProfileService mentorProfileService;

    @Autowired
    private com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Autowired
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;

    @Autowired
    private StorageGateway storageGateway;

    private User mentorUser;
    private User otherUser;
    private User adminUser;
    private User secondAdminUser;

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

        otherUser = userRepository.save(User.builder()
                .email("other-mentor-flow@test.com")
                .fullName("Other Mentor Candidate")
                .status(UserStatus.ACTIVE)
                .build());

        secondAdminUser = userRepository.save(User.builder()
                .email("second-admin-flow@test.com")
                .fullName("Second Admin")
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
                .claimedStudentCode("SE123456")
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
                true,
                List.of(activeTag.getId()),
                List.of(new com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest("PRJ301", "Java Web", java.math.BigDecimal.valueOf(8.5))),
                3,
                3,
                2,
                "https://github.com",
                "https://portfolio.com",
                "0912345678"
        ));

        var savedProfile = mentorProfileService.getMyProfile(mentorId);
        assertEquals("0912345678", savedProfile.phoneNumber());
        assertTrue(savedProfile.requiredFieldsCompleted());

        String objectKey1 = verificationObjectKey(mentorId, "fptu-proof.png");
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                        objectKey1,
                        "fptu-proof.png",
                        "image/png",
                        1024L
                )
        );
        String objectKey2 = verificationObjectKey(mentorId, "expertise-proof.png");
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.EXPERTISE_PROOF,
                        objectKey2,
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
    void requestToBecomeMentor_existingDraftWithLegacyProfileMissingSupportLevelsShouldNotThrow() {
        UUID mentorId = mentorUser.getId();
        MentorProfile profile = new MentorProfile();
        profile.setUser(mentorUser);
        profile.setStatus(MentorStatus.DRAFT);
        profile.setHeadline("Legacy mentor profile");
        profile.setExpertiseDescription("Legacy profile before support levels existed");
        profile.setPhoneNumber("0912345678");
        profile.setAvailable(true);
        MentorProfile savedProfile = mentorProfileRepository.save(profile);

        Tag activeTag = tagRepository.save(Tag.builder()
                .code("TEST_LEGACY_HELP_TOPIC_" + Math.abs(mentorId.hashCode()))
                .nameVi("Chủ đề legacy")
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build());
        mentorTagRepository.save(MentorTag.builder()
                .id(new MentorTagId(mentorId, activeTag.getId(), MentorTagType.HELP_TOPIC))
                .mentorProfile(savedProfile)
                .tag(activeTag)
                .build());

        var first = mentorVerificationService.requestToBecomeMentor(mentorId);
        var second = mentorVerificationService.requestToBecomeMentor(mentorId);

        assertTrue(first.created());
        assertFalse(second.created());
        assertFalse(second.data().checklist().mentorProfileCompleted());
        assertFalse(second.data().checklist().canSubmit());
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

    @Test
    void approveVerification_shouldGrantMentorRoleAndKeepMenteeRole() {
        UUID mentorId = mentorUser.getId();
        mentorUser.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE);
        userRepository.save(mentorUser);

        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK");

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        assertTrue(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));
        assertTrue(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE));
    }

    @Test
    void approveVerification_whenUserAlreadyMentor_shouldNotDuplicateRole() {
        UUID mentorId = mentorUser.getId();
        mentorUser.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR);
        userRepository.save(mentorUser);

        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK");

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        long mentorRoleCount = updatedMentor.getRoles().stream()
                .filter(r -> r == com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR)
                .count();
        assertEquals(1, mentorRoleCount);
    }

    @Test
    void approveVerification_whenUserIsAdmin_shouldThrowConflictAndNotChangeState() {
        UUID mentorId = mentorUser.getId();
        mentorUser.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN);
        userRepository.save(mentorUser);

        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());

        var exception = assertThrows(BaseException.class, () -> 
            adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK")
        );
        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        assertFalse(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));
        
        var request = mentorVerificationRequestRepository.findById(pending.requestId()).orElseThrow();
        assertEquals(VerificationStatus.PENDING_REVIEW, request.getStatus());
        
        var profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertNotEquals(MentorStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void approveVerification_whenUserIsSystemAdmin_shouldThrowConflictAndNotChangeState() {
        UUID mentorId = mentorUser.getId();
        mentorUser.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN);
        userRepository.save(mentorUser);

        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());

        var exception = assertThrows(BaseException.class, () -> 
            adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK")
        );
        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        assertFalse(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));
        
        var request = mentorVerificationRequestRepository.findById(pending.requestId()).orElseThrow();
        assertEquals(VerificationStatus.PENDING_REVIEW, request.getStatus());
    }

    @Test
    void rejectVerification_shouldNotGrantMentorRole() {
        UUID mentorId = mentorUser.getId();
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.reject(adminUser.getId(), pending.requestId(), "No");

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        assertFalse(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));
    }

    @Test
    void needsRevision_shouldNotGrantMentorRole() {
        UUID mentorId = mentorUser.getId();
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.requestRevision(adminUser.getId(), pending.requestId(), "Fix");

        User updatedMentor = userRepository.findById(mentorId).orElseThrow();
        assertFalse(updatedMentor.getRoles().contains(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR));
    }

    @Test
    void approveVerification_shouldActivateMentorProfile() {
        UUID mentorId = mentorUser.getId();
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorId);
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK");

        var profile = mentorProfileRepository.findById(mentorId).orElseThrow();
        assertEquals(MentorStatus.ACTIVE, profile.getStatus());
    }

    @Test
    void withdraw_afterApproved_shouldBeRejected() {
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorUser.getId());
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());
        adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK");

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.withdraw(mentorUser.getId()));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void getDocument_otherUserShouldNotSeeAnotherUsersDocument() {
        MentorVerificationRequestResponse mentorRequest = createSubmittedRequest(mentorUser.getId());
        createDraftRequest(otherUser.getId());
        UUID documentId = mentorRequest.documents().getFirst().id();

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.getDocument(otherUser.getId(), documentId));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteDocument_otherUserShouldNotDeleteAnotherUsersDocument() {
        MentorVerificationRequestResponse mentorRequest = createDraftRequest(mentorUser.getId());
        createDraftRequest(otherUser.getId());
        UUID documentId = mentorRequest.documents().getFirst().id();

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.deleteDocument(otherUser.getId(), documentId));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void submit_otherUserShouldNotSubmitAnotherUsersRequest() {
        createDraftRequest(mentorUser.getId());

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.submit(otherUser.getId(), new MentorVerificationSubmitRequest("x", true)));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void withdraw_otherUserShouldNotWithdrawAnotherUsersRequest() {
        createDraftRequest(mentorUser.getId());

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.withdraw(otherUser.getId()));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteDocument_afterSubmit_shouldBeRejected() {
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorUser.getId());
        UUID documentId = pending.documents().getFirst().id();

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.deleteDocument(mentorUser.getId(), documentId));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void withdraw_pendingReviewLockedByAdmin_shouldBeRejected() {
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorUser.getId());
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());

        BaseException exception = assertThrows(BaseException.class,
                () -> mentorVerificationService.withdraw(mentorUser.getId()));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void approve_missingRequiredDocuments_shouldBeRejected() {
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorUser.getId());
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());

        var expertiseDocument = mentorVerificationDocumentRepository
                .findByRequestIdAndDocumentTypeAndIsActiveTrueOrderByUploadedAtDesc(
                        pending.requestId(),
                        VerificationDocumentType.EXPERTISE_PROOF
                ).stream().findFirst().orElseThrow();
        expertiseDocument.setActive(false);
        expertiseDocument.setStatus(VerificationDocumentStatus.REMOVED);
        mentorVerificationDocumentRepository.save(expertiseDocument);

        BaseException exception = assertThrows(BaseException.class,
                () -> adminMentorVerificationService.approve(adminUser.getId(), pending.requestId(), "OK"));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void approve_lockedByAnotherAdmin_shouldBeRejected() {
        MentorVerificationRequestResponse pending = createSubmittedRequest(mentorUser.getId());
        adminMentorVerificationService.getRequestDetail(adminUser.getId(), pending.requestId());

        BaseException exception = assertThrows(BaseException.class,
                () -> adminMentorVerificationService.approve(secondAdminUser.getId(), pending.requestId(), "OK"));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    private MentorVerificationRequestResponse createDraftRequest(UUID mentorId) {
        mentorVerificationService.requestToBecomeMentor(mentorId);
        String objectKey1 = verificationObjectKey(mentorId, "fptu-proof.png");
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                        objectKey1,
                        "fptu-proof.png",
                        "image/png",
                        1024L
                )
        );
        String objectKey2 = verificationObjectKey(mentorId, "expertise-proof.png");
        return mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.EXPERTISE_PROOF,
                        objectKey2,
                        "expertise-proof.png",
                        "image/png",
                        2048L
                )
        );
    }

    private MentorVerificationRequestResponse createSubmittedRequest(UUID mentorId) {
        User candidate = userRepository.findById(mentorId).orElseThrow();
        mentorVerificationService.requestToBecomeMentor(mentorId);

        Campus campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        AcademicProgram program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        Specialization specialization = specializationRepository.findAll().stream().findFirst().orElseThrow();

        studentProfileRepository.save(StudentProfile.builder()
                .user(candidate)
                .claimedStudentCode("SE" + Math.abs(mentorId.hashCode()))
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
                true,
                List.of(activeTag.getId()),
                List.of(new com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest("PRJ301", "Java Web", java.math.BigDecimal.valueOf(8.5))),
                3,
                3,
                2,
                "https://github.com/test",
                "https://portfolio.com/test",
                "0912345678"
        ));

        String objectKey1 = verificationObjectKey(mentorId, "fptu-proof.png");
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.FPTU_AFFILIATION_PROOF,
                        objectKey1,
                        "fptu-proof.png",
                        "image/png",
                        1024L
                )
        );
        String objectKey2 = verificationObjectKey(mentorId, "expertise-proof.png");
        mentorVerificationService.uploadDocument(
                mentorId,
                new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.EXPERTISE_PROOF,
                        objectKey2,
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

    private String verificationObjectKey(UUID mentorId, String filename) {
        return "skillswap/verification-documents/users/" + mentorId + "/" + filename;
    }
}
