package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileBasicRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileExpertiseRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

    private UUID userId;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = UserPrincipal.create(userId, "mentor@fpt.edu.vn", List.of(RoleCode.MENTOR));
    }

    @Test
    void getMyProfile_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/me/mentor-profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfile_authenticated_shouldReturnProfileState() throws Exception {
        when(mentorProfileService.getMyProfile(userId))
                .thenReturn(MentorProfileResponse.empty(userId));

        mockMvc.perform(get("/api/me/mentor-profile")
                        .with(user(userPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()));
    }

    @Test
    void upsertBasic_invalidRequest_shouldReturn400() throws Exception {
        MentorProfileBasicRequest request = new MentorProfileBasicRequest(
                "",
                "Software Engineer",
                "FPT Software",
                "https://example.com/avatar.jpg",
                "Bio",
                true
        );

        mockMvc.perform(put("/api/me/mentor-profile/basic")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("headline: Tiêu đề hồ sơ mentor không được để trống"));
    }

    @Test
    void upsertBasic_validRequest_shouldReturn200() throws Exception {
        MentorProfileBasicRequest request = new MentorProfileBasicRequest(
                "Backend Developer",
                "Software Engineer",
                "FPT Software",
                "https://example.com/avatar.jpg",
                "Bio",
                true
        );

        when(mentorProfileService.upsertBasic(eq(userId), any(MentorProfileBasicRequest.class)))
                .thenReturn(MentorProfileResponse.builder()
                        .exists(true)
                        .userId(userId)
                        .headline("Backend Developer")
                        .build());

        mockMvc.perform(put("/api/me/mentor-profile/basic")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.headline").value("Backend Developer"));
    }

    @Test
    void upsertExpertise_emptyTags_shouldReturn400() throws Exception {
        MentorProfileExpertiseRequest request = new MentorProfileExpertiseRequest(
                List.of(),
                List.of(UUID.randomUUID()),
                BigDecimal.ONE,
                "Software Engineering",
                null,
                null,
                null,
                null
        );

        mockMvc.perform(put("/api/me/mentor-profile/expertise")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("expertiseTagIds: Danh sách chuyên môn không được để trống"));
    }

    @Test
    void upsertExpertise_validRequest_shouldReturn200() throws Exception {
        MentorProfileExpertiseRequest request = new MentorProfileExpertiseRequest(
                List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()),
                new BigDecimal("2.5"),
                "Software Engineering",
                "Backend APIs",
                "https://www.linkedin.com/in/example",
                "https://github.com/example",
                "https://example.dev"
        );

        when(mentorProfileService.upsertExpertise(eq(userId), any(MentorProfileExpertiseRequest.class)))
                .thenReturn(MentorProfileResponse.builder()
                        .exists(true)
                        .userId(userId)
                        .industry("Software Engineering")
                        .yearsOfExperience(new BigDecimal("2.5"))
                        .build());

        mockMvc.perform(put("/api/me/mentor-profile/expertise")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("Software Engineering"))
                .andExpect(jsonPath("$.data.yearsOfExperience").value(2.5));
    }
}
