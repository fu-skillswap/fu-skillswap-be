package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogAuthorCtaClickRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogViewRequest;
import com.fptu.exe.skillswap.modules.blog.service.BlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
@Tag(name = "Blog", description = "Public blog APIs for SEO articles, mentor trust content and knowledge articles.")
public class BlogController {

    private final BlogService blogService;

    @GetMapping("/posts")
    @Operation(
            summary = "List published blog posts",
            description = """
                    Cursor-based public blog list.
                    `cursor` is an opaque string: Frontend must not decode or create it, only pass back `nextCursor`.
                    Visibility is resolved by requester: anonymous sees PUBLIC, authenticated sees PUBLIC + MEMBERS_ONLY,
                    active mentors also see MENTOR_ONLY.
                    """
    )
    public ApiResponse<CursorPageResponse<BlogPostCardResponse>> listPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) BlogAudienceType audienceType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(blogService.listPosts(principal, cursor, limit, categoryId, tagId, audienceType, keyword));
    }

    @GetMapping("/featured")
    @Operation(summary = "List featured blog posts")
    public ApiResponse<List<BlogPostCardResponse>> featured(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "6") Integer limit
    ) {
        return ApiResponse.success(blogService.featured(principal, limit == null ? 6 : limit));
    }

    @GetMapping("/posts/{slug}")
    @Operation(summary = "Get blog post detail by slug")
    public ApiResponse<BlogPostDetailResponse> getBySlug(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug
    ) {
        return ApiResponse.success(blogService.getBySlug(principal, slug));
    }

    @PostMapping("/posts/{postId}/view")
    @Operation(summary = "Record a deduplicated blog view event")
    public ApiResponse<Void> recordView(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogViewRequest request
    ) {
        blogService.recordView(principal, postId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/author-cta-click")
    @Operation(summary = "Record blog author CTA click event")
    public ApiResponse<Void> recordAuthorCtaClick(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogAuthorCtaClickRequest request
    ) {
        blogService.recordAuthorCtaClick(principal, postId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/categories")
    @Operation(summary = "List active blog categories")
    public ApiResponse<List<BlogCategoryResponse>> categories() {
        return ApiResponse.success(blogService.categories());
    }

    @GetMapping("/tags")
    @Operation(summary = "List active blog tags")
    public ApiResponse<List<BlogTagResponse>> tags() {
        return ApiResponse.success(blogService.tags());
    }
}
