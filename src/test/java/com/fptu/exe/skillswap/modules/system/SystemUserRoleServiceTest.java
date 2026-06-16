package com.fptu.exe.skillswap.modules.system;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemUserRoleService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemUserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SystemUserRoleService systemUserRoleService;

    private UUID systemAdminId;
    private User systemAdmin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        systemAdminId = UUID.randomUUID();
        systemAdmin = buildUser(systemAdminId, "system@test.com", Set.of(RoleCode.SYSTEM_ADMIN));
        targetUser = buildUser(UUID.randomUUID(), "user@test.com", new HashSet<>(Set.of(RoleCode.MENTEE)));
    }

    @Test
    void grantAdminRole_successful_shouldNormalizeEmailAndAddRole() {
        when(userRepository.findActiveByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(targetUser));
        when(userRepository.findById(systemAdminId)).thenReturn(Optional.of(systemAdmin));

        AdminUserResponse response = systemUserRoleService.grantAdminRole(systemAdminId, " User@Test.com ");

        assertEquals(targetUser.getId(), response.userId());
        assertTrue(targetUser.getRoles().contains(RoleCode.ADMIN));
        verify(userRepository).findActiveByEmailIgnoreCase("user@test.com");
        verify(userRepository).save(targetUser);
    }

    @Test
    void grantAdminRole_duplicateRole_shouldThrowConflict() {
        targetUser.getRoles().add(RoleCode.ADMIN);
        when(userRepository.findActiveByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(targetUser));

        BaseException exception = assertThrows(BaseException.class, () ->
                systemUserRoleService.grantAdminRole(systemAdminId, "user@test.com")
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void revokeAdminRole_withoutAdmin_shouldThrowConflict() {
        when(userRepository.findActiveByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(targetUser));

        BaseException exception = assertThrows(BaseException.class, () ->
                systemUserRoleService.revokeAdminRole("user@test.com")
        );

        assertEquals(ErrorCode.RESOURCE_CONFLICT, exception.getErrorCode());
    }

    @Test
    void getAdminUsers_shouldMapPagedResult() {
        targetUser.getRoles().add(RoleCode.ADMIN);
        when(userRepository.findUsersByRole(eq(RoleCode.ADMIN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(targetUser)));

        PageResponse<AdminUserResponse> response = systemUserRoleService.getAdminUsers(new BasePageRequest());

        assertEquals(1, response.getContent().size());
        assertEquals(targetUser.getEmail(), response.getContent().getFirst().email());
    }

    @Test
    void getAllUsers_shouldMapRolesFromEntityAndApplySortFallback() {
        BasePageRequest request = new BasePageRequest();
        request.setSortBy("unknown");
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(targetUser)));

        PageResponse<SystemUserResponse> response = systemUserRoleService.getAllUsers(request);

        assertEquals(1, response.getContent().size());
        assertFalse(response.getContent().getFirst().roles().isEmpty());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertEquals("createdAt: DESC", captor.getValue().getSort().toString());
    }

    private User buildUser(UUID id, String email, Set<RoleCode> roles) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("User " + email);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new HashSet<>(roles));
        return user;
    }

    private void assertTrue(boolean value) {
        assertEquals(true, value);
    }
}
