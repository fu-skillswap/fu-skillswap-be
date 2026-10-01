package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.modules.identity.dto.response.AppPlatformConfigResponse;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/app-config")
@Tag(name = "App Configuration", description = "Cấu hình tính năng động (Feature Flags) cho các nền tảng client Web và Mobile theo quy định của Google Play Store.")
public class AppConfigController {

    @Operation(
            summary = "Lấy cấu hình tính năng theo nền tảng",
            description = "Trả về cờ tính năng nạp tiền, thanh toán và mô hình Consumption-Only cho client Flutter hoặc Web."
    )
    @GetMapping
    public ApiResponse<AppPlatformConfigResponse> getAppConfig(
            @RequestParam(defaultValue = "android") String platform
    ) {
        String normalizedPlatform = platform == null ? "android" : platform.trim().toLowerCase();
        boolean isMobile = "android".equals(normalizedPlatform) || "ios".equals(normalizedPlatform);

        AppPlatformConfigResponse config = new AppPlatformConfigResponse(
                normalizedPlatform,
                !isMobile, // topupEnabled: false on mobile, true on web
                !isMobile, // directPaymentEnabled: false on mobile, true on web
                isMobile,  // consumptionOnly: true on mobile, false on web
                isMobile
                        ? "Phiên bản ứng dụng di động chỉ hỗ trợ sử dụng số dư tài khoản có sẵn để đặt lịch hẹn."
                        : null
        );

        return ApiResponse.success(config);
    }
}
