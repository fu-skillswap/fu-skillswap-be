package com.fptu.exe.skillswap.modules.system.integration;

import com.fptu.exe.skillswap.modules.system.domain.SystemAppVersion;
import com.fptu.exe.skillswap.modules.system.repository.SystemAppVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemAppVersionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemAppVersionRepository repository;

    @Test
    @DisplayName("GET /api/system/app-version is public and returns default android version config")
    void getAppVersion_publicEndpoint_returns200WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/system/app-version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.platform", is("android")))
                .andExpect(jsonPath("$.data.minVersionCode", is(1)))
                .andExpect(jsonPath("$.data.latestVersionCode", is(1)))
                .andExpect(jsonPath("$.data.isMaintenance", is(false)));
    }

    @Test
    @DisplayName("GET /api/system/app-version?platform=ios returns ios version config")
    void getAppVersion_ios_returnsIosPlatform() throws Exception {
        mockMvc.perform(get("/api/system/app-version").param("platform", "ios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.platform", is("ios")))
                .andExpect(jsonPath("$.data.minVersionCode", is(1)))
                .andExpect(jsonPath("$.data.latestVersionCode", is(1)))
                .andExpect(jsonPath("$.data.isMaintenance", is(false)));
    }

    @Test
    @DisplayName("GET /api/system/app-version reflects updated values in database")
    void getAppVersion_whenUpdatedInDatabase_returnsUpdatedValues() throws Exception {
        SystemAppVersion version = repository.findByPlatformIgnoreCase("android")
                .orElseGet(() -> SystemAppVersion.builder().platform("android").build());

        version.setMinVersionCode(2);
        version.setLatestVersionCode(4);
        version.setMaintenance(true);
        version.setMaintenanceMessage("Hệ thống bảo trì nâng cấp");
        repository.saveAndFlush(version);

        mockMvc.perform(get("/api/system/app-version").param("platform", "android"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(200)))
                .andExpect(jsonPath("$.data.platform", is("android")))
                .andExpect(jsonPath("$.data.minVersionCode", is(2)))
                .andExpect(jsonPath("$.data.latestVersionCode", is(4)))
                .andExpect(jsonPath("$.data.isMaintenance", is(true)))
                .andExpect(jsonPath("$.data.maintenanceMessage", is("Hệ thống bảo trì nâng cấp")));
    }
}
