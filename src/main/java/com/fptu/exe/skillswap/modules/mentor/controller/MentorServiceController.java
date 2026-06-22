package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceActiveRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorServiceResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorServiceUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.service.MentorServiceManagementService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor-services")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mentor Profile", description = "Mentor portfolio management (available slots, headline, expertise description, service offerings)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('MENTOR')")
public class MentorServiceController {

    private final MentorServiceManagementService mentorServiceManagementService;

    @Operation(summary = "Xem danh sách dịch vụ mentoring của tôi")
    @GetMapping
    public ApiResponse<List<MentorServiceResponse>> getMyServices(@AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.getMyServices(principal.getPublicId()));
    }

    @Operation(summary = "Xem chi tiết một dịch vụ mentoring của tôi")
    @GetMapping("/{serviceId}")
    public ApiResponse<MentorServiceResponse> getMyServiceDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.getMyServiceDetail(principal.getPublicId(), serviceId));
    }

    @Operation(summary = "Tạo dịch vụ mentoring mới")
    @PostMapping
    public ApiResponse<MentorServiceResponse> createService(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorServiceUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.createService(principal.getPublicId(), request));
    }

    @Operation(summary = "Cập nhật dịch vụ mentoring")
    @PutMapping("/{serviceId}")
    public ApiResponse<MentorServiceResponse> updateService(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId,
            @Valid @RequestBody MentorServiceUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.updateService(principal.getPublicId(), serviceId, request));
    }

    @Operation(summary = "Bật hoặc tắt dịch vụ mentoring")
    @PatchMapping("/{serviceId}/active")
    public ApiResponse<MentorServiceResponse> changeActiveStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId,
            @Valid @RequestBody MentorServiceActiveRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.changeActiveStatus(principal.getPublicId(), serviceId, request.active()));
    }

    @Operation(summary = "Xóa mềm dịch vụ mentoring")
    @DeleteMapping("/{serviceId}")
    public ApiResponse<MentorServiceResponse> deleteService(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.deleteService(principal.getPublicId(), serviceId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
