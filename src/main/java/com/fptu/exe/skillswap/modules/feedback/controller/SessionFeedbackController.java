package com.fptu.exe.skillswap.modules.feedback.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.feedback.dto.SessionFeedbackResponse;
import com.fptu.exe.skillswap.modules.feedback.dto.SubmitFeedbackRequest;
import com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Session Feedback", description = "API đánh giá (feedback) sau buổi học mentoring")
@SecurityRequirement(name = "bearerAuth")
public class SessionFeedbackController {

    private final SessionFeedbackService sessionFeedbackService;

    @Operation(summary = "Gửi đánh giá (feedback) cho buổi học đã hoàn thành")
    @PostMapping("/{sessionId}/feedback")
    public ResponseEntity<ApiResponse<SessionFeedbackResponse>> submitFeedback(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmitFeedbackRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        SessionFeedbackResponse response = sessionFeedbackService.submitFeedback(
                principal.getPublicId(),
                sessionId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
