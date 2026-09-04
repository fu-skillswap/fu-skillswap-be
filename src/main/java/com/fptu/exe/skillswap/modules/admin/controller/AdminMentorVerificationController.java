package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminMentorVerificationReviewRequest;
import com.fptu.exe.skillswap.modules.admin.service.AdminMentorVerificationModerationService;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorVerificationQueueFilterRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationLockResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationQueueItemResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorVerificationRequestResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@Tag(name = "Admin - Mentor Verification", description = "Admin - dành cho quản trị viên. Xem queue và xử lý hồ sơ đăng ký mentor; không dùng cho FE người dùng thông thường.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminMentorVerificationController {

    private final AdminMentorVerificationModerationService AdminMentorVerificationModerationService;

    @Operation(summary = "Bước 1 - Lấy danh sách hồ sơ chờ duyệt", description = "Có thể tìm kiếm, lọc và phân trang.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách hồ sơ cần duyệt",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "VerificationQueue",
                            value = """
                                    {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"content":[{"requestId":"019f5234-aaaa-bbbb-cccc-1234567890ab","mentorUserId":"019f6234-aaaa-bbbb-cccc-1234567890ab","mentorEmail":"mentor@example.com","mentorFullName":"Nguyen Van B","status":"PENDING_REVIEW","revisionCount":0,"submittedAt":"2026-09-04T03:20:00","createdAt":"2026-09-04T03:00:00","updatedAt":"2026-09-04T03:20:00"}],"page":0,"size":20,"totalElements":1,"totalPages":1,"last":true}}
                                    """
                    ))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    @GetMapping
    public ApiResponse<PageResponse<AdminMentorVerificationQueueItemResponse>> getQueue(
            @ParameterObject @ModelAttribute AdminMentorVerificationQueueFilterRequest filterRequest
    ) {
        return ApiResponse.success(AdminMentorVerificationModerationService.getQueue(filterRequest));
    }

    @Operation(summary = "Bước 2 - Mở chi tiết hồ sơ", description = "Hệ thống có thể tự giữ hồ sơ cho admin đang xem.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.getRequestDetail(requiredAdminId(principal), requestId));
    }

    private UUID requiredAdminId(UserPrincipal principal) {
        if (principal == null || !(principal.getRoles().contains(RoleCode.ADMIN) || principal.getRoles().contains(RoleCode.SYSTEM_ADMIN))) {
            throw new AccessDeniedException("Bạn không có quyền thực hiện hành động này");
        }
        return principal.getPublicId();
    }

    @Operation(summary = "Kiểm tra ai đang xử lý hồ sơ", description = "FE dùng để bật hoặc khóa các nút xử lý.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.getLockStatus(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Gia hạn thời gian giữ hồ sơ", description = "Chỉ admin đang giữ hồ sơ mới dùng được.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.refreshLock(principal.getPublicId(), requestId));
    }

    @Operation(summary = "Ngừng giữ hồ sơ", description = "Admin hiện tại hoặc system admin có thể thực hiện.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.releaseLock(principal.getPublicId(), java.util.Set.copyOf(principal.getRoles()), requestId));
    }

    @Operation(summary = "Bước 3A - Yêu cầu mentor bổ sung", description = "Mentor tiếp tục sửa trên hồ sơ hiện tại.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.requestRevision(
                principal.getPublicId(),
                requestId,
                request.note()
        ));
    }

    @Operation(summary = "Bước 3B - Duyệt hồ sơ mentor", description = "Admin - dành cho quản trị viên. Dùng sau khi đã xem minh chứng; request có thể kèm note, kết quả trả hồ sơ với status APPROVED.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.approve(
                principal.getPublicId(),
                requestId,
                request == null ? null : request.note()
        ));
    }

    @Operation(summary = "Bước 3C - Từ chối hồ sơ mentor", description = "Đóng hồ sơ hiện tại; mentor cần tạo hồ sơ mới nếu đăng ký lại.")
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
        return ApiResponse.success(AdminMentorVerificationModerationService.reject(
                principal.getPublicId(),
                requestId,
                request.note()
        ));
    }
}
