package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorViolationHistoryResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.AdminMentorViolationRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.ReverseMentorViolationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationSource;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorViolationType;
import com.fptu.exe.skillswap.modules.mentor.service.MentorViolationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@Tag(name = "Mentor Violation", description = "Điểm vi phạm nội bộ, không xuất hiện trên mentor discovery hoặc API cho mentee")
@SecurityRequirement(name = "bearerAuth")
public class MentorViolationController {

    private final MentorViolationService mentorViolationService;

    @GetMapping("/api/me/mentor-violations")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor xem điểm vi phạm của chính mình")
    public ApiResponse<MentorViolationHistoryResponse> getMyViolations(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (principal == null) throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        return ApiResponse.success(mentorViolationService.getHistory(principal.getPublicId(), page, size));
    }

    @GetMapping("/api/admin/mentors/{mentorUserId}/violations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Admin xem điểm vi phạm của một mentor")
    public ApiResponse<MentorViolationHistoryResponse> getMentorViolations(
            @PathVariable UUID mentorUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(mentorViolationService.getHistory(mentorUserId, page, size));
    }

    @PostMapping("/api/admin/mentors/{mentorUserId}/violations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Admin ghi nhận vi phạm đã xác minh của mentor")
    public ApiResponse<Void> recordViolation(
            @PathVariable UUID mentorUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminMentorViolationRequest request) {
        if (request.type() == MentorViolationType.LATE_CANCELLATION
                || request.type() == MentorViolationType.COMPLETION_OVERDUE
                || request.type() == MentorViolationType.MENTOR_NO_SHOW
                || request.sourceModule() == MentorViolationSource.BOOKING) {
            throw new BaseException(ErrorCode.BAD_REQUEST,
                    "Vi phạm booking phải được hệ thống ghi nhận từ booking gốc.");
        }
        if (!isTypeAllowedForSource(request.sourceModule(), request.type())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Loại vi phạm không phù hợp với nguồn dữ liệu.");
        }
        mentorViolationService.recordAdminConfirmed(mentorUserId, request.sourceModule(), request.sourceReferenceId(),
                request.type(), request.severity(), principal.getPublicId(), request.reason(), request.decisionNote());
        return ApiResponse.success(null);
    }

    @PostMapping("/api/admin/mentors/{mentorUserId}/violations/{violationId}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Admin đảo một điểm vi phạm đã kết luận sai")
    public ApiResponse<Void> reverseViolation(
            @PathVariable UUID mentorUserId,
            @PathVariable UUID violationId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReverseMentorViolationRequest request) {
        mentorViolationService.reverse(mentorUserId, violationId, principal.getPublicId(), request.reason());
        return ApiResponse.success(null);
    }

    private boolean isTypeAllowedForSource(MentorViolationSource source, MentorViolationType type) {
        return switch (source) {
            case CHAT -> type == MentorViolationType.CHAT_POLICY_BREACH;
            case FORUM -> type == MentorViolationType.FORUM_POLICY_BREACH;
            case VERIFICATION -> type == MentorViolationType.VERIFICATION_FRAUD;
            case ADMIN -> type == MentorViolationType.ADMIN_CONFIRMED_BREACH;
            case BOOKING -> false;
        };
    }
}
