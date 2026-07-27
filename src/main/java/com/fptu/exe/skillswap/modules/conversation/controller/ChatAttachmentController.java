package com.fptu.exe.skillswap.modules.conversation.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentDownloadResponse;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/me/chat-attachments") @RequiredArgsConstructor
public class ChatAttachmentController {
    private final ConversationService conversationService;
    @PostMapping("/{attachmentId}/download-url")
    public ApiResponse<ChatAttachmentDownloadResponse> download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attachmentId) {
        return ApiResponse.success(conversationService.downloadAttachment(attachmentId, principal.getId()));
    }
}
