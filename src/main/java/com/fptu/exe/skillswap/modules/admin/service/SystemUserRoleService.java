package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
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
        return userAdminPort.grantAdminRole(systemAdminId, email);
    }

    @Transactional
    public AdminUserResponse revokeAdminRole(String email) {
        return userAdminPort.revokeAdminRole(email);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest) {
        return userAdminPort.getAdminUsers(pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemUserResponse> getAllUsers(BasePageRequest pageRequest) {
        return userAdminPort.getAllUsers(pageRequest);
    }
}
