package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mentor/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Bookings", description = "Các API để mentor xử lý hàng đợi booking request và cập nhật thông tin buổi mentoring")
@SecurityRequirement(name = "bearerAuth")
public class MentorBookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Mentor chấp nhận một booking đang chờ phản hồi",
            description = """
                    Chỉ dùng cho booking ở trạng thái PENDING và thuộc về mentor hiện tại.
                    Khi một booking trong slot được ACCEPTED:
                    - slot sẽ được xem là đã có lịch chính thức
                    - các booking PENDING còn lại của cùng slot sẽ tự động chuyển REJECTED với lý do hệ thống
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chấp nhận booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không còn ở trạng thái PENDING hoặc slot đã được xử lý bởi request khác")
    })
    @PostMapping("/{bookingId}/accept")
    public ApiResponse<BookingResponse> acceptBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) AcceptBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.acceptBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Mentor từ chối booking đang chờ phản hồi",
            description = "Mentor phải gửi rejectReason rõ ràng. Chỉ từ chối được booking PENDING thuộc về mình."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Từ chối booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu rejectReason hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không còn ở trạng thái PENDING để từ chối")
    })
    @PostMapping("/{bookingId}/reject")
    public ApiResponse<BookingResponse> rejectBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.rejectBooking(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Mentor hủy booking đã chấp nhận",
            description = """
                    Mentor chỉ hủy các booking thuộc về mình và phải gửi cancelReason.
                    Backend tự áp business rule phạt khi hủy sát giờ theo timezone Asia/Ho_Chi_Minh.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Mentor hủy booking thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu cancelReason hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking không ở trạng thái cho phép mentor hủy hoặc đã quá mốc thời gian backend cho phép")
    })
    @PostMapping("/{bookingId}/cancel")
    public ApiResponse<BookingResponse> cancelBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.cancelBookingByMentor(principal.getPublicId(), bookingId, request));
    }

    @Operation(
            summary = "Mentor lưu hoặc cập nhật meeting link",
            description = "Chỉ áp dụng cho booking đã được ACCEPTED và thuộc về mentor hiện tại. FE nên gọi lại detail sau khi cập nhật để đồng bộ meetingPlatform, meetingLink và location."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu meeting link thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "meetingPlatform hoặc meetingLink không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentor của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa được ACCEPTED hoặc không còn cho phép cập nhật meeting link")
    })
    @PatchMapping("/{bookingId}/meeting-link")
    public ApiResponse<BookingResponse> saveMeetingLink(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody SaveMeetingLinkRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(bookingService.saveMeetingLink(principal.getPublicId(), bookingId, request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
