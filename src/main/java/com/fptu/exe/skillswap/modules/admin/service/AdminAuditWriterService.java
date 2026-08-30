package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.AuditLogJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditWriterService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public void writeOperatorEvent(
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String operatorEventType,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người quản trị"));

        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .action(AuditAction.UPDATE)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(toAuditJson(operatorEventType, oldValue))
                .newValue(toAuditJson(operatorEventType, newValue))
                .build());
    }

    private String toAuditJson(String operatorEventType, Map<String, Object> payload) {
        if (payload == null && operatorEventType == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (payload != null) {
            normalized.putAll(payload);
        }
        if (operatorEventType != null) {
            normalized.put("operatorEventType", operatorEventType);
        }
        return AuditLogJsonUtil.toJson(normalized);
    }
}
