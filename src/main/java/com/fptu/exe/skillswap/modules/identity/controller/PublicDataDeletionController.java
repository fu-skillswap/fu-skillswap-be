package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.TrustedClientIpResolver;
import com.fptu.exe.skillswap.modules.identity.dto.request.DataDeletionPublicRequest;
import com.fptu.exe.skillswap.modules.identity.service.IdentityService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/public/data-deletion-requests")
@RequiredArgsConstructor
@Tag(name = "Data Deletion Public", description = "Tiếp nhận yêu cầu xóa tài khoản và dữ liệu từ bên ngoài ứng dụng theo chuẩn Google Play Data Safety.")
public class PublicDataDeletionController {

    private final IdentityService identityService;
    private final InMemoryRateLimitService rateLimitService;
    private final TrustedClientIpResolver trustedClientIpResolver;

    @Operation(
            summary = "Gửi yêu cầu xóa dữ liệu cá nhân",
            description = "Dùng cho trang web công khai data-deletion.html để người dùng không mở app vẫn có thể gửi yêu cầu xóa tài khoản và dữ liệu."
    )
    @PostMapping
    public ApiResponse<String> submitDataDeletionRequest(
            @Valid @RequestBody DataDeletionPublicRequest request,
            HttpServletRequest httpServletRequest
    ) {
        String clientIp = resolveClientIp(httpServletRequest);
        rateLimitService.check(
                RateLimitScope.SECURITY,
                "public:data-deletion:" + clientIp,
                10,
                Duration.ofHours(1),
                "Bạn đã gửi quá nhiều yêu cầu, vui lòng thử lại sau 1 giờ"
        );

        identityService.submitDataDeletionRequest(request.email(), request.reason(), clientIp);
        return ApiResponse.success("Yêu cầu xóa dữ liệu của bạn đã được ghi nhận. Ban quản trị sẽ tiến hành xử lý trong vòng 24 - 48 giờ làm việc.");
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return trustedClientIpResolver.resolve(request);
    }
}
