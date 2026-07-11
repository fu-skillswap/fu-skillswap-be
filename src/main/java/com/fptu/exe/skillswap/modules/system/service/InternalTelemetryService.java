package com.fptu.exe.skillswap.modules.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.system.domain.InternalTelemetryEvent;
import com.fptu.exe.skillswap.modules.system.repository.InternalTelemetryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalTelemetryService {

    private final InternalTelemetryEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(String eventType, UUID userId, String subjectType, UUID subjectId, Map<String, ?> metadata) {
        try {
            repository.save(InternalTelemetryEvent.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .subjectType(subjectType)
                    .subjectId(subjectId)
                    .metadataJson(toJson(metadata))
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Failed to record telemetry event {}: {}", eventType, ex.getMessage());
        }
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize telemetry metadata: {}", ex.getMessage());
            return null;
        }
    }
}
