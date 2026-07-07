package com.fptu.exe.skillswap.modules.system.repository;

import com.fptu.exe.skillswap.modules.system.domain.InternalTelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InternalTelemetryEventRepository extends JpaRepository<InternalTelemetryEvent, UUID> {
}
