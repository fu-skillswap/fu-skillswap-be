package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.request.AdminForumReportListRequest;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumReportResponse;
import com.fptu.exe.skillswap.modules.admin.service.AdminForumModerationService;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "application.cursor.crypto.version=test",
        "application.cursor.crypto.aes-key=MDEyMzQ1Njc4OWFiY2RlZg==",
        "application.cursor.crypto.hmac-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class ForumControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ForumPostService forumPostService;
    @MockBean
    private ForumReportService forumReportService;
    @MockBean
    private AdminForumModerationService adminForumModerationService;
    @MockBean
    private CursorCodec cursorCodec;

    @Test
    void forumPosts_menteeShouldBeAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "mentee@test.com", List.of(RoleCode.MENTEE));
        when(forumPostService.getPosts(any(), any(), any(), any(), any(), any()))
                .thenReturn(CursorPageResponse.<ForumPostResponse>builder().items(List.of()).nextCursor(null).prevCursor(null).hasNext(false).hasPrev(false).limit(20).build());

        mockMvc.perform(get("/api/forum/posts")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk());
    }

    @Test
    void forumPosts_adminShouldBeForbidden() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "admin@test.com", List.of(RoleCode.ADMIN));

        mockMvc.perform(get("/api/forum/posts")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(forumPostService);
    }

    @Test
    void forumPosts_anonymousShouldBeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/forum/posts"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(forumPostService);
    }

    @Test
    void adminForumReports_adminShouldBeAllowed() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "admin@test.com", List.of(RoleCode.ADMIN));
        when(adminForumModerationService.getReports(any(AdminForumReportListRequest.class)))
                .thenReturn(PageResponse.<ForumReportResponse>builder().content(List.of()).page(0).size(20).totalElements(0).totalPages(0).last(true).build());

        mockMvc.perform(get("/api/admin/forum/reports")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk());
    }

    @Test
    void adminForumReports_menteeShouldBeForbidden() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "mentee@test.com", List.of(RoleCode.MENTEE));

        mockMvc.perform(get("/api/admin/forum/reports")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminForumModerationService);
    }
}
