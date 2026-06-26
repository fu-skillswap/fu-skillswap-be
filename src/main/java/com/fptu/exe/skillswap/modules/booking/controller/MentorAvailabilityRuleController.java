package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
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
@RequestMapping("/api/me/availability-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Availability Rule", description = "API để mentor quản lý availability rules gốc dùng để sinh availability slots.")
@SecurityRequirement(name = "bearerAuth")
public class MentorAvailabilityRuleController {

    private final MentorAvailabilityService mentorAvailabilityService;

    @Operation(
            summary = "Lấy danh sách availability rules của mentor",
            description = "Trả về toàn bộ availability rules đang active của mentor hiện tại. FE dùng để render màn quản lý lịch gốc."
    )
    @GetMapping
    public ApiResponse<List<AvailabilityRuleResponse>> getMyRules(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.getMyRules(principal.getPublicId()));
    }

    @Operation(
            summary = "Tạo availability rule mới",
            description = "Mentor tạo rule OPEN/CLOSED để hệ thống sinh availability slots theo rule này."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo rule thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu rule không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có quyền MENTOR"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Mentor hiện chưa đủ điều kiện cấu hình lịch")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityRuleResponse>> createRule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentorAvailabilityService.createRule(principal.getPublicId(), request)));
    }

    @Operation(
            summary = "Cập nhật availability rule",
            description = "Mentor cập nhật rule lịch gốc hiện có. Hệ thống sẽ reconcile lại future windows theo rule mới."
    )
    @PutMapping("/{ruleId}")
    public ApiResponse<AvailabilityRuleResponse> updateRule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.updateRule(principal.getPublicId(), ruleId, request));
    }

    @Operation(
            summary = "Xóa mềm availability rule",
            description = "Mentor tắt một rule lịch gốc. Các future windows phù hợp sẽ bị deactivate theo business rule hiện tại."
    )
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> deleteRule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
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
