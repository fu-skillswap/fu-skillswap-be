package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminEmailOutboxListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxDetailResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminEmailOutboxItemResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminEmailOutboxService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/email-outbox")
@RequiredArgsConstructor
@Tag(name = "Admin - Email Outbox", description = "Nhóm API để admin xem email outbox nội bộ, chẩn đoán delivery issue và retry lại các email đang FAILED.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminEmailOutboxController {

    private final AdminEmailOutboxService adminEmailOutboxService;

    @Operation(
            summary = "Lấy danh sách email outbox",
            description = "Trả về danh sách email outbox có thể lọc theo status, templateCode, toEmail và createdAt range. FE admin dùng endpoint này để giám sát backlog email và điều hướng sang retry khi cần."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy email outbox thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminEmailOutboxItemResponse>> getEmailOutbox(
            @ParameterObject @ModelAttribute AdminEmailOutboxListRequest request
    ) {
        return ApiResponse.success(adminEmailOutboxService.getEmailOutbox(request));
    }

    @Operation(
            summary = "Lấy chi tiết một email outbox",
            description = "Trả về body và lastError đầy đủ của email outbox để admin chẩn đoán vấn đề delivery."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết email outbox thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy email outbox")
    })
    @GetMapping("/{emailOutboxId}")
    public ApiResponse<AdminEmailOutboxDetailResponse> getEmailOutboxDetail(@PathVariable UUID emailOutboxId) {
        return ApiResponse.success(adminEmailOutboxService.getEmailOutboxDetail(emailOutboxId));
    }

    @Operation(
            summary = "Retry một email outbox bị lỗi",
            description = "Chỉ hỗ trợ retry email đang FAILED. Backend reset cùng row hiện tại về PENDING, tăng retryCount và xóa lastError/sentAt để worker mail có thể xử lý lại."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Retry email thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy email outbox"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email không ở trạng thái cho phép retry")
    })
    @PostMapping("/{emailOutboxId}/retry")
    public ApiResponse<AdminEmailOutboxDetailResponse> retryEmailOutbox(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID emailOutboxId
    ) {
        return ApiResponse.success(adminEmailOutboxService.retry(emailOutboxId, principal.getPublicId()));
    }
}
