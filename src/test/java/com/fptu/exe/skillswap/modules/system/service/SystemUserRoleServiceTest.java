package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.admin.service.SystemUserRoleService;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.dto.SystemUserDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminDto;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
        UserAdminDto mockDto = UserAdminDto.builder()
                .userId(userId)
                .email("user@test.com")
                .fullName("User Full Name")
                .avatarUrl("avatar.jpg")
                .status(UserStatus.ACTIVE)
                .build();
        when(userAdminPort.grantAdminRole(systemAdminId, "user@test.com")).thenReturn(mockDto);

        AdminUserResponse response = systemUserRoleService.grantAdminRole(systemAdminId, "user@test.com");

        assertEquals(userId, response.userId());
        assertEquals("user@test.com", response.email());
        verify(userAdminPort).grantAdminRole(systemAdminId, "user@test.com");
    }

    @Test
    void revokeAdminRole_delegatesToPort() {
        UUID userId = UUID.randomUUID();
        UserAdminDto mockDto = UserAdminDto.builder()
                .userId(userId)
                .email("user@test.com")
                .fullName("User Full Name")
                .avatarUrl("avatar.jpg")
                .status(UserStatus.ACTIVE)
                .build();
        when(userAdminPort.revokeAdminRole("user@test.com")).thenReturn(mockDto);

        AdminUserResponse response = systemUserRoleService.revokeAdminRole("user@test.com");

        assertEquals(userId, response.userId());
        verify(userAdminPort).revokeAdminRole("user@test.com");
    }

    @Test
    void getAdminUsers_delegatesToPort() {
        UserAdminDto adminUser = UserAdminDto.builder()
                .userId(UUID.randomUUID())
                .email("admin@test.com")
                .fullName("Admin User")
                .avatarUrl("avatar.jpg")
                .status(UserStatus.ACTIVE)
                .build();
        PageResponse<UserAdminDto> mockPage = PageResponse.<UserAdminDto>builder()
                .content(List.of(adminUser))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(userAdminPort.getAdminUsers(any(BasePageRequest.class))).thenReturn(mockPage);

        PageResponse<AdminUserResponse> response = systemUserRoleService.getAdminUsers(new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("admin@test.com", response.getContent().getFirst().email());
        verify(userAdminPort).getAdminUsers(any(BasePageRequest.class));
    }

    @Test
    void getAllUsers_delegatesToPort() {
        SystemUserDto sysUser = SystemUserDto.builder()
                .userId(UUID.randomUUID())
                .email("user@test.com")
                .fullName("Test User")
                .avatarUrl("avatar.jpg")
                .status(UserStatus.ACTIVE)
                .roles(List.of(RoleCode.MENTEE))
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .build();
        PageResponse<SystemUserDto> mockPage = PageResponse.<SystemUserDto>builder()
                .content(List.of(sysUser))
                .page(0).size(20).totalElements(1).totalPages(1)
                .build();
        when(userAdminPort.getAllUsers(any(BasePageRequest.class))).thenReturn(mockPage);

        PageResponse<SystemUserResponse> response = systemUserRoleService.getAllUsers(new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals("user@test.com", response.getContent().getFirst().email());
        verify(userAdminPort).getAllUsers(any(BasePageRequest.class));
    }
}
