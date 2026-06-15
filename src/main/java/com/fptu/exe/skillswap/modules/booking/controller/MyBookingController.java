package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/bookings")
@RequiredArgsConstructor
@Tag(name = "My Bookings", description = "Các API để mentee hoặc mentor xem danh sách booking liên quan tới mình")
@SecurityRequirement(name = "bearerAuth")
public class MyBookingController {

    private final BookingService bookingService;

    @Operation(summary = "Xem danh sách booking của tôi theo vai trò mentee hoặc mentor")
    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute BookingListRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getMyBookings(principal.getPublicId(), request));
    }

    @Operation(summary = "Xem chi tiết một booking thuộc về tôi")
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBookingDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getBookingDetail(principal.getPublicId(), bookingId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
