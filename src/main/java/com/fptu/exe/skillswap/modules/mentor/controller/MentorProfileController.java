package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorProfileUpsertRequest;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/mentor-profile")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mentor Profile", description = "Nhóm API tạo và duy trì hồ sơ mentor nền tảng, quyết định mentor đã đủ dữ liệu để verification hoặc xuất hiện trên discovery hay chưa. FE dùng trước khi user nộp mentor verification hoặc trước khi hiển thị mentor public.")
@SecurityRequirement(name = "bearerAuth")
/**
 * Authorization design decision:
 *
 * <p>Any authenticated user (including new mentees) may GET and PUT the mentor profile.
 * This is intentional: the mentor profile is a prerequisite for the verification wizard
 * and must be fillable before the user has the MENTOR role. Having a profile record does
 * NOT grant any mentor privileges — only a successfully approved verification request does.
 *
 * <p>ADMIN and SYSTEM_ADMIN are blocked from managing mentor profiles (same reason as
 * MentorVerificationController: conflict-of-interest and audit integrity).
 *
 * <p>This is NOT a security risk because:
 * 1. The profile data itself is non-privileged (headline, bio, teaching mode, etc.).
 * 2. Downstream mentor-only operations (accept booking, manage services) still require
 *    the MENTOR role enforced at their own controllers/services.
 */
@PreAuthorize("!hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')")
public class MentorProfileController {

    private final MentorProfileService mentorProfileService;

    @Operation(summary = "Lấy hồ sơ mentor của tôi", description = "Trả về hồ sơ mentor hiện tại của user đã đăng nhập. FE dùng trong màn onboarding mentor hoặc settings; nếu hồ sơ chưa tồn tại thì response vẫn trả `data.exists=false` để FE hiển thị flow tạo mới thay vì coi đó là lỗi.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hồ sơ mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<MentorProfileResponse> getMyProfile(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileService.getMyProfile(principal.getPublicId()));
    }

    @Operation(
            summary = "Lưu hồ sơ mentor của tôi",
            description = "Tạo mới hoặc cập nhật hồ sơ mentor của user hiện tại. FE dùng trước khi user nộp mentor verification và trước khi mentor đủ điều kiện xuất hiện trên discovery để booking. Request này chứa các field hồ sơ mentor như headline, expertise, help topics, teaching mode, session duration, phone number và trạng thái sẵn sàng nhận mentee."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu hồ sơ mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PutMapping
    public ApiResponse<MentorProfileResponse> upsertProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorProfileUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileService.upsertProfile(principal.getPublicId(), request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
