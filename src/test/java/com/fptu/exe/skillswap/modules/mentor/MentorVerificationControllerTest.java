package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationAllowedActionsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationDocumentResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationTimelineEventResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MentorVerificationService mentorVerificationService;

    private UUID userId;
    private UUID requestId;
    private UUID documentId;
    private UserPrincipal userPrincipal;
    private MentorVerificationRequestResponse response;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        userPrincipal = UserPrincipal.create(userId, "mentor@fpt.edu.vn", List.of(RoleCode.MENTEE));
        response = MentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .mentorUserId(userId)
                .status(VerificationStatus.DRAFT)
                .documents(List.of())
                .checklist(MentorVerificationChecklistResponse.builder()
                        .academicProfileCompleted(true)
                        .mentorProfileCompleted(true)
                        .hasAffiliationProof(false)
                        .hasExpertiseProof(false)
                        .canSubmit(false)
                        .build())
                .allowedActions(MentorVerificationAllowedActionsResponse.builder()
                        .canUploadDocuments(true)
                        .canSubmit(false)
                        .canWithdraw(true)
                        .build())
                .build();
    }

    @Test
    void getTimeline_authenticated_shouldReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(mentorVerificationService.getTimeline(userId))
                .thenReturn(List.of(MentorVerificationTimelineEventResponse.builder()
                        .id(eventId)
                        .eventType(MentorVerificationEventType.REQUEST_CREATED)
                        .toStatus(VerificationStatus.DRAFT)
                        .actorUserId(userId)
                        .actorEmail("mentor@fpt.edu.vn")
                        .actorFullName("Mentor Candidate")
                        .build()));

        mockMvc.perform(get("/api/me/mentor-verification/timeline")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.data[0].eventType").value("REQUEST_CREATED"));
    }

    @Test
    void getDocument_authenticated_shouldReturn200() throws Exception {
        when(mentorVerificationService.getDocument(userId, documentId))
                .thenReturn(MentorVerificationDocumentResponse.builder()
                        .id(documentId)
                        .documentType(VerificationDocumentType.FPTU_AFFILIATION_PROOF)
                        .status(VerificationDocumentStatus.UPLOADED)
                        .storageKind(VerificationStorageKind.IMAGE)
                        .originalFilename("fpt-card.jpg")
                        .contentType("image/jpeg")
                        .sizeBytes(10L)
                        .fileUrl("https://example.com/fpt-card.jpg")
                        .isActive(true)
                        .version(1)
                        .build());

        mockMvc.perform(get("/api/me/mentor-verification/documents/{documentId}", documentId)
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.originalFilename").value("fpt-card.jpg"));
    }

    @Test
    void requestToBecomeMentor_newDraft_shouldReturn201() throws Exception {
        when(mentorVerificationService.requestToBecomeMentor(userId))
                .thenReturn(new MentorVerificationRequestActionResult<>(response, true));

        mockMvc.perform(post("/api/me/mentor-verification/request")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CREATED_0201"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()));
    }

    @Test
    void requestToBecomeMentor_existingDraft_shouldReturn200() throws Exception {
        when(mentorVerificationService.requestToBecomeMentor(userId))
                .thenReturn(new MentorVerificationRequestActionResult<>(response, false));

        mockMvc.perform(post("/api/me/mentor-verification/request")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()));
    }

    @Test
    void deleteDocument_authenticated_shouldReturn200() throws Exception {
        when(mentorVerificationService.deleteDocument(userId, documentId)).thenReturn(response);

        mockMvc.perform(delete("/api/me/mentor-verification/documents/{documentId}", documentId)
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()));
    }

    @Test
    void withdraw_authenticated_shouldReturn200() throws Exception {
        MentorVerificationRequestResponse withdrawnResponse = MentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .mentorUserId(userId)
                .status(VerificationStatus.WITHDRAWN)
                .documents(List.of())
                .checklist(response.checklist())
                .allowedActions(MentorVerificationAllowedActionsResponse.builder()
                        .canUploadDocuments(false)
                        .canSubmit(false)
                        .canWithdraw(false)
                        .build())
                .build();
        when(mentorVerificationService.withdraw(userId)).thenReturn(withdrawnResponse);

        mockMvc.perform(post("/api/me/mentor-verification/withdraw")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }
}
