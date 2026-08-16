package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.storage.PrivateStorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachment;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
import com.fptu.exe.skillswap.modules.chat.domain.ChatUploadIntent;
import com.fptu.exe.skillswap.modules.chat.domain.ChatUploadIntentStatus;
import com.fptu.exe.skillswap.modules.chat.domain.Conversation;
import com.fptu.exe.skillswap.modules.chat.domain.Message;
import com.fptu.exe.skillswap.modules.chat.dto.request.ChatAttachmentUploadIntentRequest;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentDownloadResponse;
import com.fptu.exe.skillswap.modules.chat.dto.response.ChatAttachmentUploadIntentResponse;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ChatUploadIntentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.strategy.AttachmentValidationRegistry;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class ChatAttachmentService {

    private final ChatUploadIntentRepository chatUploadIntentRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ConversationRepository conversationRepository;
    private final ChatRoomService chatRoomService;
    private final ChatAccessResolutionService chatAccessResolutionService;
    private final ObjectProvider<PrivateStorageGateway> privateStorageGatewayProvider;
    private final ObjectProvider<StorageObjectReader> storageObjectReaderProvider;
    private final InMemoryRateLimitService rateLimitService;
    private final StorageLifecycleProperties storageLifecycleProperties;
    private final AttachmentValidationRegistry attachmentValidationRegistry;

    @Transactional
    public ChatAttachmentUploadIntentResponse createAttachmentUploadIntent(
            UUID conversationId,
            UUID userId,
            ChatAttachmentUploadIntentRequest request
    ) {
        chatRoomService.ensureParticipant(conversationId, userId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));

        var access = chatAccessResolutionService.resolveMessagingAccess(conversation, userId);
        if (!access.canUploadAttachments()) {
            throw new BaseException(chatAccessResolutionService.resolveMessagingAccessError(access));
        }

        String contentType = normalizeAttachmentContentType(request.contentType());
        validateAttachmentFilename(request.filename(), contentType);
        if (request.sizeBytes() > 10L * 1024 * 1024) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_QUOTA_EXCEEDED);
        }
        if (chatAttachmentRepository.sumUploadedBytesByUserSince(userId, DateTimeUtil.now().toLocalDate().atStartOfDay()) + request.sizeBytes() > 50L * 1024 * 1024) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_QUOTA_EXCEEDED);
        }

        rateLimitService.check(RateLimitScope.TRANSFER, "chat:attachment-intent:" + userId, 20, Duration.ofMinutes(15), "Bạn tạo upload intent quá nhanh");
        UUID intentId = UUID.randomUUID();
        String key = "chat-attachments/" + conversationId + "/" + intentId + extensionFor(contentType);
        ChatUploadIntent intent = ChatUploadIntent.builder()
                .conversation(conversation)
                .ownerUserId(userId)
                .storageKey(key)
                .originalFilename(request.filename().trim())
                .contentType(contentType)
                .expectedSizeBytes(request.sizeBytes())
                .expiresAt(DateTimeUtil.now().plusMinutes(15))
                .build();
        chatUploadIntentRepository.save(intent);

        var upload = privateStorageGateway().generatePrivateUploadUrl(key, contentType, Duration.ofMinutes(15));
        return new ChatAttachmentUploadIntentResponse(intent.getId(), upload.uploadUrl(), upload.expiresAt(), contentType);
    }

    @Transactional(readOnly = true)
    public ChatAttachmentDownloadResponse downloadAttachment(UUID attachmentId, UUID userId) {
        ChatAttachment attachment = chatAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp đính kèm"));
        Conversation conversation = attachment.getMessage().getConversation();
        chatRoomService.ensureParticipant(conversation.getId(), userId);

        var access = chatAccessResolutionService.resolveMessagingAccess(conversation, userId);
        if (!access.canDownloadAttachments() || attachment.getState() != ChatAttachmentState.ACTIVE
                || !attachment.getExpiresAt().isAfter(DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tệp đính kèm");
        }

        String disposition = inlineCapable(attachment.getContentType()) ? "inline" : "attachment";
        var download = privateStorageGateway().generatePrivateDownloadUrl(attachment.getStorageKey(), Duration.ofMinutes(10), disposition);
        return new ChatAttachmentDownloadResponse(download.downloadUrl(), download.expiresAt());
    }

    public void consumeAttachmentIntents(Conversation conversation, UUID userId, Message message, List<UUID> intentIds) {
        if (intentIds == null || intentIds.isEmpty()) return;
        if (intentIds.size() > 5) throw new BaseException(ErrorCode.CHAT_ATTACHMENT_QUOTA_EXCEEDED);

        for (UUID intentId : new LinkedHashSet<>(intentIds)) {
            ChatUploadIntent intent = chatUploadIntentRepository.findByIdForUpdate(intentId)
                    .orElseThrow(() -> new BaseException(ErrorCode.CHAT_UPLOAD_INTENT_INVALID));
            if (!intent.getConversation().getId().equals(conversation.getId())
                    || !intent.getOwnerUserId().equals(userId)
                    || intent.getStatus() != ChatUploadIntentStatus.PENDING_UPLOAD
                    || intent.getExpiresAt().isBefore(DateTimeUtil.now())) {
                throw new BaseException(ErrorCode.CHAT_UPLOAD_INTENT_INVALID);
            }

            var metadata = storageObjectReader().headObject(intent.getStorageKey());
            if (metadata.sizeBytes() != intent.getExpectedSizeBytes() || !intent.getContentType().equalsIgnoreCase(metadata.contentType())) {
                throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
            }
            validateAttachmentSignature(intent.getStorageKey(), intent.getContentType());
            int retentionDays = Math.max(1, storageLifecycleProperties.getChatAttachmentExpiryDays());
            chatAttachmentRepository.save(ChatAttachment.builder()
                    .message(message)
                    .uploadIntent(intent)
                    .storageKey(intent.getStorageKey())
                    .originalFilename(intent.getOriginalFilename())
                    .contentType(intent.getContentType())
                    .sizeBytes(metadata.sizeBytes())
                    .expiresAt(message.getCreatedAt().plusDays(retentionDays))
                    .build());
            intent.setStatus(ChatUploadIntentStatus.CONFIRMED);
        }
    }

    public String normalizeAttachmentContentType(String value) {
        if (attachmentValidationRegistry != null) {
            return attachmentValidationRegistry.normalizeContentType(value);
        }
        return value;
    }

    public void validateAttachmentFilename(String name, String type) {
        if (attachmentValidationRegistry != null) {
            attachmentValidationRegistry.validateFilename(name, type);
        }
    }

    public String extensionFor(String type) {
        if (attachmentValidationRegistry != null) {
            return attachmentValidationRegistry.extensionFor(type);
        }
        return ".bin";
    }

    public boolean inlineCapable(String type) {
        if (attachmentValidationRegistry != null) {
            return attachmentValidationRegistry.inlineCapable(type);
        }
        return false;
    }

    public void validateAttachmentSignature(String key, String type) {
        if (attachmentValidationRegistry != null) {
            attachmentValidationRegistry.validateSignature(key, type, storageObjectReader());
        }
    }

    public void validateDocxStructure(String key) {
        final long maxUncompressedBytes = 100L * 1024 * 1024;
        final int maxEntries = 200;
        long uncompressedBytes = 0L;
        int entryCount = 0;
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        try (var raw = storageObjectReader().openObject(key);
             var zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > maxEntries || entry.isDirectory()) {
                    if (entryCount > maxEntries) {
                        throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
                    }
                    continue;
                }
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasWordDocument |= "word/document.xml".equals(entry.getName());
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressedBytes += read;
                    if (uncompressedBytes > maxUncompressedBytes) {
                        throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
                    }
                }
            }
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
        }
        if (!hasContentTypes || !hasWordDocument) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
        }
    }

    private PrivateStorageGateway privateStorageGateway() {
        PrivateStorageGateway storageGateway = privateStorageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage cho tệp đính kèm chat");
        }
        return storageGateway;
    }

    private StorageObjectReader storageObjectReader() {
        StorageObjectReader storageReader = storageObjectReaderProvider.getIfAvailable();
        if (storageReader == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage cho tệp đính kèm chat");
        }
        return storageReader;
    }
}
