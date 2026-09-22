package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationDocumentUploadRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationRequestActionResult;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorVerificationSubmitRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationProgressResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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

    @MockBean
    private InMemoryRateLimitService rateLimitService;

    @Test
    void requestToBecomeMentor_authenticatedUser_returnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "applicant@example.com", List.of(RoleCode.MENTEE));

        MentorVerificationRequestResponse mockResponse = MentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .status(VerificationStatus.DRAFT)
                .build();
        when(mentorVerificationService.requestToBecomeMentor(eq(userId)))
                .thenReturn(new MentorVerificationRequestActionResult<>(mockResponse, true));

        mockMvc.perform(post("/api/me/mentor-verification/request")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(mentorVerificationService).requestToBecomeMentor(userId);
    }

    @Test
    void submitVerification_postToMeMentorVerification_withDocuments_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "applicant@example.com", List.of(RoleCode.MENTEE));

        MentorVerificationSubmitRequest submitRequest = new MentorVerificationSubmitRequest(
                "Đã bổ sung minh chứng trường và công tác",
                true,
                List.of(new MentorVerificationDocumentUploadRequest(
                        VerificationDocumentType.AFFILIATION_PROOF,
                        UUID.randomUUID()
                ))
        );

        MentorVerificationRequestResponse mockResponse = MentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();
        when(mentorVerificationService.submit(eq(userId), any(MentorVerificationSubmitRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/me/mentor-verification")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        verify(mentorVerificationService).submit(eq(userId), any(MentorVerificationSubmitRequest.class));
    }

    @Test
    void submitVerification_legacySubmitEndpoint_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "applicant@example.com", List.of(RoleCode.MENTEE));

        MentorVerificationSubmitRequest submitRequest = new MentorVerificationSubmitRequest(
                "Hồ sơ đầy đủ",
                true,
                null
        );

        MentorVerificationRequestResponse mockResponse = MentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .status(VerificationStatus.PENDING_REVIEW)
                .build();
        when(mentorVerificationService.submit(eq(userId), any(MentorVerificationSubmitRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/me/mentor-verification/submit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

        verify(mentorVerificationService).submit(eq(userId), any(MentorVerificationSubmitRequest.class));
    }

    @Test
    void getMyRequest_authenticatedUser_returnsRequestResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "applicant@example.com", List.of(RoleCode.MENTEE));

        when(mentorVerificationService.getMyRequest(eq(userId)))
                .thenReturn(MentorVerificationRequestResponse.builder()
                        .requestId(requestId)
                        .status(VerificationStatus.PENDING_REVIEW)
                        .build());

        mockMvc.perform(get("/api/me/mentor-verification")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()));

        verify(mentorVerificationService).getMyRequest(userId);
    }

    @Test
    void unauthenticatedRequests_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me/mentor-verification"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/me/mentor-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mentorVerificationService);
    }

    @Test
    void openApiExposesSubmitDocumentsProperty() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.MentorVerificationSubmitRequest.properties.documents").exists());
    }
}
