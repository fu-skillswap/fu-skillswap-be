package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.dto.MentorBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogExpectedVersionRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostCreateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostUpdateRequest;
import com.fptu.exe.skillswap.modules.blog.service.MentorBlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/blog/posts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Blog")
public class MentorBlogController {
    private final MentorBlogService mentorBlogService;
    @GetMapping @Operation(summary = "List my mentor blog posts") public ApiResponse<List<MentorBlogPostDetailResponse>> list(@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.success(mentorBlogService.list(principal.getPublicId())); }
    @PostMapping @Operation(summary = "Create mentor blog draft") public ApiResponse<MentorBlogPostDetailResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody MentorBlogPostCreateRequest request) { return ApiResponse.created(mentorBlogService.create(principal.getPublicId(), request)); }
    @GetMapping("/{postId}") @Operation(summary = "Get my mentor blog post") public ApiResponse<MentorBlogPostDetailResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId) { return ApiResponse.success(mentorBlogService.get(principal.getPublicId(), postId)); }
    @PutMapping("/{postId}") @Operation(summary = "Update mentor blog draft", description = "Requires the current expected version to avoid overwriting a newer author change.") public ApiResponse<MentorBlogPostDetailResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody MentorBlogPostUpdateRequest request) { return ApiResponse.success(mentorBlogService.update(principal.getPublicId(), postId, request)); }
    @PostMapping("/{postId}/publish") @Operation(summary = "Publish mentor blog post", description = "Requires expected version and locks the published slug.") public ApiResponse<MentorBlogPostDetailResponse> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request) { return ApiResponse.success(mentorBlogService.publish(principal.getPublicId(), postId, request)); }
    @PostMapping("/{postId}/archive") @Operation(summary = "Archive mentor blog post", description = "Requires expected version; archived posts are removed from reader discovery.") public ApiResponse<MentorBlogPostDetailResponse> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request) { return ApiResponse.success(mentorBlogService.archive(principal.getPublicId(), postId, request)); }
}
