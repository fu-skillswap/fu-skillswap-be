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

    List<User> findUsersByRole(com.fptu.exe.skillswap.shared.constant.RoleCode role);


    Optional<StudentProfile> findStudentProfileWithDetailsByUserId(UUID userId);

    Optional<StudentProfile> findStudentProfileById(UUID userId);

    List<StudentProfile> findStudentProfilesByUserIdIn(List<UUID> userIds);

    List<User> findUsersByIdIn(java.util.Collection<UUID> userIds);

    Optional<User> findAdminVisibleUserById(UUID userId);

    Optional<UserSummaryRecord> findUserSummaryById(UUID userId);

    Optional<StudentProfileRecord> findStudentProfileRecordByUserId(UUID userId);

    java.util.Map<UUID, UserSummaryRecord> findUserSummariesByIdIn(java.util.Collection<UUID> userIds);

    java.util.Map<UUID, StudentProfileRecord> findStudentProfileRecordsByIdIn(java.util.Collection<UUID> userIds);

    void grantMentorRole(UUID userId);
}
