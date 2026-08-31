package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AdminUserView;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.SystemUserView;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserRoleServiceTest {

    @Mock
    private UserAdminPort userAdminPort;

    private SystemUserRoleService systemUserRoleService;

    private UUID systemAdminId;

    @BeforeEach
    void setUp() {
        systemUserRoleService = new SystemUserRoleService(userAdminPort);
        systemAdminId = UUID.randomUUID();
    }

    @Test
    void grantAdminRole_delegatesToPort() {
        UUID userId = UUID.randomUUID();
        AdminUserView mockResponse = new AdminUserView(userId, "user@test.com", null, null, "ACTIVE", null, null);
        when(userAdminPort.grantAdminRole(systemAdminId, "user@test.com")).thenReturn(mockResponse);

        AdminUserView response = systemUserRoleService.grantAdminRole(systemAdminId, "user@test.com");

        assertEquals(userId, response.userId());
        assertEquals("user@test.com", response.email());
        verify(userAdminPort).grantAdminRole(systemAdminId, "user@test.com");
    }

    @Test
    void revokeAdminRole_delegatesToPort() {
        UUID userId = UUID.randomUUID();
        AdminUserView mockResponse = new AdminUserView(userId, "user@test.com", null, null, "ACTIVE", null, null);
        when(userAdminPort.revokeAdminRole("user@test.com")).thenReturn(mockResponse);

        AdminUserView response = systemUserRoleService.revokeAdminRole("user@test.com");

        assertEquals(userId, response.userId());
        verify(userAdminPort).revokeAdminRole("user@test.com");
    }

    @Test
    void getAdminUsers_delegatesToPort() {
        AdminUserView adminUser = new AdminUserView(UUID.randomUUID(), "admin@test.com", null, null, "ACTIVE", null, null);
        PageResponse<AdminUserView> mockPage = PageResponse.<AdminUserView>builder()
                .content(List.of(adminUser))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(userAdminPort.getAdminUsers(any(BasePageRequest.class))).thenReturn(mockPage);

        PageResponse<AdminUserView> response = systemUserRoleService.getAdminUsers(new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("admin@test.com", response.getContent().getFirst().email());
        verify(userAdminPort).getAdminUsers(any(BasePageRequest.class));
    }

    @Test
    void getAllUsers_delegatesToPort() {
        SystemUserView sysUser = new SystemUserView(UUID.randomUUID(), "user@test.com", null, null, "ACTIVE",
                List.of(RoleCode.MENTEE.name()), null, null, null);
        PageResponse<SystemUserView> mockPage = PageResponse.<SystemUserView>builder()
                .content(List.of(sysUser))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(userAdminPort.getAllUsers(any(BasePageRequest.class))).thenReturn(mockPage);

        PageResponse<SystemUserView> response = systemUserRoleService.getAllUsers(new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("user@test.com", response.getContent().getFirst().email());
        verify(userAdminPort).getAllUsers(any(BasePageRequest.class));
    }
}
