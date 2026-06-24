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
@Tag(name = "Mentor Booking", description = "Nhóm API cho toàn bộ vòng đời booking: mentee tạo request, hai bên xem chi tiết, mentor accept/reject, hai bên cancel/complete và mentor cập nhật meeting info. FE dùng nhóm này sau khi mentee đã chọn mentor, service và slot.")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    @Operation(
            summary = "Tạo booking request",
            description = """
                    Tạo một booking request mới ở trạng thái PENDING cho mentor service và availability slot mà mentee đã chọn.
                    
                    FE dùng API này sau khi mentee chọn mentor, chọn slot còn hiển thị và nhập learning goal. Slot chưa được giữ chính thức cho mentee cho đến khi mentor accept request.
                    
                    FE cần handle các trường hợp conflict khi slot không còn hiển thị, mentor không còn discoverable, service không thuộc mentor, mentee vượt quota pending hoặc slot đã đầy hàng chờ pending.
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
