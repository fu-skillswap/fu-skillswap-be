package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleCalendarConnectRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleCalendarStatusResponse;
import com.fptu.exe.skillswap.modules.identity.service.GoogleCalendarConnectionService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/google-calendar")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Google Calendar", description = "Nhóm API kết nối, kiểm tra trạng thái và ngắt kết nối Google Calendar để backend tự tạo Google Meet và đồng bộ lịch cho booking.")
public class GoogleCalendarController {

    private final GoogleCalendarConnectionService googleCalendarConnectionService;

    @GetMapping("/status")
    @Operation(summary = "Lấy trạng thái kết nối Google Calendar")
    public ApiResponse<GoogleCalendarStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.getStatus(principal.getPublicId()));
    }

    @PostMapping("/connect")
    @Operation(summary = "Kết nối Google Calendar bằng authorization code flow")
    public ApiResponse<GoogleCalendarStatusResponse> connect(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody GoogleCalendarConnectRequest request) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.connect(principal.getPublicId(), request));
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Ngắt kết nối Google Calendar hiện tại")
    public ApiResponse<GoogleCalendarStatusResponse> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.disconnect(principal.getPublicId()));
    }

    private void ensurePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
