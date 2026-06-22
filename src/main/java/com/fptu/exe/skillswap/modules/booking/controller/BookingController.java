package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking & Session", description = "Requesting mentoring slots, managing booking queue, tracking sessions, and meeting links")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Tạo booking request từ mentee tới mentor",
            description = """
                    Tạo một yêu cầu booking mới vào slot còn hiển thị trên discovery.
                    
                    Luồng queue hiện tại:
                    - Tạo request mới chỉ đưa booking vào trạng thái PENDING, chưa giữ chỗ độc quyền cho mentee.
                    - Mỗi slot cho phép tối đa 3 request PENDING cùng lúc.
                    - Khi mentor ACCEPTED một request trong slot, các request PENDING còn lại của cùng slot sẽ tự động chuyển REJECTED.
                    
                    Các trường hợp thường bị từ chối:
                    - slot đã bị ẩn vì quá khứ, inactive, đã được ACCEPTED, hoặc đã đủ 3 request PENDING
                    - mentee đã có 3 booking PENDING tổng cộng trong hệ thống
                    - mentor không còn ở trạng thái sẵn sàng nhận lịch
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo booking request thành công, response trả về booking ở trạng thái PENDING"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ hoặc learning goal vượt ràng buộc"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có quyền mentee hợp lệ để tạo booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor, slot hoặc service được tham chiếu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Xung đột nghiệp vụ: slot không còn nhận request, mentee vượt quota pending, mentor không sẵn sàng hoặc slot không thuộc mentor đã chọn")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request) {
        ensureAuthenticated(principal);
        BookingResponse response = bookingService.createBooking(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
