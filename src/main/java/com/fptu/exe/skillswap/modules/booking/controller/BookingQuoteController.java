package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingQuoteRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingQuoteResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingQuoteService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mentor Booking", description = "Xem trước giá và hạn thanh toán trước khi tạo booking.")
public class BookingQuoteController {

    private final BookingQuoteService bookingQuoteService;

    @PostMapping("/quote")
    @PreAuthorize("hasAnyRole('MENTEE', 'MENTOR')")
    @Operation(summary = "Xem trước giá booking",
            description = "FE gọi sau khi người dùng chọn mentor, service, slot và candidate segment. API chỉ kiểm tra lựa chọn và trả giá cùng hạn thanh toán; không tạo booking, không giữ slot và không trừ credit/coupon.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Trả về báo giá để FE hiển thị trước khi tạo booking",
            content = @Content(examples = @ExampleObject(
                    name = "Quote thành công",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "slotId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                                "serviceTitle": "Review Spring Boot",
                                "durationMinutes": 60,
                                "scheduledStartAt": "2026-08-30T19:00:00+07:00",
                                "scheduledEndAt": "2026-08-30T20:00:00+07:00",
                                "pendingExpireAt": "2026-08-30T19:15:00+07:00",
                                "paymentWindowMinutes": 60,
                                "paymentPreparationBufferMinutes": 60,
                                "pricing": {
                                  "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                                  "priceScoin": 110,
                                  "estimatedPayableScoin": 110,
                                  "isEstimate": true
                                },
                                "isEstimate": true,
                                "disclaimer": "Giá có thể thay đổi khi tạo booking hoặc checkout."
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "slotId, serviceId hoặc startAt không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không đủ điều kiện tạo booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy mentor, slot hoặc service"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slot, service hoặc thời gian không còn khả dụng; tải lại availability trước khi thử lại")
    })
    public ApiResponse<BookingQuoteResponse> quote(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Gửi slotId, serviceId và startAt lấy từ candidate segment có isSelectable=true. startAt nên truyền UTC Instant.",
                    content = @Content(examples = @ExampleObject(
                            name = "Request quote",
                            value = """
                                    {
                                      "slotId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                      "serviceId": "019f4234-bbbb-cccc-dddd-1234567890ab",
                                      "startAt": "2026-08-30T11:00:00Z"
                                    }
                                    """)))
            BookingQuoteRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(bookingQuoteService.quote(principal.getPublicId(), request));
    }
}
