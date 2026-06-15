package com.fptu.exe.skillswap.modules.feedback;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.feedback.dto.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionFeedbackService sessionFeedbackService;

    @Test
    void submitFeedback_validRequest_shouldReturn201() throws Exception {
        UUID menteeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(menteeId, "mentee@fpt.edu.vn", List.of(RoleCode.MENTEE));

        SessionFeedbackResponse response = SessionFeedbackResponse.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .reviewerUserId(menteeId)
                .reviewerDisplayName("Mentee User")
                .revieweeUserId(UUID.randomUUID())
                .revieweeDisplayName("Mentor User")
                .rating(5)
                .comment("Great session")
                .build();

        when(sessionFeedbackService.submitFeedback(eq(menteeId), eq(sessionId), any())).thenReturn(response);

        mockMvc.perform(post("/api/sessions/" + sessionId + "/feedback")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "satisfactionLevel": 5,
                                  "comment": "Great session",
                                  "wouldRecommend": true,
                                  "isPublic": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.comment").value("Great session"));
    }

    @Test
    void submitFeedback_invalidRating_shouldReturn400() throws Exception {
        UUID menteeId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(menteeId, "mentee@fpt.edu.vn", List.of(RoleCode.MENTEE));

        mockMvc.perform(post("/api/sessions/" + sessionId + "/feedback")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 6,
                                  "satisfactionLevel": 5,
                                  "comment": "Too high rating value",
                                  "wouldRecommend": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFeedback_unauthenticated_shouldReturn401() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/sessions/" + sessionId + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "satisfactionLevel": 5,
                                  "comment": "Unauthenticated test",
                                  "wouldRecommend": true
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
