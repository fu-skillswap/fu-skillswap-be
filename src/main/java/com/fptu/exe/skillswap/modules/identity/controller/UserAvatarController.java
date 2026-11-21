package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.identity.dto.request.UserAvatarConfirmRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserAvatarResponse;
import com.fptu.exe.skillswap.modules.identity.service.UserAvatarService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/avatar")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Account Management", description = "Quản lý ảnh đại diện và tài khoản cá nhân của người dùng.")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    @Operation(
            summary = "Tạo upload intent cho ảnh đại diện",
            description = "Tạo URL upload tạm thời (15 phút) để tải ảnh đại diện (PNG, JPEG, WebP; tối đa 5 MiB) lên storage."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo upload intent thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tên file không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Định dạng ảnh không được hỗ trợ")
    })
    @PostMapping("/upload-intents")
    public ResponseEntity<ApiResponse<PublicAssetUploadPort.UploadIntent>> createUploadIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PublicAssetUploadPort.UploadRequest request
    ) {
        ensureAuthenticated(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userAvatarService.createUploadIntent(principal.getPublicId(), request)));
    }

    @Operation(
            summary = "Xác nhận và cập nhật ảnh đại diện mới",
            description = "Xác nhận file ảnh đã upload lên storage, kiểm tra dung lượng (tối đa 5 MiB) và cập nhật avatar hiện tại cho tài khoản."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật ảnh đại diện thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Upload intent hết hạn hoặc không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy upload intent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Ảnh đại diện vượt quá 5 MiB")
    })
    @PostMapping("/confirm")
    public ApiResponse<UserAvatarResponse> confirmAvatar(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserAvatarConfirmRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(userAvatarService.confirmAndUpdateAvatar(
                principal.getPublicId(), request.uploadIntentId()));
    }

    @Operation(
            summary = "Gỡ ảnh đại diện hiện tại",
            description = "Gỡ ảnh đại diện đã tải lên và khôi phục về ảnh đại diện của tài khoản mail/Google nếu có."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Gỡ ảnh đại diện thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @DeleteMapping
    public ApiResponse<UserAvatarResponse> removeAvatar(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(userAvatarService.removeAvatar(principal.getPublicId()));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
