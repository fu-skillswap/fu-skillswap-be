package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityRuleResponse;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
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
@RequestMapping("/api/mentor/availability-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Availability", description = "Các API để mentor cấu hình rule lịch rảnh kiểu calendar. Rule sinh slot, còn booking request chỉ xếp hàng vào slot sinh ra.")
@SecurityRequirement(name = "bearerAuth")
public class MentorAvailabilityController {

    private final MentorAvailabilityService mentorAvailabilityService;

    @Operation(
            summary = "Xem các rule lịch rảnh hiện tại của mentor",
            description = "Trả về toàn bộ availability rule đang thuộc mentor hiện tại, bao gồm rule mở lịch và rule đóng lịch."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy danh sách rule thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có role MENTOR")
    })
    @GetMapping
    public ApiResponse<List<AvailabilityRuleResponse>> getMyAvailabilityRules(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.getMyRules(principal.getPublicId()));
    }

    @Operation(
            summary = "Tạo availability rule",
            description = """
                    Tạo rule mở lịch hoặc đóng lịch theo ngày, khoảng ngày hoặc lặp theo thứ trong tuần.
                    Lưu ý:
                    - Rule chỉ định nghĩa nguồn sinh slot, không phải booking thật.
                    - Các slot đã có booking trước đó sẽ không bị mất chỉ vì mentor thay đổi rule sau này.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo rule thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Rule không hợp lệ, ví dụ thiếu thời gian hoặc khoảng ngày sai"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có role MENTOR"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Rule trùng hoặc chồng lấn với dữ liệu hiện tại theo business rule backend")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AvailabilityRuleResponse>> createAvailabilityRule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        AvailabilityRuleResponse response = mentorAvailabilityService.createRule(principal.getPublicId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(
            summary = "Cập nhật availability rule",
            description = "Cập nhật một rule hiện có của mentor. Chỉ rule thuộc mentor hiện tại mới được sửa."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật rule thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Rule mới không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có role MENTOR hoặc rule không thuộc về mình"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy rule"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Rule sau cập nhật gây xung đột với dữ liệu hiện tại")
    })
    @PutMapping("/{ruleId}")
    public ApiResponse<AvailabilityRuleResponse> updateAvailabilityRule(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpsertAvailabilityRuleRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorAvailabilityService.updateRule(principal.getPublicId(), ruleId, request));
    }

    @Operation(
            summary = "Tắt availability rule",
            description = "Xóa mềm một rule lịch rảnh. Các booking đã tạo hoặc đã được accept trước đó vẫn được giữ nguyên."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tắt rule thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người gọi không có role MENTOR hoặc rule không thuộc về mình"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy rule")
    })
    @DeleteMapping("/{ruleId}")
    public ApiResponse<Void> deleteAvailabilityRule(
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
