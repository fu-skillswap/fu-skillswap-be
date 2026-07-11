package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumCommentUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumPostUpsertRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReactionRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
    @Operation(
            summary = "Lấy danh sách bài viết forum",
            description = """
                    Trả về danh sách bài viết forum theo cursor pagination.
                    
                    Lưu ý cho Frontend:
                    - `cursor` là opaque string, chỉ được lấy từ `nextCursor` của response trước đó.
                    - Không được decode, chỉnh sửa hoặc tự tạo cursor mới.
                    - Endpoint này trả `ApiResponse<CursorPageResponse<ForumPostResponse>>`.
                    - `nextCursor = null` nghĩa là đã hết dữ liệu.
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Lấy danh sách forum posts thành công",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "ForumPostCursorPage",
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    public ApiResponse<CursorPageResponse<ForumPostResponse>> getPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(
                    description = "Opaque cursor string. Frontend không được cố gắng decode hay tự tạo chuỗi này; chỉ được lấy từ nextCursor của response trước đó để truyền lên.",
                    example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
            )
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Số lượng item mong muốn cho một lần lấy dữ liệu. Mặc định 20, tối đa 50.", example = "20")
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID helpTopicId,
            @RequestParam(required = false, defaultValue = "false") Boolean mine
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.getPosts(principal.getPublicId(), cursor, limit, keyword, helpTopicId, mine));
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
    public ApiResponse<CursorPageResponse<ForumCommentResponse>> getComments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postId,
            @Parameter(
                    description = "Opaque cursor string. Frontend không được cố gắng decode hay tự tạo chuỗi này; chỉ được lấy từ nextCursor của response trước đó để truyền lên.",
                    example = "djEuQmFzZTY0VXJsSWYuLi5PcGFxdWVDdXJzb3I"
            )
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Số lượng item mong muốn cho một lần lấy dữ liệu. Mặc định 20, tối đa 50.", example = "20")
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.getComments(principal.getPublicId(), postId, cursor, limit));
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

    @PutMapping("/comments/{commentId}/reaction")
    @Operation(summary = "Thả reaction cho bình luận forum")
    public ApiResponse<ForumCommentResponse> upsertCommentReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId,
            @Valid @RequestBody ForumReactionRequest request
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.upsertCommentReaction(principal.getPublicId(), commentId, request));
    }

    @DeleteMapping("/comments/{commentId}/reaction")
    @Operation(summary = "Bỏ reaction của tôi khỏi bình luận forum")
    public ApiResponse<ForumCommentResponse> removeCommentReaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID commentId
    ) {
        ensureAuthenticated(principal);
        return ApiResponse.success(forumPostService.removeCommentReaction(principal.getPublicId(), commentId));
    }

    private void ensureAuthenticated(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
    }
}
