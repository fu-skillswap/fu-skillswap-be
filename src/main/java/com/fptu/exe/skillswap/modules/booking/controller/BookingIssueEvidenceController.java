package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingIssueEvidenceUploadIntentRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueDetailResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceDownloadResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingIssueEvidenceUploadIntentResponse;
import com.fptu.exe.skillswap.modules.booking.service.BookingIssueEvidenceService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking dispute evidence", description = "Upload private và xem minh chứng của dispute. Chat vẫn khóa khi booking UNDER_REVIEW.")
@SecurityRequirement(name = "bearerAuth")
public class BookingIssueEvidenceController {
    private final BookingIssueEvidenceService evidenceService;

    @PostMapping("/{bookingId}/issue/evidence/upload-intents")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    @Operation(summary = "Tạo upload intent minh chứng", description = "Tạo URL upload private cho JPG, PNG hoặc PDF (tối đa 10 MB). Reporter cần confirm rồi gắn 1–5 file vào API tạo issue.")
    public ApiResponse<BookingIssueEvidenceUploadIntentResponse> createIntent(@AuthenticationPrincipal UserPrincipal principal,
                                                                                @PathVariable UUID bookingId,
                                                                                @Valid @RequestBody BookingIssueEvidenceUploadIntentRequest request) {
        return ApiResponse.success(evidenceService.createUploadIntent(requireUser(principal), bookingId, request));
    }

    @PostMapping("/{bookingId}/issue/evidence/upload-intents/{intentId}/confirm")
    @com.fptu.exe.skillswap.shared.idempotency.Idempotent
    @Operation(summary = "Xác nhận file minh chứng", description = "Backend kiểm tra kích thước và chữ ký file, sau đó chuyển file sang vùng private bất biến.")
    public ApiResponse<BookingIssueEvidenceResponse> confirmIntent(@AuthenticationPrincipal UserPrincipal principal,
                                                                     @PathVariable UUID bookingId, @PathVariable UUID intentId) {
        return ApiResponse.success(evidenceService.confirmUploadIntent(requireUser(principal), bookingId, intentId));
    }

    @GetMapping("/{bookingId}/issue/detail")
    @Operation(summary = "Xem dispute và minh chứng", description = "Chỉ mentor và mentee của booking xem được file ACTIVE. File bị admin ẩn sẽ không lộ cho participant.")
    public ApiResponse<BookingIssueDetailResponse> detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID bookingId) {
        return ApiResponse.success(evidenceService.getForParticipant(requireUser(principal), bookingId));
    }

    @GetMapping("/{bookingId}/issue/evidence/{evidenceId}/download")
    @Operation(summary = "Lấy URL tải minh chứng", description = "Trả URL private ngắn hạn, sau khi backend kiểm tra người gọi thuộc booking.")
    public ApiResponse<BookingIssueEvidenceDownloadResponse> download(@AuthenticationPrincipal UserPrincipal principal,
                                                                        @PathVariable UUID bookingId, @PathVariable UUID evidenceId) {
        return ApiResponse.success(evidenceService.downloadForParticipant(requireUser(principal), bookingId, evidenceId));
    }

    private UUID requireUser(UserPrincipal principal) {
        if (principal == null) throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        return principal.getPublicId();
    }
}
