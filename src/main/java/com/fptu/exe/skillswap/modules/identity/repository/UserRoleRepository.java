package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUserId(UUID userId);

    @Query("select ur.id.role from UserRole ur where ur.id.userId = :userId")
    List<RoleCode> findRoleCodesByUserId(@Param("userId") UUID userId);

    List<UserRole> findByIdUserIdIn(List<UUID> userIds);

    @EntityGraph(attributePaths = "user")
    Page<UserRole> findByIdRole(RoleCode role, Pageable pageable);
}
