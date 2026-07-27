package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFunnelEventRequest;
import com.fptu.exe.skillswap.modules.mentor.service.MentorFunnelTelemetryService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor-discovery")
@RequiredArgsConstructor
@Tag(name = "Mentor Discovery Telemetry", description = "Best-effort funnel telemetry for authenticated mentor discovery flows.")
@SecurityRequirement(name = "bearerAuth")
public class MentorFunnelTelemetryController {

    private final MentorFunnelTelemetryService mentorFunnelTelemetryService;

    @PostMapping("/funnel-events")
    @Operation(summary = "Record a best-effort mentor discovery funnel event")
    public ApiResponse<Void> recordFunnelEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorFunnelEventRequest request
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        mentorFunnelTelemetryService.recordClientEvent(principal.getPublicId(), request);
        return ApiResponse.success(null);
    }
}
