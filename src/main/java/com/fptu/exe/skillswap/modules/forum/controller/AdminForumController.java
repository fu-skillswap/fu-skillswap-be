package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.service.AdminForumModerationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/forum")
@RequiredArgsConstructor
@Tag(name = "Admin - Forum", description = "Nhóm API moderation forum dành cho admin để xử lý report, ẩn hoặc khôi phục nội dung.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminForumController {

    private final AdminForumModerationService adminForumModerationService;

    @GetMapping("/reports")
    @Operation(summary = "Lấy queue forum reports")
    public ApiResponse<PageResponse<ForumReportResponse>> getReports(@ParameterObject @ModelAttribute AdminForumReportListRequest request) {
        return ApiResponse.success(adminForumModerationService.getReports(request));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Lấy chi tiết forum report")
    public ApiResponse<ForumReportResponse> getReportDetail(@PathVariable UUID reportId) {
        return ApiResponse.success(adminForumModerationService.getReportDetail(reportId));
    }

    @PostMapping("/reports/{reportId}/resolve")
    @Operation(summary = "Resolve forum report")
    public ApiResponse<ForumReportResponse> resolveReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody ForumReportResolveRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(adminForumModerationService.resolveReport(principal.getPublicId(), reportId, request));
    }

    @GetMapping("/posts")
    @Operation(summary = "Lấy danh sách forum posts cho admin")
    public ApiResponse<PageResponse<ForumPostResponse>> getPosts(@ParameterObject @ModelAttribute AdminForumPostListRequest request) {
        return ApiResponse.success(adminForumModerationService.getAdminPosts(request));
    }

    @GetMapping("/comments")
    @Operation(summary = "Lấy danh sách forum comments cho admin")
    public ApiResponse<PageResponse<ForumCommentResponse>> getComments(@ParameterObject @ModelAttribute AdminForumCommentListRequest request) {
        return ApiResponse.success(adminForumModerationService.getAdminComments(request));
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "Khôi phục bài viết forum đã bị ẩn")
    public ApiResponse<ForumPostResponse> restorePost(@PathVariable UUID postId) {
        return ApiResponse.success(adminForumModerationService.restorePost(postId));
    }

    @PostMapping("/comments/{commentId}/restore")
    @Operation(summary = "Khôi phục bình luận forum đã bị ẩn")
    public ApiResponse<ForumCommentResponse> restoreComment(@PathVariable UUID commentId) {
        return ApiResponse.success(adminForumModerationService.restoreComment(commentId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
