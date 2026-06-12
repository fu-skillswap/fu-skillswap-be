package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileBasicRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileExpertiseRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorProfileResponse;
import com.fptu.exe.skillswap.modules.mentor.service.MentorProfileService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Hồ sơ mentor", description = "Quản lý onboarding hồ sơ mentor của người dùng đang đăng nhập")
@SecurityRequirement(name = "bearerAuth")
public class MentorProfileController {

    private final MentorProfileService mentorProfileService;

    @Operation(summary = "Xem hồ sơ mentor của tôi", description = "Trả về hồ sơ mentor hiện tại. Nếu chưa tạo, data.exists=false để FE hiển thị onboarding.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy hồ sơ mentor thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @GetMapping
    public ApiResponse<MentorProfileResponse> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileService.getMyProfile(principal.getPublicId()));
    }

    @Operation(summary = "Tạo hoặc cập nhật thông tin cơ bản của mentor", description = "Step 1: lưu headline, vị trí, công ty, avatar, bio và trạng thái sẵn sàng mentoring.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu thông tin cơ bản thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PutMapping("/basic")
    public ApiResponse<MentorProfileResponse> upsertBasic(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorProfileBasicRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileService.upsertBasic(principal.getPublicId(), request));
    }

    @Operation(summary = "Tạo hoặc cập nhật chuyên môn của mentor", description = "Step 2: lưu tag chuyên môn, help topic, số năm kinh nghiệm, ngành nghề và các link nghề nghiệp.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lưu chuyên môn thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tag không tồn tại, sai loại hoặc dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PutMapping("/expertise")
    public ApiResponse<MentorProfileResponse> upsertExpertise(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MentorProfileExpertiseRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(mentorProfileService.upsertExpertise(principal.getPublicId(), request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
