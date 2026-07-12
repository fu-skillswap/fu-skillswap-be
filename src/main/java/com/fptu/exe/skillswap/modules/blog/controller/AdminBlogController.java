package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogCategoryUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogFeatureRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogPostUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogTagUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.service.AdminBlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/admin/blog")
@RequiredArgsConstructor
@Tag(name = "Admin - Blog", description = "Admin APIs for SkillSwap blog content management.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM_ADMIN')")
public class AdminBlogController {

    private final AdminBlogService adminBlogService;

    @GetMapping("/posts")
    @Operation(summary = "Admin list blog posts with cursor pagination")
    public ApiResponse<CursorPageResponse<BlogPostCardResponse>> listPosts(
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) BlogPostStatus status,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminBlogService.listPosts(cursor, limit, status, authorUserId, categoryId, tagId, keyword));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Admin get blog post detail")
    public ApiResponse<BlogPostDetailResponse> getPost(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.getPost(postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "Create draft blog post")
    public ApiResponse<BlogPostDetailResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BlogPostUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(adminBlogService.createPost(principal.getPublicId(), request));
    }

    @PutMapping("/posts/{postId}")
    @Operation(summary = "Update blog post")
    public ApiResponse<BlogPostDetailResponse> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody BlogPostUpsertRequest request
    ) {
        return ApiResponse.success(adminBlogService.updatePost(postId, request));
    }

    @PostMapping("/posts/{postId}/publish")
    @Operation(summary = "Publish blog post and lock slug")
    public ApiResponse<BlogPostDetailResponse> publish(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.publish(postId));
    }

    @PostMapping("/posts/{postId}/archive")
    @Operation(summary = "Archive blog post")
    public ApiResponse<BlogPostDetailResponse> archive(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.archive(postId));
    }

    @PostMapping("/posts/{postId}/feature")
    @Operation(summary = "Mark blog post as featured")
    public ApiResponse<BlogPostDetailResponse> feature(
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogFeatureRequest request
    ) {
        return ApiResponse.success(adminBlogService.feature(postId, request));
    }

    @PostMapping("/posts/{postId}/unfeature")
    @Operation(summary = "Remove featured state from blog post")
    public ApiResponse<BlogPostDetailResponse> unfeature(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.unfeature(postId));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Soft delete blog post")
    public ApiResponse<Void> deletePost(@PathVariable UUID postId) {
        adminBlogService.deletePost(postId);
        return ApiResponse.success(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<BlogCategoryResponse>> categories() {
        return ApiResponse.success(adminBlogService.categories());
    }

    @PutMapping("/categories")
    public ApiResponse<BlogCategoryResponse> upsertCategory(@Valid @RequestBody BlogCategoryUpsertRequest request) {
        return ApiResponse.success(adminBlogService.upsertCategory(request));
    }

    @GetMapping("/tags")
    public ApiResponse<List<BlogTagResponse>> tags() {
        return ApiResponse.success(adminBlogService.tags());
    }

    @PutMapping("/tags")
    public ApiResponse<BlogTagResponse> upsertTag(@Valid @RequestBody BlogTagUpsertRequest request) {
        return ApiResponse.success(adminBlogService.upsertTag(request));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
