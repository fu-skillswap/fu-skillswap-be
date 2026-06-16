package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mentor/availability-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Availability", description = "Các API để mentor cấu hình lịch rảnh kiểu calendar")
@SecurityRequirement(name = "bearerAuth")
public class MentorAvailabilityController {

    private final MentorAvailabilityService mentorAvailabilityService;

    @Operation(summary = "Xem các rule lịch rảnh hiện tại của mentor")
    @GetMapping
    public ApiResponse<List<AvailabilityRuleResponse>> getMyAvailabilityRules(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.getMyRules(principal.getPublicId()));
    }

    @Operation(summary = "Tạo rule lịch rảnh hoặc rule đóng lịch theo ngày/khoảng ngày")
    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityRuleResponse>> createAvailabilityRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        AvailabilityRuleResponse response = mentorAvailabilityService.createRule(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "Cập nhật rule lịch rảnh hoặc rule đóng lịch")
    @PutMapping("/{ruleId}")
    public ApiResponse<AvailabilityRuleResponse> updateAvailabilityRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.updateRule(principal.getPublicId(), ruleId, request));
    }

    @Operation(summary = "Tắt rule lịch rảnh. Các booking đã đặt trước đó vẫn được giữ nguyên")
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> deleteAvailabilityRule(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId
    ) {
        ensureAuthenticated(principal);
        mentorAvailabilityService.deleteRule(principal.getPublicId(), ruleId);
        return ApiResponse.success(null);
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
