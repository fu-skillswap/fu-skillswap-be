package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.system.dto.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.service.AdminUserService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserService adminUserService;

    @Test
    void banUser_adminRole_shouldReturn200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));

        SystemUserResponse mockResponse = SystemUserResponse.builder()
                .userId(targetUserId)
                .email("user@fpt.edu.vn")
                .fullName("User A")
                .status(UserStatus.BANNED)
                .roles(List.of(RoleCode.MENTEE))
                .build();

        when(adminUserService.changeUserStatus(eq(adminId), eq(targetUserId), eq(true), eq("Vi phạm quy định"))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/ban")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Vi phạm quy định"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BANNED"));
    }

    @Test
    void banUser_menteeRole_shouldReturn403() throws Exception {
        UUID menteeId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(menteeId, "user@fpt.edu.vn", List.of(RoleCode.MENTEE));

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/ban")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Vi phạm quy định"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unbanUser_adminRole_shouldReturn200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(adminId, "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));

        SystemUserResponse mockResponse = SystemUserResponse.builder()
                .userId(targetUserId)
                .email("user@fpt.edu.vn")
                .fullName("User A")
                .status(UserStatus.ACTIVE)
                .roles(List.of(RoleCode.MENTEE))
                .build();

        when(adminUserService.changeUserStatus(eq(adminId), eq(targetUserId), eq(false), eq("Sửa lỗi"))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/admin/users/" + targetUserId + "/unban")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Sửa lỗi"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
