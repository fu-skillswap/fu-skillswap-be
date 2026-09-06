package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionLog;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.repository.ForumActionLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import com.fptu.exe.skillswap.shared.util.AuditLogJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Persists safe forum action metadata in the same transaction as the action. */
@Service
@RequiredArgsConstructor
public class ForumActionLogService {

    private final ForumActionLogRepository forumActionLogRepository;
    private final UserQueryPort userQueryPort;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User actor, ForumActionType actionType, String targetType, UUID targetId,
                       Map<String, Object> metadata) {
        if (actor == null || actionType == null || targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("Forum action log requires actor, action and target type");
        }
        forumActionLogRepository.save(ForumActionLog.builder()
                .user(actor)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(AuditLogJsonUtil.toJson(metadata == null ? Map.of() : metadata))
                .build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorUserId, ForumActionType actionType, String targetType, UUID targetId,
                       Map<String, Object> metadata) {
        User actor = userQueryPort.findUserById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người thực hiện forum action"));
        record(actor, actionType, targetType, targetId, metadata);
    }
}
