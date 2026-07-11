package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminQueueCaseListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardOverviewResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardQueuesResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminDashboardTimeseriesResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminQueueCaseItemResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminDashboardService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin - Dashboard", description = "Nhóm API snapshot, queue cards, queue drill-down và timeseries dành cho admin dashboard/workbench. FE admin dùng để hiển thị tổng quan vận hành, backlog cần xử lý và mở từng queue case cụ thể.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
            summary = "Lấy snapshot tổng quan admin dashboard",
            description = "Trả về snapshot tổng quan user, mentor verification, booking, forum report, payout request và payment order theo đúng raw status hiện tại của backend. FE admin dùng endpoint này để dựng các widget tổng hợp trên trang dashboard."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy snapshot thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping("/overview")
    public ApiResponse<AdminDashboardOverviewResponse> getOverview() {
        return ApiResponse.success(adminDashboardService.getOverview());
    }

    @Operation(
            summary = "Lấy queue vận hành ưu tiên cho admin dashboard",
            description = "Trả về danh sách queue card cố định theo thứ tự ưu tiên để admin biết nhóm công việc nào cần xử lý trước ngay khi mở dashboard."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy queue thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping("/queues")
    public ApiResponse<AdminDashboardQueuesResponse> getQueues() {
        return ApiResponse.success(adminDashboardService.getQueues());
    }

    @Operation(
            summary = "Lấy timeseries 30 ngày cho admin dashboard",
            description = "Trả về chuỗi thời gian 30 ngày gần nhất theo timezone Asia/Ho_Chi_Minh với daily volume của user mới, mentor verification submit, booking mới, payment paid, forum report và payout request."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy timeseries thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping("/timeseries")
    public ApiResponse<AdminDashboardTimeseriesResponse> getTimeseries() {
        return ApiResponse.success(adminDashboardService.getTimeseries());
    }

    @Operation(
            summary = "Drill-down queue workbench items",
            description = "Trả về danh sách case chi tiết của một queue workbench để FE admin mở queue center, lọc case đang giữ hoặc case chưa assign và điều hướng sang detail API hiện có."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy queue items thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    @GetMapping("/queue-items")
    public ApiResponse<PageResponse<AdminQueueCaseItemResponse>> getQueueItems(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute AdminQueueCaseListRequest request
    ) {
        return ApiResponse.success(adminDashboardService.getQueueItems(principal.getPublicId(), request));
    }
}
