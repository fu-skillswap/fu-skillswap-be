package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;
import java.util.Optional;

public interface UserAdminPort {
    PageResponse<IdentityAdminPortModels.UserListItem> getVisibleUsers(IdentityAdminPortModels.AdminUserListQuery request);
    IdentityAdminPortModels.SystemUserView changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason);
    IdentityAdminPortModels.AdminUserView grantAdminRole(UUID systemAdminId, String email);
    IdentityAdminPortModels.AdminUserView revokeAdminRole(String email);
    PageResponse<IdentityAdminPortModels.AdminUserView> getAdminUsers(BasePageRequest pageRequest);
    PageResponse<IdentityAdminPortModels.SystemUserView> getAllUsers(BasePageRequest pageRequest);

    Optional<IdentityAdminPortModels.VisibleUserSummary> findVisibleUserSummary(UUID userId);
    IdentityAdminPortModels.AcademicProfileSummary getAcademicProfileSummary(UUID userId);
    AdminUserReference requireAdminReference(UUID userId);
    java.util.Optional<AdminUserReference> findReference(UUID userId);
}
