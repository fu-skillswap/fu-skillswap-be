package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorSubjectResultRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MentorProfileService mentorProfileService;

    @Test
    void getMyProfile_authenticatedMentee_shouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@example.com", List.of(RoleCode.MENTEE));
        when(mentorProfileService.getMyProfile(eq(userId)))
                .thenReturn(MentorProfileResponse.builder().userId(userId).headline("Java Mentor").build());

        mockMvc.perform(get("/api/me/mentor-profile")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Java Mentor"));

        verify(mentorProfileService).getMyProfile(userId);
    }

    @Test
    void upsertProfile_withInstitutionAndPrimaryFieldGroup_shouldReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID fieldGroupId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentor@example.com", List.of(RoleCode.MENTOR));

        MentorProfileUpsertRequest request = new MentorProfileUpsertRequest(
                "Senior Engineer & Mentor",
                "Kinh nghiệm 5 năm xây dựng backend microservices.",
                true,
                List.of(new MentorSubjectResultRequest("CS101", "Computer Science", BigDecimal.valueOf(9.0))),
                3,
                3,
                2,
                "https://github.com/mentor",
                "https://portfolio.example.com",
                "0912345678",
                institutionId,
                null,
                null,
                null,
                fieldGroupId
        );

        when(mentorProfileService.upsertProfile(eq(userId), any(MentorProfileUpsertRequest.class)))
                .thenReturn(MentorProfileResponse.builder()
                        .userId(userId)
                        .headline(request.headline())
                        .institutionId(institutionId)
                        .primaryFieldGroupId(fieldGroupId)
                        .build());

        mockMvc.perform(put("/api/me/mentor-profile")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.institutionId").value(institutionId.toString()))
                .andExpect(jsonPath("$.data.primaryFieldGroupId").value(fieldGroupId.toString()));

        verify(mentorProfileService).upsertProfile(eq(userId), any(MentorProfileUpsertRequest.class));
    }

    @Test
    void unauthenticatedRequests_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me/mentor-profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/me/mentor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mentorProfileService);
    }

    @Test
    void openApiExposesCatalogInstitutionAndFieldGroupProperties() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.MentorProfileUpsertRequest.properties.institutionId").exists())
                .andExpect(jsonPath("$.components.schemas.MentorProfileUpsertRequest.properties.companyOrOrganization").exists())
                .andExpect(jsonPath("$.components.schemas.MentorProfileUpsertRequest.properties.primaryFieldGroupId").exists());
    }
}
