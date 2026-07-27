package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.UpdateMentorBookingPolicyRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorBookingPolicyResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/mentor-booking-policy")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking Policy")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('MENTOR')")
public class MentorBookingPolicyController {

    private final MentorBookingPolicyService mentorBookingPolicyService;

    @GetMapping
    public ApiResponse<MentorBookingPolicyResponse> getPolicy(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(mentorBookingPolicyService.getPolicy(requirePrincipal(principal)));
    }

    @PatchMapping
    public ApiResponse<MentorBookingPolicyResponse> updatePolicy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateMentorBookingPolicyRequest request
    ) {
        return ApiResponse.success(mentorBookingPolicyService.updatePolicy(requirePrincipal(principal), request));
    }

    private java.util.UUID requirePrincipal(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return principal.getPublicId();
    }
}
