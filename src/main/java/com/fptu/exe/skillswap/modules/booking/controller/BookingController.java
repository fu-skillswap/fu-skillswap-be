package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking", description = "Luồng đặt lịch: xem giá → tạo request → mentor chấp nhận hoặc từ chối → thanh toán → tham gia buổi học → xác nhận hoàn tất hoặc báo vấn đề.")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;
    private final InMemoryRateLimitService rateLimitService;

    @Operation(
            summary = "Tạo booking request",
            description = """
                    Tạo yêu cầu đặt lịch ở trạng thái PENDING sau khi người học đã chọn mentor, dịch vụ và thời gian bắt đầu.

                    FE nên gọi API xem giá trước, sau đó gọi API này. Tạo request chưa có nghĩa là lịch đã được chấp nhận; mentor cần xử lý request và người học tiếp tục thanh toán nếu request được chấp nhận.

                    Nếu slot, dịch vụ hoặc thời gian không còn hợp lệ, API trả lỗi 409. FE nên tải lại thông tin mentor và lịch trước khi cho người dùng thử lại.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo booking request thành công, response trả về booking ở trạng thái PENDING"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu đầu vào không hợp lệ hoặc learning goal vượt ràng buộc"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có quyền mentee hợp lệ để tạo booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor, slot hoặc service được tham chiếu"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Xung đột nghiệp vụ: slot không còn nhận request, mentee vượt quota pending, mentor không sẵn sàng hoặc slot không thuộc mentor đã chọn", content = @Content(examples = @ExampleObject(
                    name = "Slot không còn khả dụng",
                    value = """
                            {
                              "status": 409,
                              "code": "SYS_0007",
                              "message": "Slot không còn khả dụng. Vui lòng tải lại lịch và chọn thời gian khác."
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo booking thành công", content = @Content(examples = @ExampleObject(
                    name = "Booking PENDING",
                    value = """
                            {
                              "status": 201,
                              "code": "CREATED_0201",
                              "message": "Tạo mới thành công",
                              "data": {
                                "bookingId": "019f5234-aaaa-bbbb-cccc-1234567890ab",
                                "bookingStatus": "REQUESTED",
                                "paymentStatus": "PENDING",
                                "mentorUserId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
                                "menteeUserId": "019f1234-bbbb-cccc-dddd-1234567890ab",
                                "selectedStartTime": "2026-08-30T19:00:00+07:00",
                                "selectedEndTime": "2026-08-30T20:00:00+07:00",
                                "pendingExpireAt": "2026-08-30T19:15:00+07:00",
                                "canCancel": true,
                                "canPay": false,
                                "nextAction": "NONE"
                              }
                            }
                            """)))
    })
    /**
     * Design decision: MENTOR is intentionally allowed to create a booking (as a mentee of another mentor).
     * This is a valid business use case — mentors can also be learners on the platform.
     *
     * ADMIN and SYSTEM_ADMIN are blocked here (defense-in-depth over service-layer check in
     * validateBookerEligibility). Fail fast at controller, return 403 before reaching service.
     */
    @PreAuthorize("hasAnyRole('MENTEE', 'MENTOR')")
    @PostMapping
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Tạo request sau khi quote thành công. startAt lấy từ candidate segment đã chọn; learningGoalTitle là nội dung FE cần hỏi người học.",
                    content = @Content(examples = @ExampleObject(
                            name = "Request tạo booking",
                            value = """
                                    {
                                      "slotId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                      "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                                      "startAt": "2026-08-30T11:00:00Z",
                                      "learningGoalTitle": "Review lộ trình Spring Boot",
                                      "learningGoalDescription": "Em muốn được góp ý CV backend và cách chuẩn bị phỏng vấn intern."
                                    }
                                    """)))
            CreateBookingRequest request) {
        ensureAuthenticated(principal);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BUSINESS,
                "booking:create:" + principal.getPublicId(),
                12,
                java.time.Duration.ofMinutes(10),
                "Bạn đang tạo booking quá nhanh, vui lòng chờ thêm một chút"
        );
        BookingResponse response = bookingService.createBooking(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
