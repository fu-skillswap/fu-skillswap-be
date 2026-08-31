package com.fptu.exe.skillswap.shared.event;

import java.util.Map;
import java.util.UUID;

/** Immutable request for the admin module to persist an operator audit record. */
public record OperatorAuditIntent(
        UUID actorUserId,
        String entityType,
        UUID entityId,
        String operatorEventType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue
) {
    public OperatorAuditIntent {
        oldValue = oldValue == null ? Map.of() : Map.copyOf(oldValue);
        newValue = newValue == null ? Map.of() : Map.copyOf(newValue);
    }
}
