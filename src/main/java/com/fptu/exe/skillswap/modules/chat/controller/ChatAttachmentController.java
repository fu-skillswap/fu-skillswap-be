package com.fptu.exe.skillswap.modules.chat.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentDownloadResponse;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
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
    @Operation(summary = "Tạo URL tải file chat riêng tư", description = "Kiểm tra lại quyền tham gia conversation trước khi cấp URL tải file có thời hạn ngắn. PDF và DOCX chỉ hỗ trợ tải xuống.")
    public ApiResponse<ChatAttachmentDownloadResponse> download(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attachmentId) {
        return ApiResponse.success(conversationService.downloadAttachment(attachmentId, principal.getId()));
    }
}
