package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.system.dto.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.service.AdminUserService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void changeUserStatus_banNormalUser_shouldSetStatusToBannedAndRevokeSessions() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).fullName("Admin User").build();
        User user = User.builder()
                .id(userId)
                .email("test@fpt.edu.vn")
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));
        when(userSessionRepository.findByUserIdAndIsRevokedFalse(userId)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, true, "Vi phạm quy định");

        assertNotNull(response);
        assertEquals(UserStatus.BANNED, response.status());
        assertEquals(UserStatus.BANNED, user.getStatus());
        verify(userRepository).save(user);
        verify(userSessionRepository).saveAll(anyList());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void changeUserStatus_unbanNormalUser_shouldSetStatusToActive() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).fullName("Admin User").build();
        User user = User.builder()
                .id(userId)
                .email("test@fpt.edu.vn")
                .fullName("Test User")
                .status(UserStatus.BANNED)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTOR));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, false, "Kích hoạt lại");

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository).save(user);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void changeUserStatus_banSystemAdmin_shouldThrowAccessDenied() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).fullName("Admin User").build();
        User user = User.builder()
                .id(userId)
                .email("admin@fpt.edu.vn")
                .fullName("System Admin")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.SYSTEM_ADMIN));

        BaseException exception = assertThrows(BaseException.class, () -> 
                adminUserService.changeUserStatus(adminId, userId, true, "Lý do")
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeUserStatus_banOtherAdmin_shouldThrowAccessDenied() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).fullName("Admin User").build();
        User user = User.builder()
                .id(userId)
                .email("admin2@fpt.edu.vn")
                .fullName("Other Admin")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.ADMIN));

        BaseException exception = assertThrows(BaseException.class, () -> 
                adminUserService.changeUserStatus(adminId, userId, true, "Lý do")
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeUserStatus_banSelf_shouldThrowAccessDenied() {
        UUID adminId = UUID.randomUUID();

        BaseException exception = assertThrows(BaseException.class, () -> 
                adminUserService.changeUserStatus(adminId, adminId, true, "Lý do")
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void changeUserStatus_nullUserId_shouldThrowBadRequest() {
        BaseException exception = assertThrows(BaseException.class, () -> 
                adminUserService.changeUserStatus(UUID.randomUUID(), null, true, "Lý do")
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void changeUserStatus_nonExistentUser_shouldThrowUserNotFound() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User adminUser = User.builder().id(adminId).fullName("Admin User").build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BaseException exception = assertThrows(BaseException.class, () -> 
                adminUserService.changeUserStatus(adminId, userId, true, "Lý do")
        );

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }
}
