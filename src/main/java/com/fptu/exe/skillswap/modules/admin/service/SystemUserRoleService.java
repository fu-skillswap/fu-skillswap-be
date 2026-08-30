package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.dto.SystemUserDto;
import com.fptu.exe.skillswap.modules.identity.port.dto.UserAdminDto;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemUserRoleService {

    private final UserAdminPort userAdminPort;

    @Transactional
    public AdminUserResponse grantAdminRole(UUID systemAdminId, String email) {
        UserAdminDto dto = userAdminPort.grantAdminRole(systemAdminId, email);
        return toAdminUserResponse(dto);
    }

    @Transactional
    public AdminUserResponse revokeAdminRole(String email) {
        UserAdminDto dto = userAdminPort.revokeAdminRole(email);
        return toAdminUserResponse(dto);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest) {
        PageResponse<UserAdminDto> result = userAdminPort.getAdminUsers(pageRequest);
        return PageResponse.<AdminUserResponse>builder()
                .content(result.getContent().stream().map(this::toAdminUserResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemUserResponse> getAllUsers(BasePageRequest pageRequest) {
        PageResponse<SystemUserDto> result = userAdminPort.getAllUsers(pageRequest);
        return PageResponse.<SystemUserResponse>builder()
                .content(result.getContent().stream().map(this::toSystemUserResponse).toList())
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    private AdminUserResponse toAdminUserResponse(UserAdminDto dto) {
        return AdminUserResponse.builder()
                .userId(dto.userId())
                .email(dto.email())
                .fullName(dto.fullName())
                .avatarUrl(dto.avatarUrl())
                .status(dto.status())
                .assignedBy(null)
                .assignedAt(null)
                .build();
    }

    private SystemUserResponse toSystemUserResponse(SystemUserDto dto) {
        return SystemUserResponse.builder()
                .userId(dto.userId())
                .email(dto.email())
                .fullName(dto.fullName())
                .avatarUrl(dto.avatarUrl())
                .status(dto.status())
                .roles(dto.roles())
                .lastLoginAt(dto.lastLoginAt())
                .createdAt(dto.createdAt())
                .build();
    }
}
