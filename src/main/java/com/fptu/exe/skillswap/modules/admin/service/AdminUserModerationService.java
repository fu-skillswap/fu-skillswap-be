package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserAcademicResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.dto.SystemUserDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminFilterQuery;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminListItemDto;
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
        UserAdminFilterQuery query = new UserAdminFilterQuery();
        if (request != null) {
            query.setKeyword(request.getKeyword());
            query.setRole(request.getRole());
            query.setStatus(request.getStatus());
            query.setPage(request.getPage());
            query.setSize(request.getSize());
            query.setSortBy(request.getSortBy());
            query.setDirection(request.getDirection());
        }
        PageResponse<UserAdminListItemDto> result = userAdminPort.getVisibleUsers(query);
        return PageResponse.<AdminUserListItemResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional
    public SystemUserResponse changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
        SystemUserDto dto = userAdminPort.changeUserStatus(adminId, userId, ban, reason);
        SystemUserResponse response = SystemUserResponse.builder()
                .userId(dto.userId())
                .email(dto.email())
                .fullName(dto.fullName())
                .avatarUrl(dto.avatarUrl())
                .status(dto.status())
                .roles(dto.roles())
                .lastLoginAt(dto.lastLoginAt())
                .createdAt(dto.createdAt())
                .build();

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

    private AdminUserListItemResponse toResponse(UserAdminListItemDto dto) {
        return AdminUserListItemResponse.builder()
                .userId(dto.userId())
                .email(dto.email())
                .fullName(dto.fullName())
                .avatarUrl(dto.avatarUrl())
                .status(dto.status())
                .roles(dto.roles())
                .lastLoginAt(dto.lastLoginAt())
                .createdAt(dto.createdAt())
                .academicProfile(dto.academicProfile() != null
                        ? AdminUserAcademicResponse.builder().claimedStudentCode(dto.academicProfile().claimedStudentCode()).build()
                        : null)
                .build();
    }
}
