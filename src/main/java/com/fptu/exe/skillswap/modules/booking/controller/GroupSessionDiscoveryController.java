package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionDiscoveryResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionCommerceService;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/group-sessions")
@RequiredArgsConstructor
@Tag(name = "Group Sessions", description = "Learner discovery and seat booking APIs for published group sessions.")
@ConditionalOnProperty(prefix = "application.group-sessions", name = "enabled", havingValue = "true")
public class GroupSessionDiscoveryController {

    private final GroupSessionDiscoveryService discoveryService;
    private final GroupSessionCommerceService commerceService;
    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "List published group sessions")
    public ApiResponse<CursorPageResponse<GroupSessionDiscoveryResponse>> list(
            @RequestParam(required = false) UUID mentorUserId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(discoveryService.list(mentorUserId, serviceId, from, cursor, limit));
    }

    @GetMapping("/{groupSessionId}")
    @Operation(summary = "Get published group-session detail")
    public ApiResponse<GroupSessionDiscoveryResponse> detail(@PathVariable UUID groupSessionId) {
        return ApiResponse.success(discoveryService.detail(groupSessionId));
    }

    @PostMapping("/{groupSessionId}/bookings")
    @PreAuthorize("hasAnyRole('MENTEE', 'MENTOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a group-session seat hold", description = "Creates an attendee booking and seat hold. Payment order creation remains in the existing checkout API.")
    public ResponseEntity<ApiResponse<BookingResponse>> createSeat(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody CreateGroupSessionBookingRequest request) {
        if (principal == null) throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        var booking = commerceService.createSeat(principal.getPublicId(), groupSessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(bookingService.getBookingDetail(principal.getPublicId(), booking.getId())));
    }
}
