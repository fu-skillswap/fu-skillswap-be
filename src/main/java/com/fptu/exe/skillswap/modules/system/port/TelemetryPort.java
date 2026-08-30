package com.fptu.exe.skillswap.modules.system.port;

import java.util.Map;
import java.util.UUID;

public interface TelemetryPort {
    void record(String eventType, UUID userId, String subjectType, UUID subjectId, Map<String, ?> metadata);
}
