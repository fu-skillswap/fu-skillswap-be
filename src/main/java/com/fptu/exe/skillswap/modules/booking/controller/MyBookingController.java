package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
@RequestMapping("/api/me/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking & Session", description = "Requesting mentoring slots, managing booking queue, tracking sessions, and meeting links")
@SecurityRequirement(name = "bearerAuth")
public class MyBookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Xem danh sách booking của tôi",
            description = """
                    Trả danh sách booking theo góc nhìn của chính người dùng hiện tại.
                    - role=MENTEE: chỉ trả các booking do tôi tạo
                    - role=MENTOR: chỉ trả các booking được gửi tới tôi với tư cách mentor
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> getMyBookings(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject @ModelAttribute BookingListRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getMyBookings(principal.getPublicId(), request));
    }

    @Operation(
            summary = "Xem chi tiết một booking thuộc về tôi",
            description = "Chỉ trả dữ liệu nếu booking này thuộc về chính người dùng hiện tại với vai trò mentee hoặc mentor."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy chi tiết booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về người dùng hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking")
    })
    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> getBookingDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.getBookingDetail(principal.getPublicId(), bookingId));
    }

    @Operation(
            summary = "Mentee hủy booking của chính mình",
            description = """
                    Mentee chỉ được hủy booking của mình ở các trạng thái backend cho phép.
                    Business rule hiện tại yêu cầu phải gửi cancelReason.
                    Các ngưỡng phạt hoặc giới hạn sát giờ sẽ do backend tự tính theo timezone Asia/Ho_Chi_Minh.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hủy booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu lý do hủy hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về mentee hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không ở trạng thái có thể hủy hoặc đã quá mốc thời gian backend cho phép")
    })
    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.cancelBookingByMentee(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Đánh dấu booking đã hoàn thành",
            description = """
                    Dùng cho mentee hoặc mentor đánh dấu buổi mentoring đã hoàn thành.
                    Backend chỉ cho phép khi booking đã được ACCEPTED và đã tới thời điểm phù hợp để complete.
                    completionNote là tùy chọn, dùng để lưu ghi chú ngắn sau buổi học.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đánh dấu hoàn thành thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "completionNote vượt ràng buộc độ dài"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking không thuộc về người dùng hiện tại"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa đủ điều kiện để complete hoặc đã hoàn thành trước đó")
    })
    @PostMapping("/{bookingId}/complete")
    public ApiResponse<BookingResponse> completeBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CompleteBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.completeBooking(principal.getPublicId(), bookingId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
