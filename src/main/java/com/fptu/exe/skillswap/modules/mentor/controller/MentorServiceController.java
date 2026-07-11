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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/mentor-services")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mentor Services", description = "Nhóm API để mentor tạo, cập nhật, bật tắt hoặc lưu trữ các dịch vụ mentoring cụ thể. FE dùng nhóm này để quản lý các gói mà mentee sẽ chọn khi booking.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('MENTOR')")
public class MentorServiceController {

    private final MentorServiceManagementService mentorServiceManagementService;

    @Operation(
            summary = "Lấy danh sách mentor services của tôi",
            description = "Trả về danh sách dịch vụ mentoring thuộc về mentor hiện tại. Có thể lọc theo query param `active=true|false|all`, mặc định là `all` để FE quản lý cả service đang bật và đã xóa mềm."
    )
    @GetMapping
    public ApiResponse<List<MentorServiceResponse>> getMyServices(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "all") String active
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.getMyServices(principal.getPublicId(), active));
    }

    @Operation(
            summary = "Lấy chi tiết mentor service của tôi",
            description = "Trả về chi tiết một dịch vụ mentoring thuộc về mentor hiện tại. FE dùng trước khi mở màn sửa dịch vụ hoặc khi cần hiển thị thông tin đầy đủ của một service."
    )
    @GetMapping("/{serviceId}")
    public ApiResponse<MentorServiceResponse> getMyServiceDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.getMyServiceDetail(principal.getPublicId(), serviceId));
    }

    @Operation(
            summary = "Tạo mentor service",
            description = "Tạo một dịch vụ mentoring mới cho mentor hiện tại. Contract hiện tại vẫn nhận title, description, expectedOutcome, durationMinutes, pricing/free flags và help topics của dịch vụ. FE cần bám đúng schema runtime hiện tại thay vì suy luận từ plan phase sau."
    )
    @PostMapping
    public ApiResponse<MentorServiceResponse> createService(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorServiceUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.createService(principal.getPublicId(), request));
    }

    @Operation(
            summary = "Cập nhật mentor service",
            description = "Cập nhật một dịch vụ mentoring hiện có của mentor theo contract runtime hiện tại, bao gồm thông tin mô tả, pricing/free flags và help topics."
    )
    @PutMapping("/{serviceId}")
    public ApiResponse<MentorServiceResponse> updateService(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId,
            @Valid @RequestBody MentorServiceUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.updateService(principal.getPublicId(), serviceId, request));
    }

    @Operation(
            summary = "Đổi trạng thái active của service",
            description = "Bật hoặc tắt một dịch vụ mentoring của mentor hiện tại. FE dùng khi mentor muốn tạm dừng hoặc mở lại dịch vụ mà không xóa hẳn dữ liệu service."
    )
    @PatchMapping("/{serviceId}/active")
    public ApiResponse<MentorServiceResponse> changeActiveStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId,
            @Valid @RequestBody MentorServiceActiveRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorServiceManagementService.changeActiveStatus(principal.getPublicId(), serviceId, request.active()));
    }

    @Operation(
            summary = "Lưu trữ mentor service",
            description = "Xóa mềm một dịch vụ mentoring của mentor hiện tại. FE dùng khi mentor không muốn cung cấp dịch vụ đó nữa nhưng backend vẫn cần giữ dữ liệu lịch sử liên quan."
    )
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
