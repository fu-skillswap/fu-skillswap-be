package com.fptu.exe.skillswap.modules.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationArchiveManifestRepository extends JpaRepository<NotificationArchiveManifest, UUID> {
}
