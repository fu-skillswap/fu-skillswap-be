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
@Tag(name = "Mentor Availability", description = "Nhóm API để mentor quản lý availability rules và để mentee đọc các slot còn hiển thị có thể booking. FE dùng sau khi mentor đã có service và trước khi tạo booking request.")
@SecurityRequirement(name = "bearerAuth")
public class MentorAvailabilityController {

    private final MentorAvailabilityService mentorAvailabilityService;

    @Operation(
            summary = "Lấy danh sách availability rules của tôi",
            description = "Trả về các availability rules thuộc về mentor hiện tại, bao gồm cả rule mở lịch và rule đóng lịch. FE dùng ở màn quản lý lịch của mentor trước khi sửa hoặc tạo rule mới."
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
                    Tạo một availability rule kiểu OPEN hoặc CLOSED cho mentor hiện tại.
                    FE dùng để khai báo khi nào hệ thống nên sinh ra các slot hiển thị cho booking trong tương lai.
                    Availability rule chỉ định nghĩa logic sinh slot chứ không tạo booking thật, và việc đổi rule không xóa các booking đã tồn tại.
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
            description = "Cập nhật một availability rule hiện có của mentor hiện tại. FE dùng khi mentor muốn thay đổi logic lịch trong tương lai như ngày hiệu lực, kiểu lặp hoặc service gắn với rule."
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
            description = "Tắt mềm một availability rule của mentor hiện tại. FE dùng khi mentor không muốn tiếp tục sinh slot từ rule đó nhưng vẫn cần giữ nguyên dữ liệu booking lịch sử."
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
