package com.fptu.exe.skillswap.modules.forum.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public administration contracts of the Forum module. */
public final class ForumAdminPortModels {

    private ForumAdminPortModels() {
    }

    public record ReportListQuery(Integer page, Integer size, String keyword, String status, String targetType) {
    }

    public record PostListQuery(String cursor, Integer limit, String keyword, UUID forumTopicId, UUID authorId, String status) {
    }

    public record CommentListQuery(String cursor, Integer limit, String keyword, UUID postId, UUID authorId, String status) {
    }

    public record ResolveReportCommand(@NotBlank String action, @Size(max = 500) String reviewNote) {
    }

    public record ProgramView(UUID id, String code, String nameVi, String nameEn) {
    }

    public record TopicView(UUID id, String code, String nameVi, String nameEn, Integer displayOrder) {
    }

    public record ReportView(
            UUID reportId, String targetType, UUID targetId, String targetStatus, String targetTitle,
            String targetContentPreview, UUID targetAuthorUserId, String targetAuthorFullName,
            UUID reporterUserId, String reporterFullName, String reasonType, String description,
            String status, UUID reviewedByUserId, String reviewNote, LocalDateTime resolvedAt, LocalDateTime createdAt
    ) {
    }

    public record PostView(
            UUID postId, UUID authorUserId, String authorFullName, String authorAvatarUrl,
            ProgramView authorProgram, TopicView forumTopic, String title, String content, String status,
            Integer commentCount, Integer reactionCount, Integer reportCount, LocalDateTime lastActivityAt,
            boolean reactedByCurrentUser, String myReactionType, LocalDateTime createdAt, LocalDateTime updatedAt,
            List<String> imageUrls
    ) {
    }

    public record CommentView(
            UUID commentId, UUID postId, UUID authorUserId, String authorFullName, String authorAvatarUrl,
            String authorRole, String content, String status, Integer reportCount, Integer reactionCount,
            Boolean reactedByCurrentUser, UUID replyToCommentId, UUID replyToUserId, String replyToUserName,
            LocalDateTime createdAt, LocalDateTime updatedAt, List<String> imageUrls
    ) {
    }
}
