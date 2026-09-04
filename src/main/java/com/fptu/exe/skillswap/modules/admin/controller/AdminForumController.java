package com.fptu.exe.skillswap.modules.admin.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumCommentResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumPostResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumReportResponse;
import com.fptu.exe.skillswap.modules.admin.dto.response.AdminForumResponseMapper;
import com.fptu.exe.skillswap.modules.forum.port.CreateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseView;
import com.fptu.exe.skillswap.modules.forum.port.SetForumProhibitedPhraseActiveCommand;
import com.fptu.exe.skillswap.modules.forum.port.UpdateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand;
import com.fptu.exe.skillswap.modules.admin.service.AdminForumModerationService;
import com.fptu.exe.skillswap.modules.admin.service.AdminForumProhibitedPhraseService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/forum")
@RequiredArgsConstructor
@Tag(name = "Admin - Forum", description = "Admin - dành cho quản trị viên. Xử lý report, ẩn hoặc khôi phục nội dung forum; dữ liệu moderation không dùng cho public FE.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class AdminForumController {

    private final AdminForumModerationService adminForumModerationService;
    private final AdminForumProhibitedPhraseService prohibitedPhraseService;

    @GetMapping("/prohibited-phrases")
    @Operation(summary = "Lấy danh sách cụm từ cấm của forum")
    public ApiResponse<CursorPageResponse<ForumProhibitedPhraseView>> getProhibitedPhrases(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(prohibitedPhraseService.list(isActive, cursor, limit));
    }

    @GetMapping("/prohibited-phrases/{ruleId}")
    @Operation(summary = "Lấy chi tiết cụm từ cấm của forum")
    public ApiResponse<ForumProhibitedPhraseView> getProhibitedPhrase(@PathVariable UUID ruleId) {
        return ApiResponse.success(prohibitedPhraseService.get(ruleId));
    }

    @PostMapping("/prohibited-phrases")
    @Operation(summary = "Thêm cụm từ cấm cho forum")
    public ApiResponse<ForumProhibitedPhraseView> createProhibitedPhrase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateForumProhibitedPhraseCommand command
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.created(prohibitedPhraseService.create(principal.getPublicId(), command));
    }

    @org.springframework.web.bind.annotation.PutMapping("/prohibited-phrases/{ruleId}")
    @Operation(summary = "Cập nhật cụm từ cấm của forum")
    public ApiResponse<ForumProhibitedPhraseView> updateProhibitedPhrase(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateForumProhibitedPhraseCommand command
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(prohibitedPhraseService.update(principal.getPublicId(), ruleId, command));
    }

    @PatchMapping("/prohibited-phrases/{ruleId}/active")
    @Operation(summary = "Bật hoặc tắt cụm từ cấm của forum")
    public ApiResponse<ForumProhibitedPhraseView> changeProhibitedPhraseActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID ruleId,
            @Valid @RequestBody SetForumProhibitedPhraseActiveCommand command
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(prohibitedPhraseService.changeActive(principal.getPublicId(), ruleId, command));
    }

    @GetMapping("/reports")
    @Operation(summary = "Lấy queue forum reports")
    public ApiResponse<PageResponse<AdminForumReportResponse>> getReports(@ParameterObject @ModelAttribute ReportListQuery request) {
        return ApiResponse.success(AdminForumResponseMapper.reports(adminForumModerationService.getReports(request)));
    }

    @GetMapping("/reports/{reportId}")
    @Operation(summary = "Lấy chi tiết forum report")
    public ApiResponse<AdminForumReportResponse> getReportDetail(@PathVariable UUID reportId) {
        return ApiResponse.success(AdminForumResponseMapper.report(adminForumModerationService.getReportDetail(reportId)));
    }

    @PostMapping("/reports/{reportId}/resolve")
    @Operation(summary = "Xử lý forum report", description = "Admin chọn action moderation và có thể ghi review note. Kết quả trả về trạng thái report và target sau xử lý.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Xử lý report thành công",
            content = @Content(mediaType = "application/json", examples = @ExampleObject(
                    name = "ForumReportResolved",
                    value = """
                            {"status":200,"code":"SUCCESS_0200","message":"Thành công","data":{"reportId":"019f8234-aaaa-bbbb-cccc-1234567890ab","targetType":"POST","targetId":"019f5234-aaaa-bbbb-cccc-1234567890ab","targetStatus":"HIDDEN","status":"RESOLVED","reviewNote":"Đã ẩn nội dung spam.","resolvedAt":"2026-09-04T03:35:00"}}
                            """
            ))
    )
    public ApiResponse<AdminForumReportResponse> resolveReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID reportId,
            @Valid @RequestBody ResolveReportCommand request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(AdminForumResponseMapper.report(
                adminForumModerationService.resolveReport(principal.getPublicId(), reportId, request)));
    }

    @GetMapping("/posts")
    @Operation(
            summary = "Lấy danh sách forum posts cho admin",
            description = """
                    Trả về danh sách bài viết forum cho admin theo cursor pagination.
                    
                    Lưu ý cho Frontend:
                    - `cursor` là opaque string, chỉ được lấy từ `nextCursor` của response trước đó.
                    - Không được decode, chỉnh sửa hoặc tự tạo cursor mới.
                    - Response là `ApiResponse<CursorPageResponse<AdminForumPostResponse>>`.
                    - Bộ lọc hỗ trợ `keyword`, `forumTopicId`, `authorId`, `status`.
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
                                                    "forumTopic": {
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
    public ApiResponse<CursorPageResponse<AdminForumPostResponse>> getPosts(@ParameterObject @ModelAttribute PostListQuery request) {
        return ApiResponse.success(AdminForumResponseMapper.posts(adminForumModerationService.getAdminPosts(request)));
    }

    @GetMapping("/comments")
    @Operation(summary = "Lấy danh sách forum comments cho admin")
    public ApiResponse<CursorPageResponse<AdminForumCommentResponse>> getComments(@ParameterObject @ModelAttribute CommentListQuery request) {
        return ApiResponse.success(AdminForumResponseMapper.comments(adminForumModerationService.getAdminComments(request)));
    }

    @PostMapping("/posts/{postId}/restore")
    @Operation(summary = "Khôi phục bài viết forum đã bị ẩn")
    public ApiResponse<AdminForumPostResponse> restorePost(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId) {
        return ApiResponse.success(AdminForumResponseMapper.post(
                adminForumModerationService.restorePost(principal.getPublicId(), postId)));
    }

    @PostMapping("/comments/{commentId}/restore")
    @Operation(summary = "Khôi phục bình luận forum đã bị ẩn")
    public ApiResponse<AdminForumCommentResponse> restoreComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId) {
        return ApiResponse.success(AdminForumResponseMapper.comment(
                adminForumModerationService.restoreComment(principal.getPublicId(), commentId)));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
