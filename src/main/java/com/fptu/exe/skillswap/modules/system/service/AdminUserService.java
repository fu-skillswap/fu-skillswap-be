package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.system.dto.request.AdminUserListRequest;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserListItemResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserAcademicResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.AuditLogJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "lastLoginAt", "fullName", "email", "status");

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StudentProfileRepository studentProfileRepository;
    private final NotificationService notificationService;

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
        
        // Bulk fetch profiles for admin display
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

    @Transactional
    public SystemUserResponse changeUserStatus(UUID adminId, UUID userId, boolean ban, String reason) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Mã người dùng không hợp lệ");
        }

        if (userId.equals(adminId)) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "Không thể tự khóa tài khoản của chính mình");
        }

        User adminUser = userRepository.findById(adminId)
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

            // Revoke all active sessions
            List<UserSession> activeSessions = userSessionRepository.findByUserIdAndIsRevokedFalse(userId);
            for (UserSession session : activeSessions) {
                session.setRevoked(true);
            }
            userSessionRepository.saveAll(activeSessions);
            eventPublisher.publishEvent(new UserBannedEvent(userId));
        } else {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
        if (!ban && oldStatus == UserStatus.BANNED) {
            notifyAccountUnlockedSafely(userId);
        }

        // Save Audit Log
        AuditLog auditLog = AuditLog.builder()
                .actor(adminUser)
                .action(AuditAction.UPDATE)
                .entityType("USER")
                .entityId(userId)
                .oldValue(AuditLogJsonUtil.toJson(Map.of("status", oldStatus.name())))
                .newValue(AuditLogJsonUtil.toJson(Map.of(
                        "status", user.getStatus().name(),
                        "reason", reason == null ? "" : reason
                )))
                .build();
        auditLogRepository.save(auditLog);

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
}
