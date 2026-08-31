package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AdminUserView;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.SystemUserView;
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
    public AdminUserView grantAdminRole(UUID systemAdminId, String email) {
        return userAdminPort.grantAdminRole(systemAdminId, email);
    }

    @Transactional
    public AdminUserView revokeAdminRole(String email) {
        return userAdminPort.revokeAdminRole(email);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> getAdminUsers(BasePageRequest pageRequest) {
        return userAdminPort.getAdminUsers(pageRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemUserView> getAllUsers(BasePageRequest pageRequest) {
        return userAdminPort.getAllUsers(pageRequest);
    }
}
