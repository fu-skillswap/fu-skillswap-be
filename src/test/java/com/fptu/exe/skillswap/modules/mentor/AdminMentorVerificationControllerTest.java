package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorVerificationChecklistResponse;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMentorVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminMentorVerificationService adminMentorVerificationService;

    private UUID adminId;
    private UUID requestId;
    private AdminMentorVerificationRequestResponse response;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        response = AdminMentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .mentorUserId(UUID.randomUUID())
                .mentorEmail("mentor@fpt.edu.vn")
                .mentorFullName("Mentor User")
                .status(VerificationStatus.APPROVED)
                .reviewNote("Hồ sơ hợp lệ")
                .documents(List.of())
                .checklist(MentorVerificationChecklistResponse.builder()
                        .academicProfileCompleted(true)
                        .mentorProfileCompleted(true)
                        .hasAffiliationProof(true)
                        .hasExpertiseProof(true)
                        .canSubmit(true)
                        .build())
                .build();
    }

    @Test
    void getLockStatus_adminRole_shouldReturn200() throws Exception {
        UserPrincipal adminPrincipal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));
        when(adminMentorVerificationService.getLockStatus(adminId, requestId))
                .thenReturn(AdminMentorVerificationLockResponse.builder()
                        .requestId(requestId)
                        .locked(true)
                        .canReview(true)
                        .lockedByAdminId(adminId)
                        .lockedByAdminEmail("admin@fpt.edu.vn")
                        .lockedByAdminFullName("Admin")
                        .secondsRemaining(299)
                        .build());

        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}/lock", requestId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value("admin@fpt.edu.vn"));
    }

    @Test
    void refreshLock_adminRole_shouldReturn200() throws Exception {
        UserPrincipal adminPrincipal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));
        when(adminMentorVerificationService.refreshLock(adminId, requestId))
                .thenReturn(AdminMentorVerificationLockResponse.builder()
                        .requestId(requestId)
                        .locked(true)
                        .canReview(true)
                        .lockedByAdminId(adminId)
                        .lockedByAdminEmail("admin@fpt.edu.vn")
                        .lockedByAdminFullName("Admin")
                        .secondsRemaining(299)
                        .build());

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/lock/refresh", requestId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andExpect(jsonPath("$.data.secondsRemaining").value(299));
    }

    @Test
    void approve_adminRole_shouldReturn200() throws Exception {
        UserPrincipal adminPrincipal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));
        when(adminMentorVerificationService.approve(adminId, requestId, "Hồ sơ hợp lệ")).thenReturn(response);

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", requestId)
                        .with(user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Hồ sơ hợp lệ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void approve_systemAdminRole_shouldReturn403() throws Exception {
        UserPrincipal systemAdminPrincipal = UserPrincipal.create(adminId, "sysadmin@fpt.edu.vn", List.of(RoleCode.SYSTEM_ADMIN));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", requestId)
                        .with(user(systemAdminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Hồ sơ hợp lệ"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_nonAdminRole_shouldReturn403() throws Exception {
        UserPrincipal menteePrincipal = UserPrincipal.create(UUID.randomUUID(), "user@fpt.edu.vn", List.of(RoleCode.MENTEE));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/approve", requestId)
                        .with(user(menteePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Hồ sơ hợp lệ"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getQueue_withFilters_shouldReturn200() throws Exception {
        UserPrincipal adminPrincipal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));
        PageResponse<AdminMentorVerificationQueueItemResponse> pageResponse = PageResponse.<AdminMentorVerificationQueueItemResponse>builder()
                .content(List.of(AdminMentorVerificationQueueItemResponse.builder()
                        .requestId(requestId)
                        .mentorUserId(UUID.randomUUID())
                        .mentorEmail("mentor@fpt.edu.vn")
                        .mentorFullName("Mentor User")
                        .status(VerificationStatus.PENDING_REVIEW)
                        .revisionCount(1)
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
        when(adminMentorVerificationService.getQueue(any())).thenReturn(pageResponse);

        mockMvc.perform(get("/api/admin/mentor-verification/requests")
                        .with(user(adminPrincipal))
                        .param("status", "PENDING_REVIEW")
                        .param("keyword", "mentor")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void getRequestDetail_adminRole_shouldReturnLockMetadata() throws Exception {
        UserPrincipal adminPrincipal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));
        AdminMentorVerificationRequestResponse detailResponse = AdminMentorVerificationRequestResponse.builder()
                .requestId(requestId)
                .mentorUserId(UUID.randomUUID())
                .mentorEmail("mentor@fpt.edu.vn")
                .mentorFullName("Mentor User")
                .status(VerificationStatus.PENDING_REVIEW)
                .lockedByAdminEmail("admin@fpt.edu.vn")
                .canReview(true)
                .documents(List.of())
                .checklist(MentorVerificationChecklistResponse.builder()
                        .academicProfileCompleted(true)
                        .mentorProfileCompleted(true)
                        .hasAffiliationProof(true)
                        .hasExpertiseProof(true)
                        .canSubmit(true)
                        .build())
                .build();
        when(adminMentorVerificationService.getRequestDetail(adminId, requestId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/admin/mentor-verification/requests/{requestId}", requestId)
                        .with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lockedByAdminEmail").value("admin@fpt.edu.vn"))
                .andExpect(jsonPath("$.data.canReview").value(true));
    }
}
