package com.fptu.exe.skillswap.modules.admin.port;

import java.util.Map;
import java.util.UUID;

public interface AuditLogPort {
    void writeOperatorEvent(
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String operatorEventType,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    );
}
