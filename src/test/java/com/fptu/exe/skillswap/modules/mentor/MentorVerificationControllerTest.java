package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationAllowedActionsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationRequestResponse;
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
