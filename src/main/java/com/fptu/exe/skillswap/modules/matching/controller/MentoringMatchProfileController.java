package com.fptu.exe.skillswap.modules.matching.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.matching.dto.request.MentoringMatchProfileSubmitRequest;
import com.fptu.exe.skillswap.modules.matching.dto.response.MentoringMatchProfileResponse;
import com.fptu.exe.skillswap.modules.matching.dto.response.MentoringQuestionnaireResponse;
import com.fptu.exe.skillswap.modules.matching.service.MentoringMatchProfileService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/matching-profile")
@RequiredArgsConstructor
@Tag(name = "Mentee Matching Profile", description = "API nhu cầu mentoring của mentee dùng cho Smart Matching.")
@SecurityRequirement(name = "bearerAuth")
public class MentoringMatchProfileController {

    private final MentoringMatchProfileService mentoringMatchProfileService;

    @Operation(summary = "Lấy trạng thái nhu cầu mentoring của tôi")
    @GetMapping
    public ApiResponse<MentoringMatchProfileResponse> getMyMatchingProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentoringMatchProfileService.getMyMatchingProfile(principal.getPublicId()));
    }

    @Operation(summary = "Lấy 5 câu hỏi nhu cầu mentoring đang active")
    @GetMapping("/questionnaire")
    public ApiResponse<MentoringQuestionnaireResponse> getQuestionnaire(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentoringMatchProfileService.getActiveQuestionnaire());
    }

    @Operation(summary = "Lưu 5 câu trả lời nhu cầu mentoring")
    @PutMapping
    public ApiResponse<MentoringMatchProfileResponse> submitMatchingProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentoringMatchProfileSubmitRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentoringMatchProfileService.submitMyMatchingProfile(principal.getPublicId(), request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
