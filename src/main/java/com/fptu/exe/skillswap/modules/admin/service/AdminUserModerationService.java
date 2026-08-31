package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListFilterRequest;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AdminUserListQuery;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.SystemUserView;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.UserListItem;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserModerationService {

    private final UserAdminPort userAdminPort;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public PageResponse<UserListItem> getVisibleUsers(AdminUserListFilterRequest request) {
        AdminUserListFilterRequest safe = request == null ? new AdminUserListFilterRequest() : request;
        return userAdminPort.getVisibleUsers(new AdminUserListQuery(
                safe.getKeyword(), safe.getRole(), safe.getStatus(), safe.getPage(), safe.getSize(),
                safe.getSortBy(), safe.getDirection()));
    }

    @Transactional
    public SystemUserView changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
        SystemUserView response = userAdminPort.changeUserStatus(adminId, userId, ban, reason);

        adminAuditWriterService.writeOperatorEvent(
                adminId,
                "USER",
                userId,
                ban ? "USER_BANNED" : "USER_UNBANNED",
                Map.of(),
                Map.of("status", response.status() == null ? "" : response.status(), "reason", reason == null ? "" : reason)
        );

        return response;
    }
}
