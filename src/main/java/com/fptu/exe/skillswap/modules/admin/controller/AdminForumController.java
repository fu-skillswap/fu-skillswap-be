package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminForumModerationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
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
    @Operation(
            summary = "Lấy danh sách forum posts cho admin",
            description = """
                    Trả về danh sách bài viết forum cho admin theo cursor pagination.
                    
                    Lưu ý cho Frontend:
                    - `cursor` là opaque string, chỉ được lấy từ `nextCursor` của response trước đó.
                    - Không được decode, chỉnh sửa hoặc tự tạo cursor mới.
                    - Response là `ApiResponse<CursorPageResponse<ForumPostResponse>>`.
                    - Bộ lọc hỗ trợ `keyword`, `helpTopicId`, `authorId`, `status`.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách forum posts cho admin thành công",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            mediaType = "application/json",
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                    name = "AdminForumPostCursorPage",
                                    value = """
                                            {
                                              "timestamp": "2026-07-08 15:40:00",
                                              "status": 200,
                                              "code": "SUCCESS_0200",
                                              "message": "Thành công",
                                              "data": {
                                                "items": [
                                                  {
                                                    "postId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a1001",
                                                    "authorUserId": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a2001",
                                                    "authorFullName": "Nguyen Van A",
                                                    "authorAvatarUrl": "https://cdn.skillswap.asia/avatar/a.jpg",
                                                    "helpTopic": {
                                                      "id": "0197e6b1-2d1a-7f0f-bc7a-9bc2e31a3001",
                                                      "code": "HELP_PROJECT_REVIEW",
                                                      "nameVi": "Góp ý dự án/case study",
                                                      "nameEn": "Project or case study review"
                                                    },
                                                    "title": "Xin góp ý slide milestone",
                                                    "content": "Mọi người review giúp mình flow thuyết trình với.",
                                                    "status": "PUBLISHED",
                                                    "commentCount": 3,
                                                    "reactionCount": 5,
                                                    "reportCount": 0,
                                                    "lastActivityAt": "2026-07-08T14:30:00",
                                                    "reactedByCurrentUser": false,
                                                    "myReactionType": null,
                                                    "createdAt": "2026-07-08T10:00:00",
                                                    "updatedAt": "2026-07-08T10:00:00"
                                                  }
                                                ],
                                                "nextCursor": "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I",
                                                "prevCursor": null,
                                                "hasNext": true,
                                                "hasPrev": false,
                                                "limit": 20
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền admin")
    })
    public ApiResponse<CursorPageResponse<ForumPostResponse>> getPosts(@ParameterObject @ModelAttribute AdminForumPostListRequest request) {
        return ApiResponse.success(adminForumModerationService.getAdminPosts(request));
    }

    @GetMapping("/comments")
    @Operation(summary = "Lấy danh sách forum comments cho admin")
    public ApiResponse<CursorPageResponse<ForumCommentResponse>> getComments(@ParameterObject @ModelAttribute AdminForumCommentListRequest request) {
        return ApiResponse.success(adminForumModerationService.getAdminComments(request));
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "Khôi phục bài viết forum đã bị ẩn")
    public ApiResponse<ForumPostResponse> restorePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        return ApiResponse.success(adminForumModerationService.restorePost(principal.getPublicId(), postId));
    }

    @PostMapping("/comments/{commentId}/restore")
    @Operation(summary = "Khôi phục bình luận forum đã bị ẩn")
    public ApiResponse<ForumCommentResponse> restoreComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId) {
        return ApiResponse.success(adminForumModerationService.restoreComment(principal.getPublicId(), commentId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
