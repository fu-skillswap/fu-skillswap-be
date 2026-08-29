package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminForumModerationService {

    private final ForumAdminPort forumAdminPort;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public PageResponse<ForumReportResponse> getReports(AdminForumReportListRequest request) {
        return forumAdminPort.getReports(request);
    }

    @Transactional(readOnly = true)
    public ForumReportResponse getReportDetail(UUID reportId) {
        return forumAdminPort.getReportDetail(reportId);
    }

    @Transactional
    public ForumReportResponse resolveReport(UUID adminUserId, UUID reportId, ForumReportResolveRequest request) {
        ForumReportResponse response = forumAdminPort.resolveReport(adminUserId, reportId, request);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_REPORT",
                reportId,
                "RESOLVE_FORUM_REPORT",
                Map.of("status", ForumReportStatus.OPEN.name()),
                Map.of("status", response.status() == null ? "" : response.status(),
                        "action", request.action() == null ? "" : request.action().name(),
                        "note", request.reviewNote() == null ? "" : request.reviewNote())
        );

        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ForumPostResponse> getAdminPosts(AdminForumPostListRequest request) {
        return forumAdminPort.getAdminPosts(request);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ForumCommentResponse> getAdminComments(AdminForumCommentListRequest request) {
        return forumAdminPort.getAdminComments(request);
    }

    @Transactional
    public ForumPostResponse restorePost(UUID adminUserId, UUID postId) {
        ForumPostResponse response = forumAdminPort.restorePost(adminUserId, postId);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_POST",
                postId,
                "RESTORE_FORUM_POST",
                Map.of("status", ForumPostStatus.HIDDEN.name()),
                Map.of("status", ForumPostStatus.PUBLISHED.name())
        );

        return response;
    }

    @Transactional
    public ForumCommentResponse restoreComment(UUID adminUserId, UUID commentId) {
        ForumCommentResponse response = forumAdminPort.restoreComment(adminUserId, commentId);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_COMMENT",
                commentId,
                "RESTORE_FORUM_COMMENT",
                Map.of("status", ForumCommentStatus.HIDDEN.name()),
                Map.of("status", ForumCommentStatus.VISIBLE.name())
        );

        return response;
    }
}
