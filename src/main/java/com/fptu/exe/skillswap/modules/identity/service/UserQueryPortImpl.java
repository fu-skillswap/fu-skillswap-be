package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.port.StudentProfileRecord;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.identity.port.PublicUserQueryPort;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
public class UserQueryPortImpl implements UserQueryPort, UserLockPort, PublicUserQueryPort {

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
    public List<User> findUsersByRole(com.fptu.exe.skillswap.shared.constant.RoleCode role) {
        return userRepository.findUsersByRole(role, org.springframework.data.domain.Pageable.unpaged()).getContent();
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

    @Override
    public Optional<UserSummaryRecord> findUserSummaryById(UUID userId) {
        return findUserById(userId).map(this::toUserSummaryRecord);
    }

    @Override
    public Optional<StudentProfileRecord> findStudentProfileRecordByUserId(UUID userId) {
        return findStudentProfileWithDetailsByUserId(userId).map(this::toStudentProfileRecord);
    }

    @Override
    public Map<UUID, UserSummaryRecord> findUserSummariesByIdIn(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::toUserSummaryRecord));
    }

    @Override
    public Map<UUID, StudentProfileRecord> findStudentProfileRecordsByIdIn(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty() || studentProfileRepository == null) {
            return Map.of();
        }
        return studentProfileRepository.findByUserIdIn(List.copyOf(userIds)).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, this::toStudentProfileRecord));
    }

    @Override
    @Transactional
    public void grantMentorRole(UUID userId) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(user -> {
            user.getRoles().remove(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTEE);
            user.getRoles().add(com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR);
            userRepository.save(user);
        });
    }

    private UserSummaryRecord toUserSummaryRecord(User user) {
        return new UserSummaryRecord(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRoles(),
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getStatus() == com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
        );
    }

    private StudentProfileRecord toStudentProfileRecord(StudentProfile profile) {
        return new StudentProfileRecord(
                profile.getUserId(),
                profile.getClaimedStudentCode(),
                profile.getCampus() != null ? profile.getCampus().getId() : null,
                profile.getCampus() != null ? profile.getCampus().getName() : null,
                profile.getProgram() != null ? profile.getProgram().getId() : null,
                profile.getProgram() != null ? profile.getProgram().getCode() : null,
                profile.getProgram() != null ? profile.getProgram().getNameVi() : null,
                profile.getSpecialization() != null ? profile.getSpecialization().getId() : null,
                profile.getSpecialization() != null ? profile.getSpecialization().getCode() : null,
                profile.getSpecialization() != null ? profile.getSpecialization().getNameVi() : null,
                profile.getSemester(),
                profile.getIntakeYear(),
                profile.isAlumni(),
                profile.getBio()
        );
    }
}
