package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.system.domain.SystemAppVersion;
import com.fptu.exe.skillswap.modules.system.dto.response.AppVersionResponse;
import com.fptu.exe.skillswap.modules.system.repository.SystemAppVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAppVersionServiceTest {

    @Mock
    private SystemAppVersionRepository repository;

    @InjectMocks
    private SystemAppVersionService service;

    @Test
    @DisplayName("Returns version config when record exists in database")
    void getAppVersion_foundInDatabase_returnsMappedResponse() {
        SystemAppVersion entity = SystemAppVersion.builder()
                .id(1L)
                .platform("android")
                .minVersionCode(2)
                .latestVersionCode(5)
                .isMaintenance(false)
                .updateUrl("https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap")
                .maintenanceMessage(null)
                .build();

        when(repository.findByPlatformIgnoreCase("android")).thenReturn(Optional.of(entity));

        AppVersionResponse response = service.getAppVersion("android");

        assertNotNull(response);
        assertEquals("android", response.platform());
        assertEquals(2, response.minVersionCode());
        assertEquals(5, response.latestVersionCode());
        assertFalse(response.isMaintenance());
        assertEquals("https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap", response.updateUrl());
    }

    @Test
    @DisplayName("Returns maintenance config when isMaintenance is true")
    void getAppVersion_maintenanceActive_returnsMaintenanceMessage() {
        SystemAppVersion entity = SystemAppVersion.builder()
                .id(1L)
                .platform("android")
                .minVersionCode(1)
                .latestVersionCode(1)
                .isMaintenance(true)
                .updateUrl("https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap")
                .maintenanceMessage("Hệ thống đang bảo trì máy chủ đến 14h00")
                .build();

        when(repository.findByPlatformIgnoreCase("android")).thenReturn(Optional.of(entity));

        AppVersionResponse response = service.getAppVersion("android");

        assertNotNull(response);
        assertTrue(response.isMaintenance());
        assertEquals("Hệ thống đang bảo trì máy chủ đến 14h00", response.maintenanceMessage());
    }

    @Test
    @DisplayName("Returns safe default fallback when record not found in database")
    void getAppVersion_notFoundInDatabase_returnsDefaultFallback() {
        when(repository.findByPlatformIgnoreCase("android")).thenReturn(Optional.empty());

        AppVersionResponse response = service.getAppVersion("android");

        assertNotNull(response);
        assertEquals("android", response.platform());
        assertEquals(1, response.minVersionCode());
        assertEquals(1, response.latestVersionCode());
        assertFalse(response.isMaintenance());
        assertNotNull(response.updateUrl());
    }

    @Test
    @DisplayName("Returns safe default fallback when database throws unexpected exception")
    void getAppVersion_databaseThrowsException_returnsDefaultFallback() {
        when(repository.findByPlatformIgnoreCase("android")).thenThrow(new RuntimeException("DB Connection Timeout"));

        AppVersionResponse response = service.getAppVersion("android");

        assertNotNull(response);
        assertEquals("android", response.platform());
        assertEquals(1, response.minVersionCode());
        assertEquals(1, response.latestVersionCode());
        assertFalse(response.isMaintenance());
    }

    @Test
    @DisplayName("Normalizes platform input and supports iOS")
    void getAppVersion_ios_normalizesAndQueries() {
        SystemAppVersion entity = SystemAppVersion.builder()
                .id(2L)
                .platform("ios")
                .minVersionCode(1)
                .latestVersionCode(3)
                .isMaintenance(false)
                .updateUrl("https://apps.apple.com/app/id000000000")
                .build();

        when(repository.findByPlatformIgnoreCase("ios")).thenReturn(Optional.of(entity));

        AppVersionResponse response = service.getAppVersion(" IOS ");

        assertNotNull(response);
        assertEquals("ios", response.platform());
        assertEquals(3, response.latestVersionCode());
    }
}
