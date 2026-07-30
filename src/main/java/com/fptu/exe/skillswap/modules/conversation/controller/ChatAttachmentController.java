package com.fptu.exe.skillswap.modules.conversation.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.conversation.dto.response.ChatAttachmentDownloadResponse;
import com.fptu.exe.skillswap.modules.conversation.service.ConversationService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/me/chat-attachments") @RequiredArgsConstructor
@Tag(name = "Conversation")
@SecurityRequirement(name = "bearerAuth")
public class ChatAttachmentController {
    private final ConversationService conversationService;
    @PostMapping("/{attachmentId}/download-url")
    @Operation(summary = "Create private chat attachment download URL", description = "Re-authorizes the current conversation participant before issuing a short-lived private credential. PDF and DOCX are download-only.")
    public ApiResponse<ChatAttachmentDownloadResponse> download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attachmentId) {
        return ApiResponse.success(conversationService.downloadAttachment(attachmentId, principal.getId()));
    }
}
