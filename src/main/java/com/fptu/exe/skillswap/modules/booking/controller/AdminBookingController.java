package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "Admin - Bookings", description = "Nhóm API vận hành nội bộ để theo dõi booking và session toàn hệ thống. FE admin dùng trong dashboard vận hành hoặc khi cần kiểm tra sự cố booking.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

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
        return ApiResponse.success(bookingService.getAdminBookings(request));
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
        return ApiResponse.success(bookingService.getAdminBookingDetail(bookingId));
    }
}
