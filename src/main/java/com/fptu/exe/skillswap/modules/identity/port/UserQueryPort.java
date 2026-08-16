package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserQueryPort {

    boolean existsById(UUID userId);

    Optional<User> findUserById(UUID userId);

    User getUserReference(UUID userId);

    UserStatus getUserStatus(UUID userId);

    boolean isUserActive(UUID userId);

    org.springframework.data.domain.Page<User> findUsersByRole(com.fptu.exe.skillswap.shared.constant.RoleCode role, org.springframework.data.domain.Pageable pageable);

    Optional<StudentProfile> findStudentProfileWithDetailsByUserId(UUID userId);

    Optional<StudentProfile> findStudentProfileById(UUID userId);

    List<StudentProfile> findStudentProfilesByUserIdIn(List<UUID> userIds);
}
