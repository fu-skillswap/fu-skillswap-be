package com.fptu.exe.skillswap.modules.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ConversationResponse;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversationService conversationService;

    @Test
    void getConversationDetail_whenAuthenticated_shouldReturnDetail() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "user@test.com", List.of(RoleCode.MENTEE));

        ConversationResponse detail = ConversationResponse.builder()
                .id(convId)
                .unreadCount(5L)
                .build();

        when(conversationService.getConversationDetail(convId, userId)).thenReturn(detail);

        mockMvc.perform(get("/api/me/conversations/" + convId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(convId.toString()))
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    @Test
    void getTotalUnreadCount_shouldReturnCount() throws Exception {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "user@test.com", List.of(RoleCode.MENTEE));

        when(conversationService.getTotalUnreadCount(userId)).thenReturn(10L);

        mockMvc.perform(get("/api/me/conversations/unread-count")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.totalUnreadCount").value(10));
    }

    @Test
    void markConversationAsRead_shouldSucceed() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(userId, "user@test.com", List.of(RoleCode.MENTEE));

        doNothing().when(conversationService).markConversationAsRead(convId, userId);

        mockMvc.perform(patch("/api/me/conversations/" + convId + "/read")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").value("Đánh dấu đã đọc thành công"));

        verify(conversationService, times(1)).markConversationAsRead(convId, userId);
    }
}
