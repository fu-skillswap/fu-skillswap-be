package com.fptu.exe.skillswap.modules.system.controller;

import com.fptu.exe.skillswap.modules.system.dto.response.AppVersionResponse;
import com.fptu.exe.skillswap.modules.system.service.SystemAppVersionService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/app-version")
@RequiredArgsConstructor
@Tag(name = "System App Version", description = "Kiểm soát phiên bản ứng dụng di động (Version Guard) và cờ bảo trì hệ thống.")
public class SystemAppVersionController {

    private final SystemAppVersionService service;

    @Operation(
            summary = "Kiểm tra phiên bản ứng dụng và trạng thái bảo trì",
            description = "Endpoint công khai (không cần login). Trả về minVersionCode để ép người dùng cập nhật (Force Update), latestVersionCode để gợi ý cập nhật, và isMaintenance để thông báo bảo trì hệ thống."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy thông tin phiên bản thành công",
                    content = @Content(examples = @ExampleObject(
                            name = "Phiên bản bình thường",
                            value = """
                                    {
                                      "timestamp": "2026-10-01T12:00:00Z",
                                      "status": 200,
                                      "code": "SUCCESS_0200",
                                      "message": "Thành công",
                                      "data": {
                                        "platform": "android",
                                        "minVersionCode": 1,
                                        "latestVersionCode": 1,
                                        "isMaintenance": false,
                                        "updateUrl": "https://play.google.com/store/apps/details?id=com.fptu.exe.skillswap",
                                        "maintenanceMessage": null
                                      }
                                    }
                                    """
                    ))
            )
    })
    @GetMapping
    public ApiResponse<AppVersionResponse> getAppVersion(
            @Parameter(description = "Nền tảng thiết bị (android hoặc ios). Mặc định là android.", example = "android")
            @RequestParam(defaultValue = "android", required = false) String platform
    ) {
        return ApiResponse.success(service.getAppVersion(platform));
    }
}
