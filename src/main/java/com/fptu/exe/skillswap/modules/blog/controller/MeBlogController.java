package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.dto.BlogFollowResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostCardResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/blog")
@RequiredArgsConstructor
@Tag(name = "Blog", description = "Authenticated blog APIs for current user.")
@SecurityRequirement(name = "bearerAuth")
public class MeBlogController {

    private final BlogService blogService;

    @GetMapping("/bookmarks")
    @Operation(summary = "List my bookmarked blog posts with cursor pagination")
    public ApiResponse<CursorPageResponse<BlogPostCardResponse>> myBookmarks(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(blogService.myBookmarks(principal, cursor, limit));
    }

    @GetMapping("/follows")
    @Operation(summary = "List categories and tags I follow")
    public ApiResponse<BlogFollowResponse> myFollows(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(blogService.myFollows(principal));
    }

    @GetMapping("/feed")
    @Operation(
            summary = "Personalized blog feed",
            description = "`cursor` is an opaque string. The feed uses followed category/tag first and falls back to latest accessible posts if the user has no follows."
    )
    public ApiResponse<CursorPageResponse<BlogPostCardResponse>> feed(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(blogService.personalizedFeed(principal, cursor, limit));
    }
}
