package com.fptu.exe.skillswap.modules.conversation.dto.response;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatAttachmentState; import java.time.LocalDateTime; import java.util.UUID;
public record ChatAttachmentResponse(UUID attachmentId,String filename,String contentType,long sizeBytes,boolean inlineCapable,boolean downloadable,LocalDateTime expiresAt,ChatAttachmentState state) {}
