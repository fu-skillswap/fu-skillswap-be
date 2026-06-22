package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationReviewRequest;
import com.fptu.exe.skillswap.modules.mentor.service.AdminMentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mentor-verification/requests")
@RequiredArgsConstructor
@Tag(name = "Admin - Mentor Verification", description = "Admin approval workflow for pending mentor verification requests")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorVerificationController {

    private final AdminMentorVerificationService adminMentorVerificationService;

    @Operation(summary = "Xem danh sách hồ sơ xác thực mentor với filter/search tối ưu cho admin queue")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminMentorVerificationQueueItemResponse>> getQueue(
            @ParameterObject @ModelAttribute AdminMentorVerificationQueueFilterRequest filterRequest
    ) {
        return ApiResponse.success(adminMentorVerificationService.getQueue(filterRequest));
    }

    @Operation(summary = "Xem chi tiết một hồ sơ xác thực mentor và tự động claim soft lock nếu đang ở trạng thái pending")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy hồ sơ")
    })
    @GetMapping("/{requestId}")
    public ApiResponse<AdminMentorVerificationRequestResponse> getRequestDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.getRequestDetail(requiredAdminId(principal), requestId));
    }

    private UUID requiredAdminId(UserPrincipal principal) {
        if (principal == null || !principal.getRoles().contains(RoleCode.ADMIN)) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện hành động này");
        }
        return principal.getPublicId();
    }

    @Operation(summary = "Xem trạng thái soft lock hiện tại của hồ sơ xác thực mentor")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy trạng thái lock thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @GetMapping("/{requestId}/lock")
    public ApiResponse<AdminMentorVerificationLockResponse> getLockStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.getLockStatus(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Gia hạn soft lock hồ sơ xác thực mentor thêm 5 phút cho admin đang xử lý")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gia hạn lock thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @PostMapping("/{requestId}/lock/refresh")
    public ApiResponse<AdminMentorVerificationLockResponse> refreshLock(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.refreshLock(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Yêu cầu mentor chỉnh sửa hồ sơ hiện tại trên request cũ")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Yêu cầu chỉnh sửa thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @PostMapping("/{requestId}/request-revision")
    public ApiResponse<AdminMentorVerificationRequestResponse> requestRevision(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody AdminMentorVerificationReviewRequest request
    ) {
        return ApiResponse.success(adminMentorVerificationService.requestRevision(
                principal.getPublicId(),
                requestId,
                request.note()
        ));
    }

    @Operation(summary = "Phê duyệt hồ sơ xác thực mentor và mở khóa request")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Phê duyệt thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @PostMapping("/{requestId}/approve")
    public ApiResponse<AdminMentorVerificationRequestResponse> approve(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) AdminMentorVerificationReviewRequest request
    ) {
        return ApiResponse.success(adminMentorVerificationService.approve(
                principal.getPublicId(),
                requestId,
                request == null ? null : request.note()
        ));
    }

    @Operation(summary = "Từ chối hồ sơ xác thực mentor và khóa request")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Từ chối thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @PostMapping("/{requestId}/reject")
    public ApiResponse<AdminMentorVerificationRequestResponse> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
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
