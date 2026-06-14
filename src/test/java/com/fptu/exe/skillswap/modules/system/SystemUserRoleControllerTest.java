package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.system.dto.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemUserRoleService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemUserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemUserRoleService systemUserRoleService;

    @Test
    void grantAdminRole_systemAdminRole_shouldReturn200() throws Exception {
        UUID systemAdminId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(systemAdminId, "root@fpt.edu.vn", List.of(RoleCode.SYSTEM_ADMIN));

        when(systemUserRoleService.grantAdminRole(eq(systemAdminId), eq("admin@fpt.edu.vn")))
                .thenReturn(response(adminId, "admin@fpt.edu.vn"));

        mockMvc.perform(post("/api/system/users/admin-role/grant")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@fpt.edu.vn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@fpt.edu.vn"));
    }

    @Test
    void grantAdminRole_adminRole_shouldReturn403() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "admin@fpt.edu.vn", List.of(RoleCode.ADMIN));

        mockMvc.perform(post("/api/system/users/admin-role/grant")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "target@fpt.edu.vn"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeAdminRole_systemAdminRole_shouldReturn200() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "root@fpt.edu.vn", List.of(RoleCode.SYSTEM_ADMIN));
        when(systemUserRoleService.revokeAdminRole("admin@fpt.edu.vn"))
                .thenReturn(response(UUID.randomUUID(), "admin@fpt.edu.vn"));

        mockMvc.perform(post("/api/system/users/admin-role/revoke")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@fpt.edu.vn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("admin@fpt.edu.vn"));
    }

    @Test
    void getAdminUsers_systemAdminRole_shouldReturn200() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "root@fpt.edu.vn", List.of(RoleCode.SYSTEM_ADMIN));
        when(systemUserRoleService.getAdminUsers(any()))
                .thenReturn(PageResponse.<AdminUserResponse>builder()
                        .content(List.of(response(UUID.randomUUID(), "admin@fpt.edu.vn")))
                        .page(0)
                        .size(10)
                        .totalElements(1)
                        .totalPages(1)
                        .last(true)
                        .build());

        mockMvc.perform(get("/api/system/users/admins")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("admin@fpt.edu.vn"));
    }

    private AdminUserResponse response(UUID userId, String email) {
        return AdminUserResponse.builder()
                .userId(userId)
                .email(email)
                .fullName("Admin User")
                .status(UserStatus.ACTIVE)
                .assignedAt(LocalDateTime.now())
                .build();
    }
}
