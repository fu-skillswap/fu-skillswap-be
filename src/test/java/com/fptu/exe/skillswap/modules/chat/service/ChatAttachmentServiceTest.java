package com.fptu.exe.skillswap.modules.chat.service;

import com.fptu.exe.skillswap.infrastructure.storage.PrivateStorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ChatUploadIntentRepository;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.strategy.AttachmentValidationRegistry;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    private ChatUploadIntentRepository chatUploadIntentRepository;
    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private ChatAccessResolutionService chatAccessResolutionService;
    @Mock
    private ObjectProvider<PrivateStorageGateway> privateStorageGatewayProvider;
    @Mock
    private ObjectProvider<StorageObjectReader> storageObjectReaderProvider;
    @Mock
    private InMemoryRateLimitService rateLimitService;
    @Mock
    private StorageLifecycleProperties storageLifecycleProperties;
    @Mock
    private AttachmentValidationRegistry attachmentValidationRegistry;

    @InjectMocks
    private ChatAttachmentService service;

    @Test
    void validateAttachmentIntents_nullElementThrowsBusinessBadRequestInsteadOfDataAccess500() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        BaseException ex = assertThrows(BaseException.class, () ->
                service.validateAttachmentIntents(conversationId, userId, Arrays.asList((UUID) null)));

        assertEquals(ErrorCode.CHAT_UPLOAD_INTENT_INVALID, ex.getErrorCode());
        verifyNoInteractions(chatUploadIntentRepository);
    }
}
