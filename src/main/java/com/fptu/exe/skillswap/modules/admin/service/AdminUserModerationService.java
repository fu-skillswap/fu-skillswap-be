package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
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
    public PageResponse<AdminUserListItemResponse> getVisibleUsers(AdminUserListRequest request) {
        return userAdminPort.getVisibleUsers(request);
    }

    @Transactional
    public SystemUserResponse changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
        SystemUserResponse response = userAdminPort.changeUserStatus(adminId, userId, ban, reason);

        adminAuditWriterService.writeOperatorEvent(
                adminId,
                "USER",
                userId,
                ban ? "USER_BANNED" : "USER_UNBANNED",
                Map.of(),
                Map.of("status", response.status() == null ? "" : response.status().name(), "reason", reason == null ? "" : reason)
        );

        return response;
    }
}
