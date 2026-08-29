package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryAcademicProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface UserAdminPort {
    PageResponse<AdminUserListItemResponse> getVisibleUsers(AdminUserListRequest request);
    SystemUserResponse changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason);
    AdminUserResponse grantAdminRole(UUID systemAdminId, String email);
    AdminUserResponse revokeAdminRole(String email);
    PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest);
    PageResponse<SystemUserResponse> getAllUsers(BasePageRequest pageRequest);
    AdminUserSummaryAcademicProfileResponse getAcademicProfileSummary(UUID userId);
}
