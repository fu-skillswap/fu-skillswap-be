package com.fptu.exe.skillswap.modules.matching.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireActivateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireVersionCreateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.response.AdminQuestionnaireActivationResponse;
import com.fptu.exe.skillswap.modules.matching.dto.response.AdminQuestionnaireVersionSummaryResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mentoring-questionnaire")
@RequiredArgsConstructor
@Tag(name = "Admin - Mentoring Questionnaire", description = "Admin quản lý version và activation của bộ câu hỏi nhu cầu mentoring.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
public class AdminMentoringQuestionnaireController {

    private final MentoringMatchProfileService mentoringMatchProfileService;

    @Operation(summary = "Lấy danh sách questionnaire versions")
    @GetMapping("/versions")
    public ApiResponse<List<AdminQuestionnaireVersionSummaryResponse>> listVersions() {
        return ApiResponse.success(mentoringMatchProfileService.listVersions());
    }

    @Operation(summary = "Lấy chi tiết questionnaire version")
    @GetMapping("/versions/{versionId}")
    public ApiResponse<MentoringQuestionnaireResponse> getVersion(@PathVariable UUID versionId) {
        return ApiResponse.success(mentoringMatchProfileService.getVersion(versionId));
    }

    @Operation(summary = "Tạo questionnaire version mới")
    @PostMapping("/versions")
    public ResponseEntity<ApiResponse<MentoringQuestionnaireResponse>> createVersion(
            @Valid @RequestBody(required = false) AdminQuestionnaireVersionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(mentoringMatchProfileService.createVersion(request)));
    }

    @Operation(summary = "Activate questionnaire version")
    @PostMapping("/activate")
    public ApiResponse<AdminQuestionnaireActivationResponse> activateVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminQuestionnaireActivateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentoringMatchProfileService.activateVersion(principal.getPublicId(), request));
    }

    @Operation(summary = "Lấy activation hiện tại")
    @GetMapping("/active")
    public ApiResponse<AdminQuestionnaireActivationResponse> getActive() {
        return ApiResponse.success(mentoringMatchProfileService.getActiveActivation());
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
