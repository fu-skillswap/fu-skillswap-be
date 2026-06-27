package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.identity.dto.response.OnboardingStatusResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.modules.mentor.service.MentorVerificationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/onboarding-status")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "API truy vấn trạng thái onboarding tổng hợp của user hiện tại.")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingStatusController {

    private final AcademicService academicService;
    private final MentorProfileService mentorProfileService;
    private final MentorVerificationService mentorVerificationService;

    @GetMapping
    @Operation(
            summary = "Lấy trạng thái onboarding của tôi",
            description = "Trả về trạng thái hoàn thành student profile, mentor profile, verification status và gợi ý action tiếp theo cho FE điều hướng."
    )
    public ApiResponse<OnboardingStatusResponse> getOnboardingStatus(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        UUID userId = principal.getPublicId();
        boolean studentProfileCompleted = academicService.hasCompletedStudentProfile(userId);
        boolean mentorProfileCompleted = mentorProfileService.hasCompletedMentorProfile(userId);
        String verificationStatus = mentorVerificationService.getLatestVerificationStatus(userId);
        List<RoleCode> roles = principal.getRoles();

        String nextAction;
        if (!studentProfileCompleted) {
            nextAction = "COMPLETE_STUDENT_PROFILE";
        } else if (roles.contains(RoleCode.MENTOR)) {
            nextAction = "EXPLORE";
        } else {
            switch (verificationStatus) {
                case "PENDING_REVIEW":
                    nextAction = "WAIT_FOR_APPROVE";
                    break;
                case "NEEDS_REVISION":
                    nextAction = "REVISE_MENTOR_VERIFICATION";
                    break;
                case "APPROVED":
                    nextAction = "EXPLORE";
                    break;
                case "REJECTED":
                case "NOT_STARTED":
                case "WITHDRAWN":
                default:
                    if (!mentorProfileCompleted) {
                        nextAction = "COMPLETE_MENTOR_PROFILE_OR_EXPLORE";
                    } else {
                        nextAction = "SUBMIT_MENTOR_VERIFICATION_OR_EXPLORE";
                    }
                    break;
            }
        }

        OnboardingStatusResponse response = OnboardingStatusResponse.builder()
                .studentProfileCompleted(studentProfileCompleted)
                .mentorProfileCompleted(mentorProfileCompleted)
                .mentorVerificationStatus(verificationStatus)
                .roles(roles)
                .nextRecommendedAction(nextAction)
                .build();

        return ApiResponse.success(response);
    }
}
