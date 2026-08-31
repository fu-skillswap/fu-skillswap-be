package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.PostListQuery;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ReportView;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPortModels.ResolveReportCommand;
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
        ReportView mockResponse = reportView(reportId, "RESOLVED_ACTION_TAKEN");
        when(forumAdminPort.resolveReport(eq(adminId), eq(reportId), any(ResolveReportCommand.class)))
                .thenReturn(mockResponse);

        var response = service.resolveReport(adminId, reportId, new ResolveReportCommand("HIDE_COMMENT", "Vi phạm"));

        assertEquals("RESOLVED_ACTION_TAKEN", response.status());
        verify(forumAdminPort).resolveReport(eq(adminId), eq(reportId), any(ResolveReportCommand.class));
    }

    @Test
    void resolveConfirmNoAction_delegatesToPort() {
        UUID adminId = UuidUtil.generateUuidV7();
        UUID reportId = UuidUtil.generateUuidV7();
        ReportView mockResponse = reportView(reportId, "RESOLVED_NO_ACTION");
        when(forumAdminPort.resolveReport(eq(adminId), eq(reportId), any(ResolveReportCommand.class)))
                .thenReturn(mockResponse);

        var response = service.resolveReport(adminId, reportId, new ResolveReportCommand("CONFIRM_NO_ACTION", "Đã kiểm tra"));

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
        when(forumAdminPort.getAdminPosts(any(PostListQuery.class)))
                .thenReturn(mockPage);

        var response = service.getAdminPosts(new PostListQuery(null, 2, null, null, null, null));

        assertTrue(response.items().isEmpty());
        verify(forumAdminPort).getAdminPosts(any(PostListQuery.class));
    }

    private ReportView reportView(UUID reportId, String status) {
        return new ReportView(reportId, null, null, null, null, null, null, null,
                null, null, null, null, status, null, null, null, null);
    }
}
