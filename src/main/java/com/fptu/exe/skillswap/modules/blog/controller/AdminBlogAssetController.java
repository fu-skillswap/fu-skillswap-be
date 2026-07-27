package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse;
import com.fptu.exe.skillswap.modules.filestorage.service.PublicAssetUploadService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/blog/assets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminBlogAssetController {
    private final PublicAssetUploadService publicAssetUploadService;
    @PostMapping("/upload-intents")
    public ApiResponse<PublicAssetUploadIntentResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody PublicAssetUploadIntentRequest request) {
        return ApiResponse.created(publicAssetUploadService.createBlogImageIntent(principal.getPublicId(), request));
    }
    @PostMapping("/{intentId}/confirm")
    public ApiResponse<PublicAssetResponse> confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID intentId) {
        return ApiResponse.success(publicAssetUploadService.confirmBlogImage(principal.getPublicId(), intentId));
    }
}
