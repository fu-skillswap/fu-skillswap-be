package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.system.dto.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemUserRoleService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @InjectMocks
    private SystemUserRoleService service;

    @Test
    void grantAdminRole_existingUserWithoutAdminRole_shouldCreateRole() {
        UUID systemAdminId = UUID.randomUUID();
        User systemAdmin = user(systemAdminId, "root@fpt.edu.vn");
        User targetUser = user(UUID.randomUUID(), "admin@fpt.edu.vn");

        when(userRepository.findActiveByEmailIgnoreCase("admin@fpt.edu.vn")).thenReturn(Optional.of(targetUser));
        when(userRoleRepository.existsById(new UserRoleId(targetUser.getId(), RoleCode.ADMIN))).thenReturn(false);
        when(userRepository.findById(systemAdminId)).thenReturn(Optional.of(systemAdmin));
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserResponse response = service.grantAdminRole(systemAdminId, " ADMIN@fpt.edu.vn ");

        assertEquals(targetUser.getId(), response.userId());
        assertEquals(systemAdminId, response.assignedBy());

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(captor.capture());
        assertEquals(new UserRoleId(targetUser.getId(), RoleCode.ADMIN), captor.getValue().getId());
    }

    @Test
    void grantAdminRole_existingAdmin_shouldThrowConflict() {
        User targetUser = user(UUID.randomUUID(), "admin@fpt.edu.vn");
        when(userRepository.findActiveByEmailIgnoreCase("admin@fpt.edu.vn")).thenReturn(Optional.of(targetUser));
        when(userRoleRepository.existsById(new UserRoleId(targetUser.getId(), RoleCode.ADMIN))).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class,
                () -> service.grantAdminRole(UUID.randomUUID(), "admin@fpt.edu.vn"));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void revokeAdminRole_existingAdmin_shouldDeleteRole() {
        User targetUser = user(UUID.randomUUID(), "admin@fpt.edu.vn");
        UserRole role = UserRole.builder()
                .id(new UserRoleId(targetUser.getId(), RoleCode.ADMIN))
                .user(targetUser)
                .assignedAt(LocalDateTime.now())
                .build();

        when(userRepository.findActiveByEmailIgnoreCase("admin@fpt.edu.vn")).thenReturn(Optional.of(targetUser));
        when(userRoleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        AdminUserResponse response = service.revokeAdminRole("admin@fpt.edu.vn");

        assertEquals(targetUser.getId(), response.userId());
        verify(userRoleRepository).delete(role);
    }

    @Test
    void getAdminUsers_shouldReturnPagedAdminUsers() {
        User admin = user(UUID.randomUUID(), "admin@fpt.edu.vn");
        UserRole role = UserRole.builder()
                .id(new UserRoleId(admin.getId(), RoleCode.ADMIN))
                .user(admin)
                .assignedAt(LocalDateTime.now())
                .build();
        BasePageRequest request = new BasePageRequest();

        when(userRoleRepository.findByIdRole(eq(RoleCode.ADMIN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(role), request.getPageable(), 1));

        PageResponse<AdminUserResponse> response = service.getAdminUsers(request);

        assertEquals(1, response.getTotalElements());
        assertEquals("admin@fpt.edu.vn", response.getContent().get(0).email());
    }

    private User user(UUID id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
