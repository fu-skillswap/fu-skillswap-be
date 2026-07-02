package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCaseActivityListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCaseActivityItemResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCaseOwnershipResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminCaseService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/admin/cases")
@RequiredArgsConstructor
@Tag(name = "Admin - Cases", description = "Nhóm API workbench để admin nhận ownership case, gỡ ownership và xem operator activity nội bộ trên từng case vận hành.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminCaseController {

    private final AdminCaseService adminCaseService;

    @Operation(summary = "Lấy ownership của một case", description = "Trả về owner hiện tại của case vận hành để FE admin hiển thị trạng thái ai đang xử lý case này.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy ownership thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy case")
    })
    @GetMapping("/{caseType}/{caseId}/ownership")
    public ApiResponse<AdminCaseOwnershipResponse> getOwnership(
            @PathVariable String caseType,
            @PathVariable UUID caseId
    ) {
        return ApiResponse.success(adminCaseService.getOwnership(caseType, caseId));
    }

    @Operation(summary = "Assign case cho chính mình", description = "Nếu case chưa có owner thì assign cho caller; nếu caller đã giữ case thì trả idempotent; nếu case đang thuộc admin khác thì trả 409.")
    @PostMapping("/{caseType}/{caseId}/assign")
    public ApiResponse<AdminCaseOwnershipResponse> assignToMe(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String caseType,
            @PathVariable UUID caseId
    ) {
        return ApiResponse.success(adminCaseService.assignToCurrentAdmin(principal.getPublicId(), caseType, caseId));
    }

    @Operation(summary = "Gỡ ownership của case", description = "Owner hiện tại có thể tự gỡ ownership; SYSTEM_ADMIN có thể force unassign case của admin khác.")
    @PostMapping("/{caseType}/{caseId}/unassign")
    public ApiResponse<AdminCaseOwnershipResponse> unassign(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String caseType,
            @PathVariable UUID caseId
    ) {
        return ApiResponse.success(adminCaseService.unassign(principal.getPublicId(), java.util.Set.copyOf(principal.getRoles()), caseType, caseId));
    }

    @Operation(summary = "Lấy operator activity của case", description = "Trả về history vận hành nội bộ của case từ admin notes, audit logs và các safe action Phase 3. Đây không phải full business timeline của domain.")
    @GetMapping("/{caseType}/{caseId}/activity")
    public ApiResponse<PageResponse<AdminCaseActivityItemResponse>> getActivity(
            @PathVariable String caseType,
            @PathVariable UUID caseId,
            @ParameterObject @ModelAttribute AdminCaseActivityListRequest request
    ) {
        return ApiResponse.success(adminCaseService.getActivity(caseType, caseId, request));
    }
}
