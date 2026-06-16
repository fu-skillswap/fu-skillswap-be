package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository {
    boolean existsById(UserRoleId id);

    UserRole save(UserRole role);

    Optional<UserRole> findById(UserRoleId id);

    void delete(UserRole role);

    List<UserRole> findByIdUserIdIn(List<UUID> userIds);

    List<UserRole> findByIdRole(RoleCode role);

    Page<UserRole> findByIdRole(RoleCode role, Pageable pageable);

    List<RoleCode> findRoleCodesByUserId(UUID userId);
}
