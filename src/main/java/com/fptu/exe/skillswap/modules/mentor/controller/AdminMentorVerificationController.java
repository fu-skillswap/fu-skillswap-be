package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.AdminMentorVerificationReviewRequest;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mentor-verification/requests")
@RequiredArgsConstructor
@Tag(name = "Admin Mentor Verification", description = "API duyệt hồ sơ xác thực mentor dành cho admin")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorVerificationController {

    private final AdminMentorVerificationService adminMentorVerificationService;

    @Operation(summary = "Xem danh sách hồ sơ mentor với filter/search tối ưu cho admin queue")
    @GetMapping
    public ApiResponse<PageResponse<AdminMentorVerificationQueueItemResponse>> getQueue(
            @ParameterObject @ModelAttribute AdminMentorVerificationQueueFilterRequest filterRequest
    ) {
        return ApiResponse.success(adminMentorVerificationService.getQueue(filterRequest));
    }

    @Operation(summary = "Xem chi tiết một hồ sơ xác thực mentor")
    @GetMapping("/{requestId}")
    public ApiResponse<AdminMentorVerificationRequestResponse> getRequestDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.getRequestDetail(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Xem trạng thái soft lock của hồ sơ xác thực mentor")
    @GetMapping("/{requestId}/lock")
    public ApiResponse<AdminMentorVerificationLockResponse> getLockStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.getLockStatus(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Gia hạn soft lock hồ sơ xác thực mentor thêm 5 phút")
    @PostMapping("/{requestId}/lock/refresh")
    public ApiResponse<AdminMentorVerificationLockResponse> refreshLock(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.refreshLock(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Yêu cầu mentor chỉnh sửa hồ sơ hiện tại")
    @PostMapping("/{requestId}/request-revision")
    public ApiResponse<AdminMentorVerificationRequestResponse> requestRevision(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminMentorVerificationReviewRequest request
    ) {
        return ApiResponse.success(adminMentorVerificationService.requestRevision(
                principal.getPublicId(),
                requestId,
                request.note()
        ));
    }

    @Operation(summary = "Phê duyệt hồ sơ xác thực mentor")
    @PostMapping("/{requestId}/approve")
    public ApiResponse<AdminMentorVerificationRequestResponse> approve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @RequestBody(required = false) AdminMentorVerificationReviewRequest request
    ) {
        return ApiResponse.success(adminMentorVerificationService.approve(
                principal.getPublicId(),
                requestId,
                request == null ? null : request.note()
        ));
    }

    @Operation(summary = "Từ chối hồ sơ xác thực mentor")
    @PostMapping("/{requestId}/reject")
    public ApiResponse<AdminMentorVerificationRequestResponse> reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminMentorVerificationReviewRequest request
    ) {
        return ApiResponse.success(adminMentorVerificationService.reject(
                principal.getPublicId(),
                requestId,
                request.note()
        ));
    }
}
