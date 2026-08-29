package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumModerationAction;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumPostListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.request.ForumReportResolveRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.admin.service.AdminForumModerationService;
import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminForumModerationServiceTest {

    @Mock
    private ForumAdminPort forumAdminPort;
    @Mock
    private AdminAuditWriterService adminAuditWriterService;

    private AdminForumModerationService service;

    @BeforeEach
    void setUp() {
        service = new AdminForumModerationService(
                forumAdminPort,
                adminAuditWriterService
        );
    }

    @Test
    void resolveReport_delegatesToPortAndReturnsResponse() {
        UUID adminId = UuidUtil.generateUuidV7();
        UUID reportId = UuidUtil.generateUuidV7();
        ForumReportResponse mockResponse = ForumReportResponse.builder()
                .reportId(reportId)
                .status("RESOLVED_ACTION_TAKEN")
                .build();
        when(forumAdminPort.resolveReport(eq(adminId), eq(reportId), any(ForumReportResolveRequest.class)))
                .thenReturn(mockResponse);

        var response = service.resolveReport(adminId, reportId, new ForumReportResolveRequest(
                ForumModerationAction.HIDE_COMMENT, "Vi phạm"
        ));

        assertEquals("RESOLVED_ACTION_TAKEN", response.status());
        verify(forumAdminPort).resolveReport(eq(adminId), eq(reportId), any(ForumReportResolveRequest.class));
    }

    @Test
    void resolveConfirmNoAction_delegatesToPort() {
        UUID adminId = UuidUtil.generateUuidV7();
        UUID reportId = UuidUtil.generateUuidV7();
        ForumReportResponse mockResponse = ForumReportResponse.builder()
                .reportId(reportId)
                .status("RESOLVED_NO_ACTION")
                .build();
        when(forumAdminPort.resolveReport(eq(adminId), eq(reportId), any(ForumReportResolveRequest.class)))
                .thenReturn(mockResponse);

        var response = service.resolveReport(adminId, reportId, new ForumReportResolveRequest(
                ForumModerationAction.CONFIRM_NO_ACTION, "Đã kiểm tra"
        ));

        assertEquals("RESOLVED_NO_ACTION", response.status());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAdminPosts_delegatesToPort() {
        CursorPageResponse mockPage = CursorPageResponse.builder()
                .items(List.of())
                .hasNext(false)
                .hasPrev(false)
                .limit(2)
                .build();
        when(forumAdminPort.getAdminPosts(any(AdminForumPostListRequest.class)))
                .thenReturn(mockPage);

        var response = service.getAdminPosts(new AdminForumPostListRequest(null, 2, null, null, null, null));

        assertTrue(response.items().isEmpty());
        verify(forumAdminPort).getAdminPosts(any(AdminForumPostListRequest.class));
    }
}
