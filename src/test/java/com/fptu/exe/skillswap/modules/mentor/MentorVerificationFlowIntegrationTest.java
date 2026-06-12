package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.storage.CloudinaryService;
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
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.service.GoogleAuthService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Autowired
    private MentorVerificationDocumentRepository mentorVerificationDocumentRepository;

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
    void bigBangFlow_loginToMentorVerificationSubmit_shouldSucceed() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String email = "mentor-flow-" + nonce + "@fpt.edu.vn";
        String googleSub = "google-sub-" + nonce;
        mockGoogleLogin(email, googleSub);
        mockStorageProviders();

        ensureTagExists("JAVA_BACKEND_" + nonce, "Java Backend", TagType.TECH_SKILL);
        ensureTagExists("CV_REVIEW_" + nonce, "CV Review", TagType.HELP_TOPIC);

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

        MvcResult requestResult = mockMvc.perform(post("/api/me/mentor-verification/request")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.checklist.academicProfileCompleted").value(true))
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
                        .param("isPrimary", "true")
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
                        .param("isPrimary", "true")
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
                                  "submitNote": "Ready for review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.allowedActions.canSubmit").value(false))
                .andExpect(jsonPath("$.data.allowedActions.canUploadDocuments").value(false));

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(studentProfileRepository.findById(savedUser.getId())).isPresent();

        MentorProfile mentorProfile = mentorProfileRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(mentorProfile.getStatus()).isEqualTo(MentorStatus.PENDING_VERIFICATION);
        assertThat(mentorProfile.getHourlyRate()).isEqualByComparingTo(BigDecimal.ZERO);

        MentorVerificationRequest verificationRequest = mentorVerificationRequestRepository.findAll().stream()
                .filter(request -> request.getMentor().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(verificationRequest.getStatus()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(verificationRequest.getSubmittedNote()).isEqualTo("Ready for review");

        List<MentorVerificationDocument> documents = mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(verificationRequest.getId());
        assertThat(documents).hasSize(2);
        assertThat(documents)
                .extracting(MentorVerificationDocument::getDocumentType)
                .containsExactlyInAnyOrder(VerificationDocumentType.FPTU_AFFILIATION_PROOF, VerificationDocumentType.EXPERTISE_PROOF);
        assertThat(storedFileRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
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
}
