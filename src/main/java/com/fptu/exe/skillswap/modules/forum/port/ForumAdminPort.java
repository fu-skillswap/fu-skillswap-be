package com.fptu.exe.skillswap.modules.forum.port;

import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;
import java.util.List;

public interface ForumAdminPort {
    PageResponse<ForumAdminPortModels.ReportView> getReports(ForumAdminPortModels.ReportListQuery request);
    ForumAdminPortModels.ReportView getReportDetail(UUID reportId);
    ForumAdminPortModels.ReportView resolveReport(UUID adminUserId, UUID reportId, ForumAdminPortModels.ResolveReportCommand request);
    CursorPageResponse<ForumAdminPortModels.PostView> getAdminPosts(ForumAdminPortModels.PostListQuery request);
    CursorPageResponse<ForumAdminPortModels.CommentView> getAdminComments(ForumAdminPortModels.CommentListQuery request);
    ForumAdminPortModels.PostView restorePost(UUID adminUserId, UUID postId);
    ForumAdminPortModels.CommentView restoreComment(UUID adminUserId, UUID commentId);
    long countPendingReports();
    long countReportsCreatedBy(UUID userId);
    boolean existsReportById(UUID reportId);
    List<String> reportStatusNames();
}
