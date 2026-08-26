package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.admin.service.AdminBookingModerationService;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.idempotency.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin - Bookings", description = "Nhóm API vận hành nội bộ để theo dõi booking và session toàn hệ thống. FE admin dùng trong dashboard vận hành hoặc khi cần kiểm tra sự cố booking.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminBookingController {

    private final AdminBookingModerationService adminBookingModerationService;

    @Operation(
            summary = "Lấy danh sách system bookings",
            description = "Trả về danh sách booking trên toàn hệ thống phục vụ vận hành nội bộ. FE admin dùng ở các màn operation khi cần filter theo status, mentor, mentee và phân trang."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có quyền ADMIN")
    })
    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> getBookings(
            @ParameterObject @ModelAttribute AdminBookingListRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.getBookings(request));
    }

    @Operation(
            summary = "Lấy chi tiết booking cho admin",
            description = "Trả về chi tiết một booking phục vụ vận hành nội bộ. FE admin dùng khi cần toàn bộ context của booking, bao gồm participant, thời gian, trạng thái và thông tin meeting."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có quyền ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking")
    })
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBookingDetail(@PathVariable UUID bookingId) {
        return ApiResponse.success(adminBookingModerationService.getBookingDetail(bookingId));
    }

    @Operation(
            summary = "Resolve booking issue",
            description = "Admin đóng một booking đang UNDER_REVIEW sau khi xử lý dispute/manual support. Action chốt outcome session và settlement hiện có của booking; không sửa service hoặc payment order gốc."
    )
    @PostMapping("/{bookingId}/resolve-issue")
    @Idempotent
    public ApiResponse<BookingResponse> resolveIssue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminResolveBookingIssueRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.resolveBookingIssue(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Reverse booking issue resolution",
            description = "Admin đảo ngược một quyết định dispute đã đóng trước đó theo bút toán bù trừ (reversal audit log). Booking quay về UNDER_REVIEW để admin có thể xem xét và ra quyết định mới."
    )
    @PostMapping("/{bookingId}/reverse-resolution")
    @Idempotent
    public ApiResponse<BookingResponse> reverseResolution(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminReverseResolutionRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.reverseBookingIssueResolution(principal.getPublicId(), bookingId, request));
    }

}
