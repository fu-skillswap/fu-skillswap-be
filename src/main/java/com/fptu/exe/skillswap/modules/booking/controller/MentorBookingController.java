package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mentor/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Bookings", description = "Các API để mentor phản hồi booking từ mentee")
@SecurityRequirement(name = "bearerAuth")
public class MentorBookingController {

    private final BookingService bookingService;

    @Operation(summary = "Mentor chấp nhận booking đang chờ phản hồi")
    @PostMapping("/{bookingId}/accept")
    public ApiResponse<BookingResponse> acceptBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) AcceptBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.acceptBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(summary = "Mentor từ chối booking đang chờ phản hồi")
    @PostMapping("/{bookingId}/reject")
    public ApiResponse<BookingResponse> rejectBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.rejectBooking(principal.getPublicId(), bookingId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
