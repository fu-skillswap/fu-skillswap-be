package com.fptu.exe.skillswap.modules.system.controller;

import com.fptu.exe.skillswap.modules.system.dto.response.AppVersionResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemAppVersionService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAppVersionControllerTest {

    @Mock
    private SystemAppVersionService service;

    @InjectMocks
    private SystemAppVersionController controller;

    @Test
    @DisplayName("getAppVersion delegates to service with platform")
    void getAppVersion_returnsServiceResult() {
        AppVersionResponse expectedResponse = new AppVersionResponse(
                "android",
                1,
                2,
                false,
                "https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap",
                null
        );

        when(service.getAppVersion("android")).thenReturn(expectedResponse);

        ApiResponse<AppVersionResponse> apiResponse = controller.getAppVersion("android");

        assertNotNull(apiResponse);
        assertEquals(200, apiResponse.getStatus());
        assertEquals(expectedResponse, apiResponse.getData());
        assertEquals(1, apiResponse.getData().minVersionCode());
        assertEquals(2, apiResponse.getData().latestVersionCode());
        assertFalse(apiResponse.getData().isMaintenance());
        verify(service).getAppVersion("android");
    }
}
