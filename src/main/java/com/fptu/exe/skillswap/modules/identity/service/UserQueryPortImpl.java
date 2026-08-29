package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
public class UserQueryPortImpl implements UserQueryPort, UserLockPort {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EntityManager entityManager;

    public UserQueryPortImpl(UserRepository userRepository, EntityManager entityManager) {
        this(userRepository, null, entityManager);
    }

    @Override
    public boolean existsById(UUID userId) {
        return userId != null && userRepository.existsById(userId);
    }

    @Override
    public Optional<User> findUserById(UUID userId) {
        return userId == null ? Optional.empty() : userRepository.findById(userId);
    }

    @Override
    public User getUserReference(UUID userId) {
        if (userId == null) {
            return null;
        }
        return entityManager.getReference(User.class, userId);
    }

    @Override
    public UserStatus getUserStatus(UUID userId) {
        return findUserById(userId)
                .map(User::getStatus)
                .orElse(null);
    }

    @Override
    public boolean isUserActive(UUID userId) {
        return findUserById(userId)
                .map(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    public java.util.List<User> lockUsersForUpdate(java.util.List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return userRepository.findAllByIdInOrderByIdForUpdate(userIds);
    }

    @Override
    public org.springframework.data.domain.Page<User> findUsersByRole(com.fptu.exe.skillswap.shared.constant.RoleCode role, org.springframework.data.domain.Pageable pageable) {
        return userRepository.findUsersByRole(role, pageable);
    }

    @Override
    public Optional<StudentProfile> findStudentProfileWithDetailsByUserId(UUID userId) {
        return (userId == null || studentProfileRepository == null) ? Optional.empty() : studentProfileRepository.findWithDetailsByUserId(userId);
    }

    @Override
    public Optional<StudentProfile> findStudentProfileById(UUID userId) {
        return (userId == null || studentProfileRepository == null) ? Optional.empty() : studentProfileRepository.findById(userId);
    }

    @Override
    public List<StudentProfile> findStudentProfilesByUserIdIn(List<UUID> userIds) {
        return (userIds == null || userIds.isEmpty() || studentProfileRepository == null) ? List.of() : studentProfileRepository.findByUserIdIn(userIds);
    }

    @Override
    public List<User> findUsersByIdIn(java.util.Collection<UUID> userIds) {
        return (userIds == null || userIds.isEmpty() || userRepository == null) ? List.of() : userRepository.findAllById(userIds);
    }

    @Override
    public Optional<User> findAdminVisibleUserById(UUID userId) {
        if (userId == null || userRepository == null) {
            return Optional.empty();
        }
        return userRepository.findAdminVisibleUserById(
                userId,
                com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE,
                com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR,
                com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN,
                com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN
        );
    }
}
