package com.fptu.exe.skillswap.modules.feedback.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.feedback.dto.response.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Validated
@Tag(name = "Session Feedback", description = "API để mentee gửi feedback công khai hoặc riêng tư sau khi buổi mentoring đã hoàn thành")
@SecurityRequirement(name = "bearerAuth")
public class SessionFeedbackController {

    private final SessionFeedbackService sessionFeedbackService;

    @Operation(
            summary = "Gửi feedback cho buổi mentoring đã hoàn thành",
            description = """
                    Chỉ mentee tham gia booking mới được gửi feedback cho mentor.
                    Booking bắt buộc ở trạng thái COMPLETED.
                    Mỗi mentee chỉ gửi một feedback cho mỗi booking.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo feedback thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu feedback không hợp lệ, ví dụ rating ngoài khoảng 1-5"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không phải mentee của booking này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking chưa COMPLETED hoặc người dùng đã gửi feedback trước đó")
    })
    @PostMapping("/{bookingId}/feedback")
    public ResponseEntity<ApiResponse<SessionFeedbackResponse>> submitFeedback(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(
                principal.getPublicId(),
                bookingId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
