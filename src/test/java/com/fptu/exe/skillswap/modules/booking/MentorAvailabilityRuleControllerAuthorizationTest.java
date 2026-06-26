package com.fptu.exe.skillswap.modules.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType.DAILY;
import static com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType.OPEN;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MentorAvailabilityRuleControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MentorAvailabilityService mentorAvailabilityService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getMyRules_adminShouldReceiveForbidden() throws Exception {
        mockMvc.perform(get("/api/me/availability-rules"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mentorAvailabilityService);
    }

    @Test
    void createRule_mentorShouldBeAllowed() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "mentor@test.com", List.of(RoleCode.MENTOR));

        mockMvc.perform(post("/api/me/availability-rules")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    private UpsertAvailabilityRuleRequest validRequest() {
        return new UpsertAvailabilityRuleRequest(
                OPEN,
                DAILY,
                null,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(7),
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                "Evening mentoring"
        );
    }
}
