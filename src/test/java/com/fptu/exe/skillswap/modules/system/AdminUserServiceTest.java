package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.system.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.service.AdminUserService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID adminId;
    private UUID userId;
    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();

        admin = new User();
        admin.setId(adminId);
        admin.setEmail("admin@test.com");
        admin.setStatus(UserStatus.ACTIVE);

        targetUser = new User();
        targetUser.setId(userId);
        targetUser.setEmail("user@test.com");
        targetUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void changeUserStatus_banSuccessful() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTOR));

        List<UserSession> sessions = new ArrayList<>();
        UserSession s1 = new UserSession();
        s1.setRevoked(false);
        sessions.add(s1);
        when(userSessionRepository.findByUserIdAndIsRevokedFalse(userId)).thenReturn(sessions);

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, true, "Violating terms");

        assertNotNull(response);
        assertEquals(UserStatus.BANNED, targetUser.getStatus());
        assertTrue(s1.isRevoked());
        verify(userSessionRepository).saveAll(sessions);
        verify(eventPublisher).publishEvent(any(UserBannedEvent.class));
        verify(userRepository).save(targetUser);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void changeUserStatus_unbanSuccessful() {
        targetUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, false, "Good behavior");

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, targetUser.getStatus());
        verify(eventPublisher, never()).publishEvent(any(UserBannedEvent.class));
        verify(userRepository).save(targetUser);
    }

    @Test
    void changeUserStatus_selfBan_shouldThrowException() {
        BaseException ex = assertThrows(BaseException.class, () ->
                adminUserService.changeUserStatus(adminId, adminId, true, "Self ban")
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void changeUserStatus_banAdmin_shouldThrowException() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.ADMIN));

        BaseException ex = assertThrows(BaseException.class, () ->
                adminUserService.changeUserStatus(adminId, userId, true, "Ban admin")
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void getVisibleUsers_shouldMapPagedUsers() {
        targetUser.setFullName("Nguyen Van A");
        targetUser.setRoles(new HashSet<>(List.of(RoleCode.MENTOR)));

        when(userRepository.searchAdminVisibleUsers(
                isNull(),
                isNull(),
                isNull(),
                eq(RoleCode.MENTEE),
                eq(RoleCode.MENTOR),
                eq(RoleCode.ADMIN),
                eq(RoleCode.SYSTEM_ADMIN),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(targetUser)));

        PageResponse<AdminUserListItemResponse> response = adminUserService.getVisibleUsers(new AdminUserListRequest());

        assertEquals(1, response.getContent().size());
        assertEquals(targetUser.getEmail(), response.getContent().getFirst().email());
        assertEquals(List.of(RoleCode.MENTOR), response.getContent().getFirst().roles());
    }

    @Test
    void getVisibleUsers_withInvalidRole_shouldThrowBadRequest() {
        AdminUserListRequest request = new AdminUserListRequest();
        request.setRole("ADMIN");

        BaseException exception = assertThrows(BaseException.class, () -> adminUserService.getVisibleUsers(request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(userRepository, never()).searchAdminVisibleUsers(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
