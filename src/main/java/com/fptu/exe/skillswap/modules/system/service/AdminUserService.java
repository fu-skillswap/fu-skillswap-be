package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserSessionRepository;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        // Save Audit Log
        AuditLog auditLog = AuditLog.builder()
                .actor(adminUser)
                .action(AuditAction.UPDATE)
                .entityType("USER")
                .entityId(userId)
                .oldValue(String.format("{\"status\":\"%s\"}", oldStatus))
                .newValue(String.format("{\"status\":\"%s\",\"reason\":\"%s\"}", user.getStatus(), reason == null ? "" : reason))
                .build();
        auditLogRepository.save(auditLog);

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
}
