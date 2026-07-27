package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.infrastructure.security.TrustedClientIpResolver;
import com.fptu.exe.skillswap.modules.blog.dto.BlogCategoryResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogFollowResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogEngagementMutationResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderCardResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.BlogTagResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogAuthorCtaClickRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogViewRequest;
import com.fptu.exe.skillswap.modules.blog.service.BlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

@RestController
@RequestMapping("/api/blog")
@RequiredArgsConstructor
@Tag(name = "Blog", description = "Public blog APIs for SEO articles, mentor trust content and knowledge articles.")
public class BlogController {

    private final BlogService blogService;
    private final InMemoryRateLimitService rateLimitService;
    private final TrustedClientIpResolver trustedClientIpResolver;

    @GetMapping("/posts")
    @Operation(
            summary = "List published blog posts",
            description = """
                    Cursor-based public blog list.
                    `cursor` is an opaque string: Frontend must not decode or create it, only pass back `nextCursor`.
                    Visibility is resolved by requester: anonymous sees PUBLIC; authenticated users also see AUTHENTICATED.
                    """
    )
    public ApiResponse<CursorPageResponse<BlogPostReaderCardResponse>> listPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Opaque cursor from previous response nextCursor. Do not decode or modify.")
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(blogService.listPosts(principal, cursor, limit, categoryId, tagId, keyword));
    }

    @GetMapping("/featured")
    @Operation(summary = "List featured blog posts")
    public ApiResponse<List<BlogPostReaderCardResponse>> featured(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "6") Integer limit
    ) {
        return ApiResponse.success(blogService.featured(principal, limit == null ? 6 : limit));
    }

    @GetMapping("/trending")
    @Operation(summary = "List trending blog posts with lightweight cached ranking")
    public ApiResponse<List<BlogPostReaderCardResponse>> trending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ApiResponse.success(blogService.trending(principal, limit == null ? 10 : limit));
    }

    @GetMapping("/posts/{slug}/related")
    @Operation(summary = "List related blog posts by category, tag and audience")
    public ApiResponse<List<BlogPostReaderCardResponse>> related(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug,
            @RequestParam(defaultValue = "6") Integer limit
    ) {
        return ApiResponse.success(blogService.related(principal, slug, limit == null ? 6 : limit));
    }

    @GetMapping("/posts/{slug}/recommendations")
    @Operation(summary = "Deprecated alias for related blog posts", deprecated = true)
    @Deprecated
    public ResponseEntity<ApiResponse<List<BlogPostReaderCardResponse>>> recommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String slug,
            @RequestParam(defaultValue = "6") Integer limit
    ) {
        String successor = "/api/blog/posts/" + slug + "/related";
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header(HttpHeaders.LINK, "<" + successor + ">; rel=\"successor-version\"")
                .body(ApiResponse.success(blogService.recommendations(principal, slug, limit == null ? 6 : limit)));
    }

    @GetMapping("/posts/{slug}")
    @Operation(summary = "Get blog post detail by slug")
    public ApiResponse<BlogPostReaderDetailResponse> getBySlug(
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
            @Valid @RequestBody BlogViewRequest request,
            HttpServletRequest httpRequest
    ) {
        String fingerprint = anonymousFingerprint(httpRequest);
        rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BEST_EFFORT, "blog:view:" + fingerprint, 120, Duration.ofMinutes(1), "Bạn đang gửi quá nhiều lượt xem blog");
        blogService.recordView(principal, postId, request, fingerprint);
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

    @PostMapping("/posts/{postId}/booking-started")
    @Operation(summary = "Record that a user started booking from a blog CTA")
    public ApiResponse<Void> recordBookingStarted(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogAuthorCtaClickRequest request
    ) {
        blogService.recordBookingStarted(principal, postId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/notification-click")
    @Operation(summary = "Record blog notification click event")
    public ApiResponse<Void> recordNotificationClick(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogAuthorCtaClickRequest request
    ) {
        blogService.recordNotificationClick(principal, postId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/recommendation-click")
    @Operation(summary = "Record blog recommendation click event")
    public ApiResponse<Void> recordRecommendationClick(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestBody(required = false) BlogAuthorCtaClickRequest request
    ) {
        blogService.recordRecommendationClick(principal, postId, request);
        return ApiResponse.success(null);
    }

    @PutMapping("/posts/{postId}/like")
    @Operation(summary = "Like a blog post. Idempotent.")
    public ApiResponse<BlogEngagementMutationResponse> like(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        return ApiResponse.success(blogService.like(principal, postId));
    }

    @DeleteMapping("/posts/{postId}/like")
    @Operation(summary = "Remove my like from a blog post. Idempotent.")
    public ApiResponse<BlogEngagementMutationResponse> unlike(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        return ApiResponse.success(blogService.unlike(principal, postId));
    }

    @PutMapping("/posts/{postId}/bookmark")
    @Operation(summary = "Bookmark a blog post. Idempotent.")
    public ApiResponse<BlogEngagementMutationResponse> bookmark(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        return ApiResponse.success(blogService.bookmark(principal, postId));
    }

    @DeleteMapping("/posts/{postId}/bookmark")
    @Operation(summary = "Remove my bookmark from a blog post. Idempotent.")
    public ApiResponse<BlogEngagementMutationResponse> unbookmark(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        return ApiResponse.success(blogService.unbookmark(principal, postId));
    }

    @PutMapping("/categories/{categoryId}/follow")
    @Operation(summary = "Follow a blog category. Idempotent.")
    public ApiResponse<BlogFollowResponse> followCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID categoryId
    ) {
        return ApiResponse.success(blogService.followCategory(principal, categoryId));
    }

    @DeleteMapping("/categories/{categoryId}/follow")
    @Operation(summary = "Unfollow a blog category. Idempotent.")
    public ApiResponse<BlogFollowResponse> unfollowCategory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID categoryId
    ) {
        return ApiResponse.success(blogService.unfollowCategory(principal, categoryId));
    }

    @PutMapping("/mentors/{mentorId}/follow")
    @Operation(summary = "Follow a mentor's articles. Idempotent.")
    public ApiResponse<BlogFollowResponse> followMentor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorId
    ) {
        return ApiResponse.success(blogService.followMentor(principal, mentorId));
    }

    @DeleteMapping("/mentors/{mentorId}/follow")
    @Operation(summary = "Unfollow a mentor's articles. Idempotent.")
    public ApiResponse<BlogFollowResponse> unfollowMentor(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID mentorId
    ) {
        return ApiResponse.success(blogService.unfollowMentor(principal, mentorId));
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

    private String anonymousFingerprint(HttpServletRequest request) {
        String ip = trustedClientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");
        return (ip == null ? "unknown-ip" : ip) + "|" + (userAgent == null ? "unknown-ua" : userAgent);
    }
}
