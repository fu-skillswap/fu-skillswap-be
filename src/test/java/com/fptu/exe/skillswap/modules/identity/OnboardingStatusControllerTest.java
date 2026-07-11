package com.fptu.exe.skillswap.modules.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.controller.OnboardingStatusController;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcademicService academicService;

    @MockBean
    private MentorProfileService mentorProfileService;

    @MockBean
    private MentorVerificationService mentorVerificationService;

    @Test
    void getOnboardingStatus_notAuthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/onboarding-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOnboardingStatus_whenStudentProfileIncomplete_shouldRecommendCompleteStudentProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));

        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(false);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(false);
        when(mentorVerificationService.getLatestVerificationStatus(userId)).thenReturn("NOT_STARTED");

        mockMvc.perform(get("/api/me/onboarding-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.studentProfileCompleted").value(false))
                .andExpect(jsonPath("$.data.nextRecommendedAction").value("COMPLETE_STUDENT_PROFILE"));
    }

    @Test
    void getOnboardingStatus_whenMenteeApprovedAsMentor_shouldRecommendExplore() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentor@test.com", List.of(RoleCode.MENTEE, RoleCode.MENTOR));

        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);
        when(mentorVerificationService.getLatestVerificationStatus(userId)).thenReturn("APPROVED");

        mockMvc.perform(get("/api/me/onboarding-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nextRecommendedAction").value("EXPLORE"));
    }

    @Test
    void getOnboardingStatus_whenVerificationPending_shouldRecommendWaitForApprove() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));

        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);
        when(mentorVerificationService.getLatestVerificationStatus(userId)).thenReturn("PENDING_REVIEW");

        mockMvc.perform(get("/api/me/onboarding-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nextRecommendedAction").value("WAIT_FOR_APPROVE"));
    }

    @Test
    void getOnboardingStatus_whenMentorProfileIncomplete_shouldRecommendCompleteMentorProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));

        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(false);
        when(mentorVerificationService.getLatestVerificationStatus(userId)).thenReturn("NOT_STARTED");

        mockMvc.perform(get("/api/me/onboarding-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nextRecommendedAction").value("COMPLETE_MENTOR_PROFILE_OR_EXPLORE"));
    }

    @Test
    void getOnboardingStatus_whenMentorProfileCompleteButNotSubmitted_shouldRecommendSubmitVerification() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));

        when(academicService.hasCompletedStudentProfile(userId)).thenReturn(true);
        when(mentorProfileService.hasCompletedMentorProfile(userId)).thenReturn(true);
        when(mentorVerificationService.getLatestVerificationStatus(userId)).thenReturn("NOT_STARTED");

        mockMvc.perform(get("/api/me/onboarding-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.nextRecommendedAction").value("SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE"));
    }
}
