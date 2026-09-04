package com.fptu.exe.skillswap.modules.admin.dto.response;

import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportView;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

/** Maps internal Forum admin port views to controller DTOs. */
public final class AdminForumResponseMapper {

    private AdminForumResponseMapper() {
    }

    public static AdminForumReportResponse report(ReportView value) {
        if (value == null) {
            return null;
        }
        return new AdminForumReportResponse(
                value.reportId(), value.targetType(), value.targetId(), value.targetStatus(), value.targetTitle(),
                value.targetContentPreview(), value.targetAuthorUserId(), value.targetAuthorFullName(),
                value.reporterUserId(), value.reporterFullName(), value.reasonType(), value.description(),
                value.status(), value.reviewedByUserId(), value.reviewNote(), value.resolvedAt(), value.createdAt());
    }

    public static AdminForumPostResponse post(PostView value) {
        if (value == null) {
            return null;
        }
        var program = value.authorProgram();
        var topic = value.forumTopic();
        return new AdminForumPostResponse(
                value.postId(), value.authorUserId(), value.authorFullName(), value.authorAvatarUrl(),
                program == null ? null : new AdminForumProgramResponse(
                        program.id(), program.code(), program.nameVi(), program.nameEn()),
                topic == null ? null : new AdminForumTopicResponse(
                        topic.id(), topic.code(), topic.nameVi(), topic.nameEn(), topic.displayOrder()),
                value.title(), value.content(), value.status(), value.commentCount(), value.reactionCount(), value.reportCount(),
                value.lastActivityAt(), value.reactedByCurrentUser(), value.myReactionType(), value.createdAt(), value.updatedAt(),
                value.imageUrls());
    }

    public static AdminForumCommentResponse comment(CommentView value) {
        if (value == null) {
            return null;
        }
        return new AdminForumCommentResponse(
                value.commentId(), value.postId(), value.authorUserId(), value.authorFullName(), value.authorAvatarUrl(),
                value.authorRole(), value.content(), value.status(), value.reportCount(), value.reactionCount(),
                value.reactedByCurrentUser(), value.replyToCommentId(), value.replyToUserId(), value.replyToUserName(),
                value.createdAt(), value.updatedAt(), value.imageUrls());
    }

    public static CursorPageResponse<AdminForumPostResponse> posts(CursorPageResponse<PostView> page) {
        if (page == null) {
            return null;
        }
        return CursorPageResponse.<AdminForumPostResponse>builder()
                .items(page.items() == null ? null : page.items().stream().map(AdminForumResponseMapper::post).toList())
                .nextCursor(page.nextCursor())
                .prevCursor(page.prevCursor())
                .hasNext(page.hasNext())
                .hasPrev(page.hasPrev())
                .limit(page.limit())
                .build();
    }

    public static CursorPageResponse<AdminForumCommentResponse> comments(CursorPageResponse<CommentView> page) {
        if (page == null) {
            return null;
        }
        return CursorPageResponse.<AdminForumCommentResponse>builder()
                .items(page.items() == null ? null : page.items().stream().map(AdminForumResponseMapper::comment).toList())
                .nextCursor(page.nextCursor())
                .prevCursor(page.prevCursor())
                .hasNext(page.hasNext())
                .hasPrev(page.hasPrev())
                .limit(page.limit())
                .build();
    }

    public static PageResponse<AdminForumReportResponse> reports(PageResponse<ReportView> page) {
        if (page == null) {
            return null;
        }
        return PageResponse.<AdminForumReportResponse>builder()
                .content(page.getContent() == null ? null : page.getContent().stream().map(AdminForumResponseMapper::report).toList())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
