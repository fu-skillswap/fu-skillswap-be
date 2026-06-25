package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorProfileControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MentorProfileService mentorProfileService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void putMentorProfile_adminShouldReceiveForbidden() throws Exception {
        mockMvc.perform(put("/api/me/mentor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mentorProfileService);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void getMentorProfile_systemAdminShouldReceiveForbidden() throws Exception {
        mockMvc.perform(get("/api/me/mentor-profile"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mentorProfileService);
    }

    @Test
    void getMentorProfile_authenticatedMenteeShouldBeAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));
        when(mentorProfileService.getMyProfile(eq(userId))).thenReturn(MentorProfileResponse.builder().exists(false).build());

        mockMvc.perform(get("/api/me/mentor-profile")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        ))))
                .andExpect(status().isOk());

        verify(mentorProfileService).getMyProfile(userId);
    }

    private MentorProfileUpsertRequest validRequest() {
        return new MentorProfileUpsertRequest(
                "Backend Mentor",
                "Hỗ trợ Java và Spring Boot",
                "EXE101, SWP391",
                true,
                List.of(UUID.randomUUID()),
                TeachingMode.ONLINE,
                60,
                "https://linkedin.com/in/test",
                "https://github.com/test",
                "https://portfolio.example.com",
                "0912345678"
        );
    }
}
