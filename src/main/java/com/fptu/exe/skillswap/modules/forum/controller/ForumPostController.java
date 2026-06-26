package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
@Tag(name = "Forum", description = "Nhóm API forum nội bộ cho người dùng đăng bài, bình luận, thả reaction và đọc thảo luận theo help topic.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('MENTEE','MENTOR') and !hasRole('ADMIN') and !hasRole('SYSTEM_ADMIN')")
public class ForumPostController {

    private final ForumPostService forumPostService;

    @GetMapping("/posts")
    @Operation(summary = "Lấy danh sách bài viết forum", description = "Danh sách bài viết forum cho user đã đăng nhập, mặc định sắp xếp bài mới nhất trước.")
    public ApiResponse<PageResponse<ForumPostResponse>> getPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID helpTopicId,
            @RequestParam(required = false, defaultValue = "false") Boolean mine
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.getPosts(principal.getPublicId(), page, size, keyword, helpTopicId, mine));
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "Lấy chi tiết bài viết forum")
    public ApiResponse<ForumPostResponse> getPostDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.getPostDetail(principal.getPublicId(), postId));
    }

    @PostMapping("/posts")
    @Operation(summary = "Tạo bài viết forum")
    public ApiResponse<ForumPostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ForumPostUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.createPost(principal.getPublicId(), request));
    }

    @PutMapping("/posts/{postId}")
    @Operation(summary = "Cập nhật bài viết forum của tôi")
    public ApiResponse<ForumPostResponse> updatePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody ForumPostUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.updatePost(principal.getPublicId(), postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "Xóa mềm bài viết forum của tôi")
    public ApiResponse<ForumPostResponse> deletePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.deletePost(principal.getPublicId(), postId));
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "Lấy comment của bài viết forum", description = "Danh sách comment theo thứ tự cũ nhất trước.")
    public ApiResponse<PageResponse<ForumCommentResponse>> getComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.getComments(principal.getPublicId(), postId, page, size));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "Tạo comment mới cho bài viết forum")
    public ApiResponse<ForumCommentResponse> createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody ForumCommentUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.createComment(principal.getPublicId(), postId, request));
    }

    @PutMapping("/comments/{commentId}")
    @Operation(summary = "Cập nhật comment forum của tôi")
    public ApiResponse<ForumCommentResponse> updateComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId,
            @Valid @RequestBody ForumCommentUpsertRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.updateComment(principal.getPublicId(), commentId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Xóa mềm comment forum của tôi")
    public ApiResponse<ForumCommentResponse> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.deleteComment(principal.getPublicId(), commentId));
    }

    @PutMapping("/posts/{postId}/reaction")
    @Operation(summary = "Thả reaction cho bài viết forum")
    public ApiResponse<ForumPostResponse> upsertReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Valid @RequestBody ForumReactionRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.upsertReaction(principal.getPublicId(), postId, request));
    }

    @DeleteMapping("/posts/{postId}/reaction")
    @Operation(summary = "Bỏ reaction của tôi khỏi bài viết forum")
    public ApiResponse<ForumPostResponse> removeReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.removeReaction(principal.getPublicId(), postId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
