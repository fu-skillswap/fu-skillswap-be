package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse;
import com.fptu.exe.skillswap.modules.blog.service.MentorBlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/blog/assets")
@RequiredArgsConstructor
@Tag(name = "Blog")
@SecurityRequirement(name = "bearerAuth")
public class BlogAssetController {
    private final MentorBlogService mentorBlogService;
    @PostMapping("/upload-intents")
    @Operation(summary = "Create mentor blog image upload intent", description = "Creates a purpose-scoped public BLOG_IMAGE upload intent. The client never chooses an object key.")
    public ApiResponse<PublicAssetUploadIntentResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody PublicAssetUploadIntentRequest request) {
        return ApiResponse.created(mentorBlogService.createImageUploadIntent(principal.getPublicId(), request));
    }
    @PostMapping("/{intentId}/confirm")
    @Operation(summary = "Confirm mentor blog image upload", description = "Verifies the uploaded object and returns a confirmed public asset for cover or inline Markdown use.")
    public ApiResponse<PublicAssetResponse> confirm(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID intentId) {
        return ApiResponse.success(mentorBlogService.confirmImageUpload(principal.getPublicId(), intentId));
    }
}
