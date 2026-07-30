package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.AdminBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogPostCreateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogPostUpdateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminMentorBlogModerationRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.AdminBlogTagWriteRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogCategoryUpsertRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogFeatureRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogExpectedVersionRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

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
    public ApiResponse<CursorPageResponse<AdminBlogPostCardResponse>> listPosts(
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) BlogPostStatus status,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean deleted
    ) {
        return ApiResponse.success(adminBlogService.listPosts(cursor, limit, status, authorUserId, categoryId, tagId, keyword, deleted));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Admin get blog post detail")
    public ApiResponse<AdminBlogPostDetailResponse> getPost(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.getPost(postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "Create draft blog post")
    public ApiResponse<AdminBlogPostDetailResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AdminBlogPostCreateRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(adminBlogService.createPost(principal.getPublicId(), request));
    }

    @PutMapping("/posts/{postId}")
    @Operation(summary = "Update blog post")
    public ApiResponse<AdminBlogPostDetailResponse> updatePost(
            @PathVariable UUID postId,
            @Valid @RequestBody AdminBlogPostUpdateRequest request
    ) {
        return ApiResponse.success(adminBlogService.updatePost(postId, request));
    }

    @PatchMapping("/posts/{postId}/moderation")
    @Operation(summary = "Moderate mentor article metadata without editing authored content")
    public ApiResponse<AdminBlogPostDetailResponse> moderateMentorPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody AdminMentorBlogModerationRequest request
    ) {
        return ApiResponse.success(adminBlogService.moderateMentorPost(principal.getPublicId(), postId, request));
    }

    @PostMapping("/posts/{postId}/publish")
    @Operation(summary = "Publish blog post and lock slug")
    public ApiResponse<AdminBlogPostDetailResponse> publish(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.publish(postId));
    }

    @PostMapping("/posts/{postId}/archive")
    @Operation(summary = "Archive blog post")
    public ApiResponse<AdminBlogPostDetailResponse> archive(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.archive(postId));
    }

    @PostMapping("/posts/{postId}/feature")
    @Operation(summary = "Mark blog post as featured")
    public ApiResponse<AdminBlogPostDetailResponse> feature(
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogFeatureRequest request
    ) {
        return ApiResponse.success(adminBlogService.feature(postId, request));
    }

    @PostMapping("/posts/{postId}/unfeature")
    @Operation(summary = "Remove featured state from blog post")
    public ApiResponse<AdminBlogPostDetailResponse> unfeature(@PathVariable UUID postId) {
        return ApiResponse.success(adminBlogService.unfeature(postId));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Soft delete blog post")
    public ApiResponse<AdminBlogPostDetailResponse> deletePost(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request) {
        return ApiResponse.success(adminBlogService.deletePost(principal.getPublicId(), postId, request));
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "Restore a deleted post as archived")
    public ApiResponse<AdminBlogPostDetailResponse> restorePost(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request
    ) {
        return ApiResponse.success(adminBlogService.restorePost(principal.getPublicId(), postId, request));
    }

    @GetMapping("/categories")
    @Operation(summary = "List admin blog categories")
    public ApiResponse<List<BlogCategoryResponse>> categories() {
        return ApiResponse.success(adminBlogService.categories());
    }

    @PutMapping("/categories")
    @Operation(summary = "Create or update blog category")
    public ResponseEntity<ApiResponse<BlogCategoryResponse>> upsertCategory(@Valid @RequestBody BlogCategoryUpsertRequest request) {
        AdminBlogService.CategoryUpsertResult result = adminBlogService.upsertCategory(request);
        return result.created()
                ? ResponseEntity.status(201).body(ApiResponse.created(result.response()))
                : ResponseEntity.ok(ApiResponse.success(result.response()));
    }

    @GetMapping("/tags")
    @Operation(summary = "List admin blog tags")
    public ApiResponse<List<BlogTagResponse>> tags() {
        return ApiResponse.success(adminBlogService.tags());
    }

    @PostMapping("/tags")
    @Operation(summary = "Create a blog tag")
    public ApiResponse<BlogTagResponse> createTag(@Valid @RequestBody AdminBlogTagWriteRequest request) {
        return ApiResponse.created(adminBlogService.createTag(request));
    }

    @PutMapping("/tags/{tagId}")
    @Operation(summary = "Update or deactivate a blog tag")
    public ApiResponse<BlogTagResponse> updateTag(
            @PathVariable UUID tagId,
            @Valid @RequestBody AdminBlogTagWriteRequest request
    ) {
        return ApiResponse.success(adminBlogService.updateTag(tagId, request));
    }

    @Deprecated
    @PutMapping("/tags")
    @Operation(summary = "Deprecated legacy blog tag upsert", deprecated = true)
    public ResponseEntity<ApiResponse<BlogTagResponse>> upsertLegacyTag(@Valid @RequestBody BlogTagUpsertRequest request) {
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Link", "</api/admin/blog/tags>; rel=\"successor-version\"")
                .body(ApiResponse.success(adminBlogService.upsertLegacyTag(request)));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
