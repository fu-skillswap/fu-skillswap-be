package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
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
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private NotificationService notificationService;

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

        ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditLogCaptor.capture());

        AuditLog savedAuditLog = auditLogCaptor.getValue();
        assertNotNull(savedAuditLog.getNewValue());
        assertTrue(savedAuditLog.getNewValue().contains("\\\"nội quy\\\""));
        assertTrue(savedAuditLog.getNewValue().contains("Cần khóa tạm thời"));
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
        verify(notificationService).createNotification(
                eq(userId),
                eq(com.fptu.exe.skillswap.modules.notification.domain.NotificationType.ACCOUNT_UNLOCKED),
                any(),
                any(),
                eq("USER"),
                eq(userId)
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
        verify(auditLogRepository).save(org.mockito.Mockito.argThat(auditLog -> {
            assertNotNull(auditLog.getNewValue());
            assertTrue(auditLog.getNewValue().contains("vi phạm"));
            assertTrue(auditLog.getNewValue().contains("\\n"));
            return true;
        }));
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
    void adminListUsers_shouldIncludeAcademicProfileClaimedCode() {
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

        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setClaimedStudentCode("SE123456");
        when(studentProfileRepository.findByUserIdIn(List.of(userId))).thenReturn(List.of(profile));

        PageResponse<AdminUserListItemResponse> response = adminUserService.getVisibleUsers(new AdminUserListRequest());

        assertEquals(1, response.getContent().size());
        assertNotNull(response.getContent().getFirst().academicProfile());
        assertEquals("SE123456", response.getContent().getFirst().academicProfile().claimedStudentCode());
    }

    @Test
    void adminListUsers_shouldUseBulkProfileLookup() {
        targetUser.setFullName("Nguyen Van A");
        targetUser.setRoles(new HashSet<>(List.of(RoleCode.MENTOR)));

        User targetUser2 = new User();
        targetUser2.setId(UUID.randomUUID());
        targetUser2.setStatus(UserStatus.ACTIVE);

        when(userRepository.searchAdminVisibleUsers(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(targetUser, targetUser2)));

        StudentProfile profile1 = new StudentProfile();
        profile1.setUserId(userId);
        profile1.setClaimedStudentCode("SE123456");

        StudentProfile profile2 = new StudentProfile();
        profile2.setUserId(targetUser2.getId());
        profile2.setClaimedStudentCode("SE999999");

        when(studentProfileRepository.findByUserIdIn(List.of(userId, targetUser2.getId()))).thenReturn(List.of(profile1, profile2));

        adminUserService.getVisibleUsers(new AdminUserListRequest());

        // Verifies repository is called exactly once with a list, avoiding N+1
        verify(studentProfileRepository, times(1)).findByUserIdIn(anyList());
    }

    @Test
    void adminGetUser_shouldIncludeAcademicProfileClaimedCode() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findById(userId)).thenReturn(Optional.of(targetUser));
        when(userRepository.findRoleCodesByUserId(userId)).thenReturn(List.of(RoleCode.MENTEE));

        StudentProfile profile = new StudentProfile();
        profile.setUserId(userId);
        profile.setClaimedStudentCode("SE_CONFLICT");
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        SystemUserResponse response = adminUserService.changeUserStatus(adminId, userId, false, "Good behavior");

        assertNotNull(response.academicProfile());
        assertEquals("SE_CONFLICT", response.academicProfile().claimedStudentCode());
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
