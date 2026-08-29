package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserAcademicResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminUserSummaryAcademicProfileResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.event.UserStatusChangedEvent;
import com.fptu.exe.skillswap.modules.identity.port.UserAdminPort;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
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
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> getVisibleUsers(AdminUserListRequest request) {
        AdminUserListRequest safeRequest = request == null ? new AdminUserListRequest() : request;
        RoleCode targetRole = parseRoleFilter(safeRequest.getRole());
        UserStatus targetStatus = parseStatusFilter(safeRequest.getStatus());
        String keywordPattern = normalizeKeywordPattern(safeRequest.getKeyword());

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

        return PageResponse.<AdminUserListItemResponse>builder()
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
    public SystemUserResponse changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
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
        return SystemUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .academicProfile(buildAcademicResponse(profile))
                .build();
    }

    @Override
    @Transactional
    public AdminUserResponse grantAdminRole(UUID systemAdminId, String email) {
        User targetUser = findTargetUser(email);
        if (targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này đã có quyền admin");
        }

        User systemAdmin = findActor(systemAdminId);
        targetUser.getRoles().remove(RoleCode.MENTEE);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.ADMIN);
        userRepository.save(targetUser);

        return AdminUserResponse.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .avatarUrl(targetUser.getAvatarUrl())
                .status(targetUser.getStatus())
                .assignedBy(systemAdmin.getId())
                .assignedAt(DateTimeUtil.now())
                .build();
    }

    @Override
    @Transactional
    public AdminUserResponse revokeAdminRole(String email) {
        User targetUser = findTargetUser(email);
        if (!targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này hiện không có quyền admin");
        }

        targetUser.getRoles().remove(RoleCode.ADMIN);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.MENTEE);
        userRepository.save(targetUser);

        return AdminUserResponse.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .avatarUrl(targetUser.getAvatarUrl())
                .status(targetUser.getStatus())
                .assignedBy(null)
                .assignedAt(null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findUsersByRole(RoleCode.ADMIN, adminRolePageable(pageRequest));
        return PageResponse.<AdminUserResponse>builder()
                .content(page.getContent().stream().map(user -> AdminUserResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .status(user.getStatus())
                        .assignedBy(null)
                        .assignedAt(null)
                        .build()).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SystemUserResponse> getAllUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findAll(systemUserPageable(pageRequest));
        Map<UUID, List<RoleCode>> rolesByUserId = loadRolesByUserId(page.getContent());

        return PageResponse.<SystemUserResponse>builder()
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
    public AdminUserSummaryAcademicProfileResponse getAcademicProfileSummary(UUID userId) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(userId).orElse(null);
        if (profile == null) {
            return null;
        }
        return new AdminUserSummaryAcademicProfileResponse(
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
                notificationService.createNotification(
                        userId,
                        NotificationType.ACCOUNT_UNLOCKED,
                        "Tài khoản của bạn đã được mở lại",
                        "Bạn có thể đăng nhập và tiếp tục sử dụng SkillSwap bình thường.",
                        "USER",
                        userId
                );
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

    private Pageable buildPageable(AdminUserListRequest request) {
        int page = Math.max(request.getPage(), 0);
        int size = Math.min(Math.max(request.getSize(), 1), 100);
        Sort.Direction direction = request.resolveDirection();
        String sortBy = resolveSortBy(request.getSortBy());
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

    private AdminUserListItemResponse toAdminUserListItem(User user, StudentProfile profile) {
        List<RoleCode> visibleRoles = new ArrayList<>();
        if (user.getRoles() != null) {
            if (user.getRoles().contains(RoleCode.MENTEE)) {
                visibleRoles.add(RoleCode.MENTEE);
            }
            if (user.getRoles().contains(RoleCode.MENTOR)) {
                visibleRoles.add(RoleCode.MENTOR);
            }
        }

        return AdminUserListItemResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(visibleRoles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .academicProfile(buildAcademicResponse(profile))
                .build();
    }

    private AdminUserAcademicResponse buildAcademicResponse(StudentProfile profile) {
        if (profile == null) {
            return null;
        }
        return AdminUserAcademicResponse.builder()
                .claimedStudentCode(profile.getClaimedStudentCode())
                .build();
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

    private SystemUserResponse toSystemUserResponse(User user, List<RoleCode> roles) {
        return SystemUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
