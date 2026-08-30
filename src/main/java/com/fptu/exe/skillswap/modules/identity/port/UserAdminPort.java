package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.modules.identity.port.dto.SystemUserDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminFilterQuery;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminListItemDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserSummaryAcademicDto;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface UserAdminPort {
    PageResponse<UserAdminListItemDto> getVisibleUsers(UserAdminFilterQuery query);
    SystemUserDto changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason);
    UserAdminDto grantAdminRole(UUID systemAdminId, String email);
    UserAdminDto revokeAdminRole(String email);
    PageResponse<UserAdminDto> getAdminUsers(BasePageRequest pageRequest);
    PageResponse<SystemUserDto> getAllUsers(BasePageRequest pageRequest);
    UserSummaryAcademicDto getAcademicProfileSummary(UUID userId);
}

