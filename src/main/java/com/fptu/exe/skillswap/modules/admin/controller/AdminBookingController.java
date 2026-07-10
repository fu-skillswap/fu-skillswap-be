package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.modules.admin.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.admin.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminBookingModerationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
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
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;

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
            description = "Admin đóng một booking đang UNDER_REVIEW sau khi xử lý dispute/manual support. Action chỉ finalize lại booking, không đổi service hay payment gốc."
    )
    @PostMapping("/{bookingId}/resolve-issue")
    public ApiResponse<BookingResponse> resolveIssue(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody AdminResolveBookingIssueRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.resolveBookingIssue(principal.getPublicId(), bookingId, request));
    }

    @GetMapping("/{bookingId}/reschedule-requests")
    @Operation(
            summary = "Admin xem yêu cầu đổi lịch của booking",
            description = "Trả về toàn bộ reschedule request của một booking để admin kiểm tra lịch sử đổi lịch khi xử lý vận hành hoặc dispute."
    )
    public ApiResponse<java.util.List<BookingRescheduleRequestResponse>> getRescheduleRequests(@PathVariable UUID bookingId) {
        return ApiResponse.success(adminBookingModerationService.getRescheduleRequests(bookingId));
    }

    @PostMapping("/reschedule-requests/{requestId}/force-approve")
    @Operation(
            summary = "Admin force approve yêu cầu đổi lịch",
            description = "Admin phê duyệt thủ công một reschedule request đang pending khi cần can thiệp vận hành. Backend ghi nhận actor admin và áp dụng lịch mới nếu request còn hợp lệ."
    )
    public ApiResponse<BookingRescheduleRequestResponse> approveRescheduleRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.acceptRescheduleRequest(principal.getPublicId(), requestId, request));
    }

    @PostMapping("/reschedule-requests/{requestId}/force-reject")
    @Operation(
            summary = "Admin force reject yêu cầu đổi lịch",
            description = "Admin từ chối thủ công một reschedule request đang pending khi cần xử lý vận hành hoặc dispute. Booking hiện tại được giữ nguyên."
    )
    public ApiResponse<BookingRescheduleRequestResponse> rejectRescheduleRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId,
            @Valid @RequestBody RespondBookingRescheduleRequest request
    ) {
        return ApiResponse.success(adminBookingModerationService.rejectRescheduleRequest(principal.getPublicId(), requestId, request));
    }
}
