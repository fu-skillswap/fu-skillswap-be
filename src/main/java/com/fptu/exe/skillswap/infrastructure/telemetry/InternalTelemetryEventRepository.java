package com.fptu.exe.skillswap.infrastructure.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternalTelemetryEventRepository extends JpaRepository<InternalTelemetryEvent, UUID> {
}
