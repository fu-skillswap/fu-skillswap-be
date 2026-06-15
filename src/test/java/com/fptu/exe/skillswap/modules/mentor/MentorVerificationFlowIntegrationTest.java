package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.storage.CloudinaryService;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.storage.R2DocumentStorageService;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.service.GoogleAuthService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorVerificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Autowired
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;

    @Autowired
    private MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;

    @Autowired
    private StoredFileRepository storedFileRepository;

    @Autowired
    private TagRepository tagRepository;

    @MockBean
    private GoogleAuthService googleAuthService;

    @MockBean
    private CloudinaryService cloudinaryService;

    @MockBean
    private R2DocumentStorageService r2DocumentStorageService;

    @Test
    void bigBangFlow_loginToRevisionResubmitApprove_shouldSucceed() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String email = "mentor-flow-" + nonce + "@fpt.edu.vn";
        String googleSub = "google-sub-" + nonce;
        mockGoogleLogin(email, googleSub);
        mockStorageProviders();
        User adminUser = createAdminUser(nonce);
        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser.getId(), adminUser.getEmail(), List.of(RoleCode.ADMIN));

        String accessToken = loginAndExtractAccessToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.profileCompleted").value(false));

        Campus campus = campusRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(Campus::getName))
                .orElseThrow();
        AcademicProgram program = academicProgramRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(AcademicProgram::getCode))
                .orElseThrow();
        Specialization specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream()
                .findFirst()
                .orElseThrow();

        String studentProfileJson = """
                {
                  "studentCode": "SE192621",
                  "displayName": "Mentor Flow User",
                  "avatarUrl": "https://example.com/avatar-flow.jpg",
                  "campusId": "%s",
                  "programId": "%s",
                  "specializationId": "%s",
                  "semester": 6,
                  "intakeYear": 2021,
                  "isAlumni": false,
                  "bio": "Integration test profile"
                }
                """.formatted(campus.getId(), program.getId(), specialization.getId());

        mockMvc.perform(put("/api/me/student-profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentProfileJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCode").value("SE192621"));

        completeMentorProfile(accessToken);

        MvcResult requestResult = mockMvc.perform(post("/api/me/mentor-verification/request")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.checklist.academicProfileCompleted").value(true))
                .andExpect(jsonPath("$.data.timeline.length()").value(1))
                .andExpect(jsonPath("$.data.timeline[0].eventType").value("REQUEST_CREATED"))
                .andReturn();

        JsonNode requestNode = objectMapper.readTree(requestResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String requestId = requestNode.path("data").path("requestId").asText();
        assertThat(requestId).isNotBlank();

        MockMultipartFile affiliationFile = new MockMultipartFile(
                "file",
                "fpt-card.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(affiliationFile)
                        .param("documentType", VerificationDocumentType.FPTU_AFFILIATION_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.checklist.hasAffiliationProof").value(true))
                .andExpect(jsonPath("$.data.checklist.canSubmit").value(false));

        MockMultipartFile expertiseFile = new MockMultipartFile(
                "file",
                "portfolio.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake-pdf".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(expertiseFile)
                        .param("documentType", VerificationDocumentType.EXPERTISE_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documents.length()").value(2))
                .andExpect(jsonPath("$.data.checklist.hasExpertiseProof").value(true))
                .andExpect(jsonPath("$.data.checklist.canSubmit").value(true));

        mockMvc.perform(post("/api/me/mentor-verification/submit")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submitNote": "Ready for review",
                                  "termsAccepted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.termsAcceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.termsVersion").value("SKILLSWAP_MENTOR_TERMS_V1"))
                .andExpect(jsonPath("$.data.allowedActions.canSubmit").value(false))
                .andExpect(jsonPath("$.data.allowedActions.canUploadDocuments").value(false))
                .andExpect(jsonPath("$.data.timeline.length()").value(2))
                .andExpect(jsonPath("$.data.timeline[1].eventType").value("SUBMITTED"));

        MvcResult adminDetailResult = mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", requestId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andReturn();

        JsonNode adminDetailNode = objectMapper.readTree(adminDetailResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(adminDetailNode.path("data").path("lockExpiresAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/admin/mentor-verification/requests")
                        .with(user(adminPrincipal))
                        .param("status", VerificationStatus.PENDING_REVIEW.name())
                        .param("keyword", "mentor-flow-" + nonce))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].mentorEmail").value(email));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/request-revision", requestId)
                        .with(user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Vui lòng bổ sung ghi chú chuyên môn rõ ràng hơn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVISION"))
                .andExpect(jsonPath("$.data.reviewNote").value("Vui lòng bổ sung ghi chú chuyên môn rõ ràng hơn"))
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.timeline.length()").value(3))
                .andExpect(jsonPath("$.data.timeline[2].eventType").value("REVISION_REQUESTED"));

        mockMvc.perform(post("/api/me/mentor-verification/submit")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submitNote": "Resubmitted after revision"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.revisionCount").value(1))
                .andExpect(jsonPath("$.data.timeline.length()").value(4))
                .andExpect(jsonPath("$.data.timeline[3].eventType").value("RESUBMITTED"));

        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", requestId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.data.canReview").value(true));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", requestId)
                        .with(user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Hồ sơ hợp lệ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewNote").value("Hồ sơ hợp lệ"))
                .andExpect(jsonPath("$.data.lockedByAdminEmail").doesNotExist())
                .andExpect(jsonPath("$.data.timeline.length()").value(5))
                .andExpect(jsonPath("$.data.timeline[4].eventType").value("APPROVED"));

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(studentProfileRepository.findById(savedUser.getId())).isPresent();

        MentorProfile mentorProfile = mentorProfileRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(mentorProfile.getStatus()).isEqualTo(MentorStatus.ACTIVE);
        assertThat(mentorProfile.getSessionDuration()).isEqualTo(60);

        MentorVerificationRequest verificationRequest = mentorVerificationRequestRepository.findAll().stream()
                .filter(request -> request.getMentor().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(verificationRequest.getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(verificationRequest.getSubmittedNote()).isEqualTo("Resubmitted after revision");
        assertThat(verificationRequest.getRevisionCount()).isEqualTo(1);
        assertThat(verificationRequest.getReviewNote()).isEqualTo("Hồ sơ hợp lệ");
        assertThat(verificationRequest.getReviewedBy()).isNotNull();
        assertThat(verificationRequest.getLockedBy()).isNull();

        List<MentorVerificationDocument> documents = mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(verificationRequest.getId());
        assertThat(documents).hasSize(2);
        assertThat(documents)
                .extracting(MentorVerificationDocument::getDocumentType)
                .containsExactlyInAnyOrder(VerificationDocumentType.FPTU_AFFILIATION_PROOF, VerificationDocumentType.EXPERTISE_PROOF);
        List<MentorVerificationRequestEvent> events = mentorVerificationRequestEventRepository
                .findByRequestIdOrderByCreatedAtAsc(verificationRequest.getId());
        assertThat(events)
                .extracting(MentorVerificationRequestEvent::getEventType)
                .containsExactly(
                        MentorVerificationEventType.REQUEST_CREATED,
                        MentorVerificationEventType.SUBMITTED,
                        MentorVerificationEventType.REVISION_REQUESTED,
                        MentorVerificationEventType.RESUBMITTED,
                        MentorVerificationEventType.APPROVED
                );
        assertThat(storedFileRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void bigBangFlow_secondAdminCannotApproveWhenSoftLocked_shouldReturn409() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String email = "mentor-lock-" + nonce + "@fpt.edu.vn";
        String googleSub = "google-lock-" + nonce;
        mockGoogleLogin(email, googleSub);
        mockStorageProviders();
        User adminA = createAdminUser("a-" + nonce);
        User adminB = createAdminUser("b-" + nonce);
        UserPrincipal adminPrincipalA = UserPrincipal.create(adminA.getId(), adminA.getEmail(), List.of(RoleCode.ADMIN));
        UserPrincipal adminPrincipalB = UserPrincipal.create(adminB.getId(), adminB.getEmail(), List.of(RoleCode.ADMIN));

        String accessToken = loginAndExtractAccessToken();
        completeStudentProfile(accessToken);
        String requestId = createAndSubmitVerificationRequest(accessToken);

        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", requestId)
                        .with(user(adminPrincipalA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value(adminA.getEmail()))
                .andExpect(jsonPath("$.data.canReview").value(true));

        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", requestId)
                        .with(user(adminPrincipalB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value(adminA.getEmail()))
                .andExpect(jsonPath("$.data.canReview").value(false));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", requestId)
                        .with(user(adminPrincipalB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Trying to approve"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    private void mockGoogleLogin(String email, String googleSub) {
        GoogleAuthService.GoogleUserInfo googleUserInfo = new GoogleAuthService.GoogleUserInfo();
        googleUserInfo.setEmail(email);
        googleUserInfo.setSub(googleSub);
        googleUserInfo.setName("Mentor Flow User");
        googleUserInfo.setPicture("https://example.com/google-avatar.jpg");
        googleUserInfo.setEmail_verified("true");
        when(googleAuthService.verifyToken("test-id-token")).thenReturn(googleUserInfo);
    }

    private void mockStorageProviders() throws Exception {
        when(cloudinaryService.upload(any(), contains("mentor-verification/")))
                .thenReturn(new CloudinaryService.CloudinaryUploadResult(
                        "cloudinary/public-id",
                        "https://res.cloudinary.com/demo/image/upload/test.jpg"
                ));
        when(r2DocumentStorageService.upload(any(), contains("mentor-verification/")))
                .thenReturn(new R2DocumentStorageService.R2UploadResult(
                        "documents/test.pdf",
                        "https://example.r2.dev/skillswap/documents/test.pdf"
                ));
    }

    private Tag ensureTagExists(String code, String nameVi, TagType tagType) {
        Tag tag = Tag.builder()
                .code(code)
                .nameVi(nameVi)
                .nameEn(nameVi)
                .type(tagType)
                .status(TagStatus.ACTIVE)
                .build();
        return tagRepository.save(tag);
    }

    private String loginAndExtractAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "test-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return jsonNode.path("data").path("accessToken").asText();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private User createAdminUser(String nonce) {
        User admin = userRepository.save(User.builder()
                .email("admin-" + nonce + "@skillswap.local")
                .fullName("Admin " + nonce)
                .avatarUrl("https://example.com/admin-" + nonce + ".jpg")
                .build());
        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(admin.getId(), RoleCode.ADMIN))
                .user(admin)
                .assignedBy(admin)
                .build());
        return admin;
    }

    private void completeStudentProfile(String accessToken) throws Exception {
        Campus campus = campusRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(Campus::getName))
                .orElseThrow();
        AcademicProgram program = academicProgramRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(AcademicProgram::getCode))
                .orElseThrow();
        Specialization specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream()
                .findFirst()
                .orElseThrow();

        String studentProfileJson = """
                {
                  "studentCode": "%s",
                  "displayName": "Mentor Flow User",
                  "avatarUrl": "https://example.com/avatar-flow.jpg",
                  "campusId": "%s",
                  "programId": "%s",
                  "specializationId": "%s",
                  "semester": 6,
                  "intakeYear": 2021,
                  "isAlumni": false,
                  "bio": "Integration test profile"
                }
                """.formatted(generateStudentCode(), campus.getId(), program.getId(), specialization.getId());

        mockMvc.perform(put("/api/me/student-profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentProfileJson))
                .andExpect(status().isOk());
    }

    private void completeMentorProfile(String accessToken) throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        Tag expertiseTag = ensureTagExists("JAVA_BACKEND_" + nonce, "Java Backend", TagType.TECH_SKILL);
        Tag helpTopicTag = ensureTagExists("CV_REVIEW_" + nonce, "CV Review", TagType.HELP_TOPIC);

        mockMvc.perform(put("/api/me/mentor-profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "headline": "Java Backend Mentor",
                                  "expertiseDescription": "Có kinh nghiệm Spring Boot và PostgreSQL",
                                  "supportingSubjects": "Cơ sở dữ liệu, Lập trình Java",
                                  "isAvailable": true,
                                  "helpTopicIds": ["%s"],
                                  "teachingMode": "ONLINE",
                                  "sessionDuration": 60,
                                  "linkedinUrl": "https://linkedin.com/in/mentor-flow",
                                  "githubUrl": "https://github.com/mentor-flow",
                                  "portfolioUrl": "https://portfolio.example.com/mentor-flow"
                                }
                                """.formatted(helpTopicTag.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredFieldsCompleted").value(true));
    }

    private String generateStudentCode() {
        int intake = Math.abs(UUID.randomUUID().hashCode()) % 22 + 1;
        int suffix = Math.abs((UUID.randomUUID().toString() + intake).hashCode()) % 9000 + 1000;
        return "SE" + String.format("%02d%04d", intake, suffix);
    }

    private String createAndSubmitVerificationRequest(String accessToken) throws Exception {
        completeMentorProfile(accessToken);

        MvcResult requestResult = mockMvc.perform(post("/api/me/mentor-verification/request")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        String requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("requestId").asText();

        MockMultipartFile affiliationFile = new MockMultipartFile(
                "file",
                "fpt-card.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(affiliationFile)
                        .param("documentType", VerificationDocumentType.FPTU_AFFILIATION_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated());

        MockMultipartFile expertiseFile = new MockMultipartFile(
                "file",
                "portfolio.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake-pdf".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(expertiseFile)
                        .param("documentType", VerificationDocumentType.EXPERTISE_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/me/mentor-verification/submit")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submitNote": "Ready for review",
                                  "termsAccepted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        return requestId;
    }

    private String createDraftRequestWithDocuments(String accessToken) throws Exception {
        MvcResult requestResult = mockMvc.perform(post("/api/me/mentor-verification/request")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        String requestId = objectMapper.readTree(requestResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("requestId").asText();

        MockMultipartFile affiliationFile = new MockMultipartFile(
                "file",
                "fpt-card.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(affiliationFile)
                        .param("documentType", VerificationDocumentType.FPTU_AFFILIATION_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated());

        MockMultipartFile expertiseFile = new MockMultipartFile(
                "file",
                "portfolio.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake-pdf".getBytes(StandardCharsets.UTF_8)
                );
        mockMvc.perform(multipart("/api/me/mentor-verification/documents")
                        .file(expertiseFile)
                        .param("documentType", VerificationDocumentType.EXPERTISE_PROOF.name())
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isCreated());

        return requestId;
    }

    @Test
    void submit_whenDbErrorDuringSubmit_shouldRollbackAllChanges() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String email = "mentor-rollback-" + nonce + "@fpt.edu.vn";
        String googleSub = "google-rollback-" + nonce;
        mockGoogleLogin(email, googleSub);
        mockStorageProviders();

        String accessToken = loginAndExtractAccessToken();
        completeStudentProfile(accessToken);
        completeMentorProfile(accessToken);

        String requestId = createDraftRequestWithDocuments(accessToken);

        MentorVerificationRequest requestBefore = mentorVerificationRequestRepository.findById(UUID.fromString(requestId)).orElseThrow();
        assertThat(requestBefore.getStatus()).isEqualTo(VerificationStatus.DRAFT);

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        MentorProfile profileBefore = mentorProfileRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(profileBefore.getStatus()).isEqualTo(MentorStatus.DRAFT);

        long eventCountBefore = mentorVerificationRequestEventRepository.count();

        org.mockito.Mockito.doThrow(new RuntimeException("Simulated database failure during profile update"))
                .when(mentorProfileRepository)
                .save(org.mockito.ArgumentMatchers.argThat(profile -> 
                        profile != null && profile.getStatus() == MentorStatus.PENDING_VERIFICATION
                ));

        mockMvc.perform(post("/api/me/mentor-verification/submit")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submitNote": "Rollback test submission",
                                  "termsAccepted": true
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYS_9999"));

        MentorVerificationRequest requestAfter = mentorVerificationRequestRepository.findById(UUID.fromString(requestId)).orElseThrow();
        assertThat(requestAfter.getStatus()).isEqualTo(VerificationStatus.DRAFT);
        assertThat(requestAfter.getSubmittedAt()).isNull();

        MentorProfile profileAfter = mentorProfileRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(profileAfter.getStatus()).isEqualTo(MentorStatus.DRAFT);

        long eventCountAfter = mentorVerificationRequestEventRepository.count();
        assertThat(eventCountAfter).isEqualTo(eventCountBefore);

        org.mockito.Mockito.reset(mentorProfileRepository);
    }
}
