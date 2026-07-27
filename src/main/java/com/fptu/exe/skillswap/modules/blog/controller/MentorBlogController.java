package com.fptu.exe.skillswap.modules.blog.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.blog.dto.MentorBlogPostDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.request.BlogExpectedVersionRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostCreateRequest;
import com.fptu.exe.skillswap.modules.blog.dto.request.MentorBlogPostUpdateRequest;
import com.fptu.exe.skillswap.modules.blog.service.MentorBlogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
public class MentorBlogController {
    private final MentorBlogService mentorBlogService;
    @GetMapping public ApiResponse<List<MentorBlogPostDetailResponse>> list(@AuthenticationPrincipal UserPrincipal principal) { return ApiResponse.success(mentorBlogService.list(principal.getPublicId())); }
    @PostMapping public ApiResponse<MentorBlogPostDetailResponse> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody MentorBlogPostCreateRequest request) { return ApiResponse.created(mentorBlogService.create(principal.getPublicId(), request)); }
    @GetMapping("/{postId}") public ApiResponse<MentorBlogPostDetailResponse> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId) { return ApiResponse.success(mentorBlogService.get(principal.getPublicId(), postId)); }
    @PutMapping("/{postId}") public ApiResponse<MentorBlogPostDetailResponse> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody MentorBlogPostUpdateRequest request) { return ApiResponse.success(mentorBlogService.update(principal.getPublicId(), postId, request)); }
    @PostMapping("/{postId}/publish") public ApiResponse<MentorBlogPostDetailResponse> publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request) { return ApiResponse.success(mentorBlogService.publish(principal.getPublicId(), postId, request)); }
    @PostMapping("/{postId}/archive") public ApiResponse<MentorBlogPostDetailResponse> archive(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID postId, @Valid @RequestBody BlogExpectedVersionRequest request) { return ApiResponse.success(mentorBlogService.archive(principal.getPublicId(), postId, request)); }
}
