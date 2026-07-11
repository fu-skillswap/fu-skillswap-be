package com.fptu.exe.skillswap.modules.admin;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminUserModerationService;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.modules.identity.event.UserStatusChangedEvent;
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
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserModerationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AdminAuditWriterService adminAuditWriterService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AdminUserModerationService adminUserService;

    private UUID adminId;
    private UUID userId;
    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();

        admin = User.builder()
                .id(adminId)
                .email("admin@test.com")
                .fullName("Admin User")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>(List.of(RoleCode.ADMIN)))
                .build();

        targetUser = User.builder()
                .id(userId)
                .email("user@test.com")
                .fullName("Target User")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void getVisibleUsers_shouldQueryRepositoryAndReturnPage() {
        AdminUserListRequest request = new AdminUserListRequest();
        request.setKeyword("test");
        request.setRole("MENTOR");
        request.setStatus("ACTIVE");

        when(userRepository.searchAdminVisibleUsers(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(targetUser)));

        PageResponse<AdminUserListItemResponse> response = adminUserService.getVisibleUsers(request);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        verify(userRepository).searchAdminVisibleUsers(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void changeUserStatus_banUser_shouldRevokeSessionsPublishEventsAndAudit() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));

        UserSession s1 = new UserSession();
        s1.setRevoked(false);
        List<UserSession> sessions = List.of(s1);
        when(userSessionRepository.findByUserIdAndIsRevokedFalse(userId)).thenReturn(sessions);

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, true, "Spamming");

        assertNotNull(response);
        assertEquals(UserStatus.BANNED, targetUser.getStatus());
        assertTrue(s1.isRevoked());
        verify(userSessionRepository).saveAll(sessions);
        verify(eventPublisher).publishEvent(any(UserBannedEvent.class));
        verify(eventPublisher).publishEvent(any(UserStatusChangedEvent.class));
        verify(userRepository).save(targetUser);
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(adminId), eq("USER"), eq(userId), eq("USER_BANNED"), any(), any()
        );
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void changeUserStatus_banReasonWithQuotes_shouldSerializeAuditJsonSafely() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));
        when(userSessionRepository.findByUserIdAndIsRevokedFalse(userId)).thenReturn(List.of());

        String reason = "Vi phạm \"nội quy\"\nCần khóa tạm thời";

        adminUserService.changeUserStatus(adminId, userId, true, reason);

        verify(adminAuditWriterService).writeOperatorEvent(
                eq(adminId), eq("USER"), eq(userId), eq("USER_BANNED"), any(), any()
        );
    }

    @Test
    void changeUserStatus_unbanUser_shouldSetActiveAndNotify() {
        targetUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, false, "Apologized");

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, targetUser.getStatus());
        verify(userRepository).save(targetUser);
        verify(eventPublisher).publishEvent(any(UserStatusChangedEvent.class));
        verify(notificationService).createNotification(
                eq(userId), eq(NotificationType.ACCOUNT_UNLOCKED), any(), any(), eq("USER"), eq(userId)
        );
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(adminId), eq("USER"), eq(userId), eq("USER_UNBANNED"), any(), any()
        );
    }

    @Test
    void changeUserStatus_unbanNotificationFailure_shouldNotRollbackPrimaryAction() {
        targetUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));
        doThrow(new RuntimeException("notification failed"))
                .when(notificationService)
                .createNotification(eq(userId), any(), any(), any(), any(), eq(userId));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, false, "Recovered");

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, targetUser.getStatus());
        verify(userRepository).save(targetUser);
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(adminId), eq("USER"), eq(userId), eq("USER_UNBANNED"), any(), any()
        );
    }

    @Test
    void changeUserStatus_banWithSpecialCharactersInReason() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));

        UserSession s1 = new UserSession();
        s1.setRevoked(false);
        List<UserSession> sessions = List.of(s1);
        when(userSessionRepository.findByUserIdAndIsRevokedFalse(userId)).thenReturn(sessions);

        String specialReason = "Lý do \"vi phạm\" điều khoản\nXuống dòng và có ký tự đặc biệt \\ / tab \t";
        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, true, specialReason);

        assertNotNull(response);
        assertEquals(UserStatus.BANNED, targetUser.getStatus());
        verify(adminAuditWriterService).writeOperatorEvent(
                eq(adminId), eq("USER"), eq(userId), eq("USER_BANNED"), any(), any()
        );
    }

    @Test
    void changeUserStatus_selfBan_shouldThrowException() {
        BaseException ex = assertThrows(BaseException.class, () ->
                adminUserService.changeUserStatus(adminId, adminId, true, "Self ban")
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    void changeUserStatus_operatorNotFound_shouldThrowException() {
        when(userRepository.findById(adminId)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () ->
                adminUserService.changeUserStatus(adminId, userId, true, "Reason")
        );
    }

    private static class UserListItemProjectionMock {
        private final UUID id;
        private final String fullName;
        private final String email;
        private final String status;
        private final String primaryLabel;
        private final int completedSessions;

        public UserListItemProjectionMock(UUID id, String fullName, String email, String status, String primaryLabel, int completedSessions) {
            this.id = id;
            this.fullName = fullName;
            this.email = email;
            this.status = status;
            this.primaryLabel = primaryLabel;
            this.completedSessions = completedSessions;
        }
    }
}
