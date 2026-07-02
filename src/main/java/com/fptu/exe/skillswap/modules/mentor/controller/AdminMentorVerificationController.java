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
@Tag(name = "Admin - Mentor Verification", description = "Nhóm API cho admin review hồ sơ mentor verification, xem chi tiết request và xử lý quyết định theo cơ chế soft lock. FE admin dùng trong queue review và màn hình xử lý hồ sơ.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminMentorVerificationController {

    private final AdminMentorVerificationService adminMentorVerificationService;

    @Operation(summary = "Lấy verification queue", description = "Trả về queue mentor verification dành cho admin với các lựa chọn search, filter, pagination và sort phục vụ vận hành. FE admin dùng ở màn queue trước khi mở một request cụ thể để review.")
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

    @Operation(summary = "Lấy chi tiết verification request", description = "Trả về chi tiết của một mentor verification request để admin review. FE admin dùng khi mở một request từ queue; backend có thể tự claim soft lock như một side effect nếu request vẫn đang pending review.")
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
        if (principal == null || !(principal.getRoles().contains(RoleCode.ADMIN) || principal.getRoles().contains(RoleCode.SYSTEM_ADMIN))) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện hành động này");
        }
        return principal.getPublicId();
    }

    @Operation(summary = "Lấy trạng thái verification lock", description = "Trả về trạng thái soft lock hiện tại của một mentor verification request. FE admin dùng để biết request đang bị admin nào giữ lock và có nên disable các action quyết định hay không.")
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

    @Operation(summary = "Gia hạn verification lock", description = "Gia hạn soft lock hiện tại của một mentor verification request. FE admin dùng trong lúc reviewer đang xử lý hồ sơ; việc refresh lock chỉ hợp lệ với admin đang sở hữu lock đó.")
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

    @Operation(summary = "Giải phóng verification lock", description = "Owner hiện tại của soft lock có thể tự release lock; SYSTEM_ADMIN có thể force release lock của admin khác mà không đổi status của request.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Release lock thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền release lock này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy request")
    })
    @PostMapping("/{requestId}/lock/release")
    public ApiResponse<AdminMentorVerificationLockResponse> releaseLock(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId
    ) {
        return ApiResponse.success(adminMentorVerificationService.releaseLock(principal.getPublicId(), java.util.Set.copyOf(principal.getRoles()), requestId));
    }

    @Operation(summary = "Yêu cầu chỉnh sửa verification", description = "Đưa mentor verification request về trạng thái cần chỉnh sửa để mentor tiếp tục sửa trên chính request hiện tại thay vì tạo lại từ đầu. FE admin dùng khi reviewer muốn mentor bổ sung hoặc sửa document/profile.")
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

    @Operation(summary = "Phê duyệt verification request", description = "Phê duyệt mentor verification request và hoàn tất bước review của admin. FE admin dùng khi reviewer xác nhận mentor đã đáp ứng yêu cầu xác thực và có thể đi tiếp như một verified mentor.")
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

    @Operation(summary = "Từ chối verification request", description = "Từ chối mentor verification request và đóng flow review hiện tại. FE admin dùng khi reviewer quyết định request không được tiếp tục và mentor sẽ phải tạo request mới sau này nếu muốn thử lại.")
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
