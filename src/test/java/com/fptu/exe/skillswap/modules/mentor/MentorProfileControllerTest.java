package com.fptu.exe.skillswap.modules.mentor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileUpsertRequest;
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
    void upsertProfile_invalidRequest_shouldReturn400() throws Exception {
        MentorProfileUpsertRequest request = validRequest("");

        mockMvc.perform(put("/api/me/mentor-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL_3001"))
                .andExpect(jsonPath("$.message").value("headline: Tiêu đề hồ sơ mentor không được để trống"));
    }

    @Test
    void upsertProfile_validRequest_shouldReturn200() throws Exception {
        MentorProfileUpsertRequest request = validRequest("Backend Developer");

        when(mentorProfileService.upsertProfile(eq(userId), any(MentorProfileUpsertRequest.class)))
                .thenReturn(MentorProfileResponse.builder()
                        .exists(true)
                        .userId(userId)
                        .headline("Backend Developer")
                        .expertiseDescription("Có kinh nghiệm Spring Boot và PostgreSQL")
                        .teachingMode(TeachingMode.ONLINE)
                        .build());

        mockMvc.perform(put("/api/me/mentor-profile")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.headline").value("Backend Developer"))
                .andExpect(jsonPath("$.data.expertiseDescription").value("Có kinh nghiệm Spring Boot và PostgreSQL"));
    }

    private MentorProfileUpsertRequest validRequest(String headline) {
        return new MentorProfileUpsertRequest(
                headline,
                "Có kinh nghiệm Spring Boot và PostgreSQL",
                "Cơ sở dữ liệu, Lập trình Java, Kiến trúc API",
                true,
                List.of(UUID.randomUUID()),
                TeachingMode.ONLINE,
                60,
                "https://www.linkedin.com/in/example",
                "https://github.com/example",
                "https://example.dev"
        );
    }
}
