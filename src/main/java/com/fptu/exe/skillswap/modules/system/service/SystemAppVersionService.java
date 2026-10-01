package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.system.domain.SystemAppVersion;
import com.fptu.exe.skillswap.modules.system.dto.response.AppVersionResponse;
import com.fptu.exe.skillswap.modules.system.repository.SystemAppVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAppVersionService {

    private static final String DEFAULT_PLATFORM = "android";
    private static final String DEFAULT_ANDROID_URL = "https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap";
    private static final String DEFAULT_IOS_URL = "https://apps.apple.com/app/id000000000";

    private final SystemAppVersionRepository repository;

    @Transactional(readOnly = true)
    public AppVersionResponse getAppVersion(String platform) {
        String normalizedPlatform = normalizePlatform(platform);
        try {
            return repository.findByPlatformIgnoreCase(normalizedPlatform)
                    .map(this::toResponse)
                    .orElseGet(() -> buildDefaultResponse(normalizedPlatform));
        } catch (Exception ex) {
            log.error("Lỗi khi truy vấn cấu hình phiên bản từ database cho platform {}: {}", normalizedPlatform, ex.getMessage());
            return buildDefaultResponse(normalizedPlatform);
        }
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return DEFAULT_PLATFORM;
        }
        String trimmed = platform.trim().toLowerCase();
        if ("ios".equals(trimmed)) {
            return "ios";
        }
        return DEFAULT_PLATFORM;
    }

    private AppVersionResponse toResponse(SystemAppVersion entity) {
        return new AppVersionResponse(
                entity.getPlatform(),
                entity.getMinVersionCode(),
                entity.getLatestVersionCode(),
                entity.isMaintenance(),
                entity.getUpdateUrl(),
                entity.getMaintenanceMessage()
        );
    }

    private AppVersionResponse buildDefaultResponse(String platform) {
        boolean isIos = "ios".equalsIgnoreCase(platform);
        return new AppVersionResponse(
                platform,
                1,
                1,
                false,
                isIos ? DEFAULT_IOS_URL : DEFAULT_ANDROID_URL,
                null
        );
    }
}
