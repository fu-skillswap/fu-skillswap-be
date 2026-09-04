package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.dto.request.GoogleCalendarConnectRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.GoogleAuthorizationContextResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/google-calendar")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Google Calendar", description = "Nhóm API kết nối, kiểm tra trạng thái và ngắt kết nối Google Calendar để backend tự tạo Google Meet và đồng bộ lịch cho booking.")
public class GoogleCalendarController {

    private final GoogleCalendarConnectionService googleCalendarConnectionService;

    @GetMapping("/status")
    @Operation(summary = "Xem trạng thái kết nối Google Calendar",
            description = "FE gọi khi mở phần cài đặt lịch. Kết quả cho biết người dùng đã kết nối hay chưa; API không yêu cầu người dùng đăng nhập lại với Google.")
    public ApiResponse<GoogleCalendarStatusResponse> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.getStatus(principal.getPublicId()));
    }

    @GetMapping("/authorization-context")
    @Operation(summary = "Chuẩn bị kết nối Google Calendar",
            description = "FE gọi trước khi mở màn hình cấp quyền Google. Backend trả context dùng một lần cho OAuth/PKCE; FE không tự tạo state hoặc lưu Google secret.")
    public ApiResponse<GoogleAuthorizationContextResponse> issueAuthorizationContext(
            @AuthenticationPrincipal UserPrincipal principal,
            @io.swagger.v3.oas.annotations.Parameter(description = "URL FE sẽ nhận kết quả OAuth", example = "https://app.skillswap.asia/settings/calendar/callback") @RequestParam String redirectUri,
            @io.swagger.v3.oas.annotations.Parameter(description = "PKCE code challenge do FE tạo theo chuẩn OAuth", example = "S256-example-code-challenge") @RequestParam String codeChallenge
    ) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.issueAuthorizationContext(
                principal.getPublicId(),
                redirectUri,
                codeChallenge
        ));
    }

    @PostMapping("/connect")
    @Operation(summary = "Hoàn tất kết nối Google Calendar",
            description = "FE gửi authorization code nhận được từ Google cùng context tương ứng. Lỗi 400 thường có nghĩa code hết hạn, redirect URL không khớp hoặc context đã dùng.")
    public ApiResponse<GoogleCalendarStatusResponse> connect(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody GoogleCalendarConnectRequest request) {
        ensurePrincipal(principal);
        return ApiResponse.success(googleCalendarConnectionService.connect(principal.getPublicId(), request));
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Ngắt kết nối Google Calendar",
            description = "Xóa kết nối hiện tại. Không thể ngắt khi mentor còn service đang hoạt động hoặc còn booking đã thanh toán trong tương lai.")
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
