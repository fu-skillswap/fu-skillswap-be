package com.fptu.exe.skillswap.modules.payment.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.payment.dto.response.ServicePricingPreviewResponse;
import com.fptu.exe.skillswap.modules.payment.service.BookingPricingPreviewService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mentor-services")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payment Orders", description = "Xem trước chi phí dịch vụ và các ưu đãi có thể áp dụng.")
public class PricingPreviewController {

    private final BookingPricingPreviewService pricingPreviewService;

    @GetMapping("/{serviceId}/pricing-preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem trước giá dịch vụ cá nhân hóa", description = "Trả về ước tính giá và ưu đãi có thể áp dụng cho người dùng hiện tại. Kết quả chỉ để tham khảo và không tạo giao dịch.")
    public ApiResponse<ServicePricingPreviewResponse> preview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(pricingPreviewService.previewDiscovery(principal.getPublicId(), serviceId));
    }
}
