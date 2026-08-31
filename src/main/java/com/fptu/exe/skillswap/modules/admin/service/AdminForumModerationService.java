package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.CommentView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand;
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
    public PageResponse<ReportView> getReports(ReportListQuery request) {
        return forumAdminPort.getReports(request);
    }

    @Transactional(readOnly = true)
    public ReportView getReportDetail(UUID reportId) {
        return forumAdminPort.getReportDetail(reportId);
    }

    @Transactional
    public ReportView resolveReport(UUID adminUserId, UUID reportId, ResolveReportCommand request) {
        ReportView response = forumAdminPort.resolveReport(adminUserId, reportId, request);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_REPORT",
                reportId,
                "RESOLVE_FORUM_REPORT",
                Map.of("status", "OPEN"),
                Map.of("status", response.status() == null ? "" : response.status(),
                        "action", request.action() == null ? "" : request.action(),
                        "note", request.reviewNote() == null ? "" : request.reviewNote())
        );

        return response;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<PostView> getAdminPosts(PostListQuery request) {
        return forumAdminPort.getAdminPosts(request);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<CommentView> getAdminComments(CommentListQuery request) {
        return forumAdminPort.getAdminComments(request);
    }

    @Transactional
    public PostView restorePost(UUID adminUserId, UUID postId) {
        PostView response = forumAdminPort.restorePost(adminUserId, postId);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_POST",
                postId,
                "RESTORE_FORUM_POST",
                Map.of("status", "HIDDEN"),
                Map.of("status", "PUBLISHED")
        );

        return response;
    }

    @Transactional
    public CommentView restoreComment(UUID adminUserId, UUID commentId) {
        CommentView response = forumAdminPort.restoreComment(adminUserId, commentId);

        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "FORUM_COMMENT",
                commentId,
                "RESTORE_FORUM_COMMENT",
                Map.of("status", "HIDDEN"),
                Map.of("status", "VISIBLE")
        );

        return response;
    }
}
