package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Admin Bookings", description = "API để admin theo dõi booking mentoring toàn hệ thống")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingService bookingService;

    @Operation(summary = "Xem danh sách booking toàn hệ thống với filter dành cho admin")
    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> getBookings(
            @ParameterObject @ModelAttribute AdminBookingListRequest request
    ) {
        return ApiResponse.success(bookingService.getAdminBookings(request));
    }

    @Operation(summary = "Xem chi tiết một booking dành cho admin")
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBookingDetail(@PathVariable UUID bookingId) {
        return ApiResponse.success(bookingService.getAdminBookingDetail(bookingId));
    }
}
