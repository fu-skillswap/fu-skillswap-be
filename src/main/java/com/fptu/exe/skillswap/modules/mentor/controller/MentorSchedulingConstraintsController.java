package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSchedulingConstraintsResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/mentor-scheduling-constraints")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor Availability Slot", description = "Xem slot rảnh và các giới hạn thời gian áp dụng khi mentor tạo lịch.")
@SecurityRequirement(name = "bearerAuth")
public class MentorSchedulingConstraintsController {

    private final MentorBookingPolicyService mentorBookingPolicyService;

    @GetMapping
    @Operation(summary = "Xem giới hạn thời gian đặt lịch", description = "Trả về các giới hạn chung của hệ thống để FE hiển thị đúng khi mentor tạo hoặc cập nhật slot rảnh.")
    public ApiResponse<MentorSchedulingConstraintsResponse> getConstraints(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return ApiResponse.success(mentorBookingPolicyService.getSchedulingConstraints());
    }
}
