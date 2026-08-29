package com.fptu.exe.skillswap.modules.forum.port;

import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumCommentListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumCommentResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

import java.util.UUID;

public interface ForumAdminPort {
    PageResponse<ForumReportResponse> getReports(AdminForumReportListRequest request);
    ForumReportResponse getReportDetail(UUID reportId);
    ForumReportResponse resolveReport(UUID adminUserId, UUID reportId, ForumReportResolveRequest request);
    CursorPageResponse<ForumPostResponse> getAdminPosts(AdminForumPostListRequest request);
    CursorPageResponse<ForumCommentResponse> getAdminComments(AdminForumCommentListRequest request);
    ForumPostResponse restorePost(UUID adminUserId, UUID postId);
    ForumCommentResponse restoreComment(UUID adminUserId, UUID commentId);
    long countPendingReports();
    long countReportsCreatedBy(UUID userId);
}
