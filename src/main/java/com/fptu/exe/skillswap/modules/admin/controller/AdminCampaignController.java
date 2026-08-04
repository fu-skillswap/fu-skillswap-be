package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignBenefitUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignCreateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignStatusRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminCampaignUpdateRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignAnalyticsResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignBenefitResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminCampaignResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminCampaignService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
@Tag(name = "Admin - Campaigns", description = "Nhóm API cho admin quản lý các chiến dịch khuyến mãi (Campaigns), điều kiện đối tượng (Audience) và quyền lợi (Benefits).")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminCampaignController {

    private final AdminCampaignService adminCampaignService;

    @GetMapping
    @Operation(summary = "Lấy danh sách các campaign (paginated & filtered)", description = "Hỗ trợ lọc theo status, fundingSource, keyword.")
    public ApiResponse<PageResponse<AdminCampaignResponse>> list(
            @ParameterObject @ModelAttribute AdminCampaignListRequest request
    ) {
        return ApiResponse.success(adminCampaignService.list(request));
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Lấy chi tiết một campaign theo ID")
    public ApiResponse<AdminCampaignResponse> getDetail(@PathVariable UUID campaignId) {
        return ApiResponse.success(adminCampaignService.getDetail(campaignId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo campaign mới (Status ban đầu: DRAFT hoặc SCHEDULED)")
    public ApiResponse<AdminCampaignResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminCampaignCreateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(adminCampaignService.create(principal.getPublicId(), request));
    }

    @PutMapping("/{campaignId}")
    @Operation(summary = "Cập nhật thông tin campaign (Chỉ cập nhật khi DRAFT, SCHEDULED hoặc PAUSED)")
    public ApiResponse<AdminCampaignResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @Valid @RequestBody AdminCampaignUpdateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminCampaignService.update(principal.getPublicId(), campaignId, request));
    }

    @PatchMapping("/{campaignId}/status")
    @Operation(summary = "Chuyển trạng thái của campaign (DRAFT -> SCHEDULED -> ACTIVE -> PAUSED -> ENDED -> ARCHIVED)")
    public ApiResponse<AdminCampaignResponse> changeStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @Valid @RequestBody AdminCampaignStatusRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminCampaignService.changeStatus(principal.getPublicId(), campaignId, request));
    }

    @GetMapping("/{campaignId}/analytics")
    @Operation(summary = "Xem báo cáo phân tích hiệu quả chiến dịch (ROI, budget burn rate, số đơn hàng, doanh thu)")
    public ApiResponse<AdminCampaignAnalyticsResponse> getAnalytics(@PathVariable UUID campaignId) {
        return ApiResponse.success(adminCampaignService.getAnalytics(campaignId));
    }

    // --- Campaign Benefits ---

    @GetMapping("/{campaignId}/benefits")
    @Operation(summary = "Lấy danh sách các quyền lợi (Benefits) của một campaign")
    public ApiResponse<List<AdminCampaignBenefitResponse>> listBenefits(@PathVariable UUID campaignId) {
        return ApiResponse.success(adminCampaignService.listBenefits(campaignId));
    }

    @PostMapping("/{campaignId}/benefits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm quyền lợi mới cho campaign")
    public ApiResponse<AdminCampaignBenefitResponse> createBenefit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @Valid @RequestBody AdminCampaignBenefitCreateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(adminCampaignService.createBenefit(principal.getPublicId(), campaignId, request));
    }

    @PutMapping("/{campaignId}/benefits/{benefitId}")
    @Operation(summary = "Cập nhật quyền lợi của campaign")
    public ApiResponse<AdminCampaignBenefitResponse> updateBenefit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @PathVariable UUID benefitId,
            @Valid @RequestBody AdminCampaignBenefitUpdateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminCampaignService.updateBenefit(principal.getPublicId(), campaignId, benefitId, request));
    }

    @DeleteMapping("/{campaignId}/benefits/{benefitId}")
    @Operation(summary = "Xóa một quyền lợi của campaign")
    public ApiResponse<Void> deleteBenefit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID campaignId,
            @PathVariable UUID benefitId
    ) {
        ensureAuthenticated(principal);
        adminCampaignService.deleteBenefit(principal.getPublicId(), campaignId, benefitId);
        return ApiResponse.success(null);
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
