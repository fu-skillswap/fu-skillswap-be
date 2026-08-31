package com.fptu.exe.skillswap.modules.forum.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.forum.dto.response.ForumPostResponse;
import com.fptu.exe.skillswap.modules.forum.service.ForumPostService;
import com.fptu.exe.skillswap.modules.forum.service.ForumReportService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
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
    void forumPosts_adminShouldBeAllowedToReadPublicPosts() throws Exception {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID(), "admin@test.com", List.of(RoleCode.ADMIN));
        when(forumPostService.getPosts(any(), any(), any(), any(), any(), any()))
                .thenReturn(CursorPageResponse.<ForumPostResponse>builder().items(List.of()).nextCursor(null).prevCursor(null).hasNext(false).hasPrev(false).limit(20).build());

        mockMvc.perform(get("/api/forum/posts")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk());
    }

    @Test
    void forumPosts_anonymousShouldBeAllowedToReadPublicPosts() throws Exception {
        when(forumPostService.getPosts(any(), any(), any(), any(), any(), any()))
                .thenReturn(CursorPageResponse.<ForumPostResponse>builder().items(List.of()).nextCursor(null).prevCursor(null).hasNext(false).hasPrev(false).limit(20).build());

        mockMvc.perform(get("/api/forum/posts"))
                .andExpect(status().isOk());
    }
}
