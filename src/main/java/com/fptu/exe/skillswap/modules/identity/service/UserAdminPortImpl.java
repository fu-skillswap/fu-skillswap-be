package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.event.UserStatusChangedEvent;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.port.AdminUserReference;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AcademicProfileSummary;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AdminUserListQuery;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.AdminUserView;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.SystemUserView;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.UserAcademicProfile;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.UserListItem;
import com.fptu.exe.skillswap.modules.identity.port.IdentityAdminPortModels.VisibleUserSummary;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminPortImpl implements UserAdminPort {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "lastLoginAt", "fullName", "email", "status");

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NotificationCommandPort notificationCommandPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public AdminUserReference requireAdminReference(UUID userId) {
        return findReference(userId).orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người quản trị"));
    }

    @Override
    public java.util.Optional<AdminUserReference> findReference(UUID userId) {
        return userId == null ? java.util.Optional.empty() : userRepository.findById(userId)
                .map(user -> new AdminUserReference(user.getId(), user.getFullName()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserListItem> getVisibleUsers(AdminUserListQuery request) {
        AdminUserListQuery safeRequest = request == null
                ? new AdminUserListQuery(null, null, null, 0, 10, "createdAt", "DESC")
                : request;
        RoleCode targetRole = parseRoleFilter(safeRequest.role());
        UserStatus targetStatus = parseStatusFilter(safeRequest.status());
        String keywordPattern = normalizeKeywordPattern(safeRequest.keyword());

        Page<User> page = userRepository.searchAdminVisibleUsers(
                keywordPattern,
                targetStatus,
                targetRole,
                RoleCode.MENTEE,
                RoleCode.MENTOR,
                RoleCode.ADMIN,
                RoleCode.SYSTEM_ADMIN,
                buildPageable(safeRequest)
        );

        List<User> users = page.getContent();
        List<UUID> userIds = users.stream().map(User::getId).toList();
        List<StudentProfile> profiles = studentProfileRepository.findByUserIdIn(userIds);
        Map<UUID, StudentProfile> profileMap = profiles.stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, p -> p));

        return PageResponse.<UserListItem>builder()
                .content(users.stream().map(user -> toAdminUserListItem(user, profileMap.get(user.getId()))).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public SystemUserView changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã người dùng không hợp lệ");
        }

        if (userId.equals(adminId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Không thể tự khóa tài khoản của chính mình");
        }

        userRepository.findById(adminId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người quản trị thực hiện hành động"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));

        List<RoleCode> roles = userRepository.findRoleCodesByUserId(userId);
        UserStatus oldStatus = user.getStatus();

        if (ban) {
            if (roles.contains(RoleCode.SYSTEM_ADMIN) || roles.contains(RoleCode.ADMIN)) {
                throw new BaseException(ErrorCode.ACCESS_DENIED, "Không thể khóa tài khoản của System Admin hoặc Admin khác");
            }
            user.setStatus(UserStatus.BANNED);

            List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsRevokedFalse(userId);
            for (UserSession session : activeSessions) {
                session.setRevoked(true);
            }
            userSessionRepository.saveAll(activeSessions);
        } else {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
        if (ban) {
            eventPublisher.publishEvent(new UserBannedEvent(userId));
        }
        eventPublisher.publishEvent(new UserStatusChangedEvent(userId, oldStatus, user.getStatus()));
        if (!ban && oldStatus == UserStatus.BANNED) {
            notifyAccountUnlockedSafely(userId);
        }

        StudentProfile profile = studentProfileRepository.findById(userId).orElse(null);
        return new SystemUserView(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
                user.getStatus().name(), roleNames(roles), user.getLastLoginAt(), user.getCreatedAt(),
                buildAcademicResponse(profile));
    }

    @Override
    @Transactional
    public AdminUserView grantAdminRole(UUID systemAdminId, String email) {
        User targetUser = findTargetUser(email);
        if (targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này đã có quyền admin");
        }

        User systemAdmin = findActor(systemAdminId);
        targetUser.getRoles().remove(RoleCode.MENTEE);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.ADMIN);
        userRepository.save(targetUser);

        return new AdminUserView(targetUser.getId(), targetUser.getEmail(), targetUser.getFullName(),
                targetUser.getAvatarUrl(), targetUser.getStatus().name(), systemAdmin.getId(), DateTimeUtil.now());
    }

    @Override
    @Transactional
    public AdminUserView revokeAdminRole(String email) {
        User targetUser = findTargetUser(email);
        if (!targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này hiện không có quyền admin");
        }

        targetUser.getRoles().remove(RoleCode.ADMIN);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.MENTEE);
        userRepository.save(targetUser);

        return new AdminUserView(targetUser.getId(), targetUser.getEmail(), targetUser.getFullName(),
                targetUser.getAvatarUrl(), targetUser.getStatus().name(), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> getAdminUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findUsersByRole(RoleCode.ADMIN, adminRolePageable(pageRequest));
        return PageResponse.<AdminUserView>builder()
                .content(page.getContent().stream().map(user -> new AdminUserView(user.getId(), user.getEmail(),
                        user.getFullName(), user.getAvatarUrl(), user.getStatus().name(), null, null)).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SystemUserView> getAllUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findAll(systemUserPageable(pageRequest));
        Map<UUID, List<RoleCode>> rolesByUserId = loadRolesByUserId(page.getContent());

        return PageResponse.<SystemUserView>builder()
                .content(page.getContent().stream()
                        .map(user -> toSystemUserResponse(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<VisibleUserSummary> findVisibleUserSummary(UUID userId) {
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findAdminVisibleUserById(userId, RoleCode.MENTEE, RoleCode.MENTOR,
                        RoleCode.ADMIN, RoleCode.SYSTEM_ADMIN)
                .map(user -> new VisibleUserSummary(user.getId(), user.getEmail(), user.getFullName(),
                        user.getAvatarUrl(), user.getStatus().name(), roleNames(user.getRoles()),
                        user.getLastLoginAt(), user.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public AcademicProfileSummary getAcademicProfileSummary(UUID userId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(userId).orElse(null);
        if (profile == null) {
            return null;
        }
        return new AcademicProfileSummary(
                profile.getClaimedStudentCode(),
                profile.getCampus() == null || profile.getCampus().getCode() == null ? null : profile.getCampus().getCode().name(),
                profile.getCampus() == null ? null : profile.getCampus().getName(),
                profile.getProgram() == null ? null : profile.getProgram().getCode(),
                profile.getProgram() == null ? null : profile.getProgram().getNameVi(),
                profile.getSpecialization() == null ? null : profile.getSpecialization().getCode(),
                profile.getSpecialization() == null ? null : profile.getSpecialization().getNameVi(),
                profile.getSemester(),
                profile.isAlumni()
        );
    }

    private void notifyAccountUnlockedSafely(UUID userId) {
        Runnable notificationTask = () -> {
            try {
                notificationCommandPort.publish(new NotificationCommandPort.NotificationIntent(
                        userId,
                        NotificationType.ACCOUNT_UNLOCKED.name(),
                        "Tài khoản của bạn đã được mở lại",
                        "Bạn có thể đăng nhập và tiếp tục sử dụng SkillSwap bình thường.",
                        "USER",
                        userId,
                        null
                ));
            } catch (Exception ex) {
                log.warn("Failed to create account unlocked notification for user {}", userId, ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationTask.run();
                }
            });
            return;
        }

        notificationTask.run();
    }

    private Pageable buildPageable(AdminUserListQuery request) {
        int page = Math.max(request.page(), 0);
        int size = Math.min(Math.max(request.size(), 1), 100);
        Sort.Direction direction = resolveDirection(request.direction());
        String sortBy = resolveSortBy(request.sortBy());
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable adminRolePageable(BasePageRequest request) {
        BasePageRequest safeRequest = request == null ? new BasePageRequest() : request;
        int page = Math.max(safeRequest.getPage(), 0);
        int size = Math.min(Math.max(safeRequest.getSize(), 1), 100);
        Sort.Direction direction = safeRequest.resolveDirection();
        String sortBy = switch (safeRequest.getSortBy() == null ? "" : safeRequest.getSortBy()) {
            case "email" -> "email";
            case "fullName" -> "fullName";
            default -> "createdAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable systemUserPageable(BasePageRequest request) {
        BasePageRequest safeRequest = request == null ? new BasePageRequest() : request;
        int page = Math.max(safeRequest.getPage(), 0);
        int size = Math.min(Math.max(safeRequest.getSize(), 1), 100);
        Sort.Direction direction = safeRequest.resolveDirection();
        String sortBy = switch (safeRequest.getSortBy() == null ? "" : safeRequest.getSortBy()) {
            case "email" -> "email";
            case "fullName" -> "fullName";
            case "lastLoginAt" -> "lastLoginAt";
            default -> "createdAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private String resolveSortBy(String sortBy) {
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            return "createdAt";
        }
        return sortBy;
    }

    private Sort.Direction resolveDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.valueOf(direction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Sort.Direction.DESC;
        }
    }

    private String normalizeKeywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private RoleCode parseRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            RoleCode roleCode = RoleCode.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (roleCode != RoleCode.MENTEE && roleCode != RoleCode.MENTOR) {
                throw new BaseException(ErrorCode.BAD_REQUEST, "role chỉ chấp nhận MENTEE hoặc MENTOR");
            }
            return roleCode;
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "role chỉ chấp nhận MENTEE hoặc MENTOR");
        }
    }

    private UserStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "status không hợp lệ");
        }
    }

    private UserListItem toAdminUserListItem(User user, StudentProfile profile) {
        List<RoleCode> visibleRoles = new ArrayList<>();
        if (user.getRoles() != null) {
            if (user.getRoles().contains(RoleCode.MENTEE)) {
                visibleRoles.add(RoleCode.MENTEE);
            }
            if (user.getRoles().contains(RoleCode.MENTOR)) {
                visibleRoles.add(RoleCode.MENTOR);
            }
        }

        return new UserListItem(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
                user.getStatus().name(), roleNames(visibleRoles), user.getLastLoginAt(), user.getCreatedAt(),
                buildAcademicResponse(profile));
    }

    private UserAcademicProfile buildAcademicResponse(StudentProfile profile) {
        if (profile == null) {
            return null;
        }
        return new UserAcademicProfile(profile.getClaimedStudentCode());
    }

    private User findTargetUser(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Email không được để trống");
        }
        return userRepository.findActiveByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với email này"));
    }

    private User findActor(UUID systemAdminId) {
        if (systemAdminId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userRepository.findById(systemAdminId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản system admin"));
    }

    private Map<UUID, List<RoleCode>> loadRolesByUserId(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<RoleCode>> rolesByUserId = new HashMap<>();
        for (User user : users) {
            rolesByUserId.put(user.getId(), new ArrayList<>(user.getRoles()));
        }
        return rolesByUserId;
    }

    private SystemUserView toSystemUserResponse(User user, List<RoleCode> roles) {
        return new SystemUserView(user.getId(), user.getEmail(), user.getFullName(), user.getAvatarUrl(),
                user.getStatus().name(), roleNames(roles), user.getLastLoginAt(), user.getCreatedAt(), null);
    }

    private List<String> roleNames(java.util.Collection<RoleCode> roles) {
        return roles == null ? List.of() : roles.stream()
                .sorted(java.util.Comparator.comparingInt(RoleCode::ordinal))
                .map(RoleCode::name)
                .toList();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
