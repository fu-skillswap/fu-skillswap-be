package com.fptu.exe.skillswap.modules.filestorage.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.PublicAssetUploadIntent;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.filestorage.repository.PublicAssetUploadIntentRepository;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAssetUploadServiceAvatarTest {

    @Mock
    private ObjectProvider<StorageGateway> storageGatewayProvider;
    @Mock
    private StorageGateway storageGateway;
    @Mock
    private PublicAssetUploadIntentRepository intentRepository;
    @Mock
    private StoredFileRepository storedFileRepository;

    private PublicAssetUploadService service;

    @BeforeEach
    void setUp() {
        service = new PublicAssetUploadService(storageGatewayProvider, intentRepository, storedFileRepository);
    }

    @Test
    void createAvatarIntent_validImage_shouldUseAvatarPrefixAndPurpose() {
        UUID userId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        when(storageGatewayProvider.getIfAvailable()).thenReturn(storageGateway);
        when(storageGateway.generatePresignedUploadUrl(eq("avatar.webp"), eq("image/webp"), eq("public-assets/avatars/" + userId)))
                .thenReturn(new StorageGateway.PresignedUpload(
                        "https://upload.example.com/avatar",
                        "https://cdn.example.com/public-assets/avatars/" + userId + "/avatar.webp",
                        "public-assets/avatars/" + userId + "/avatar.webp"
                ));
        when(intentRepository.save(any(PublicAssetUploadIntent.class))).thenAnswer(inv -> {
            PublicAssetUploadIntent saved = inv.getArgument(0);
            saved.setId(intentId);
            return saved;
        });

        PublicAssetUploadPort.UploadIntent result = service.createAvatarIntent(
                userId, new PublicAssetUploadPort.UploadRequest("avatar.webp", "IMAGE/WEBP"));

        assertEquals(intentId, result.uploadIntentId());
        assertEquals("https://upload.example.com/avatar", result.uploadUrl());
        assertEquals("USER_AVATAR_IMAGE", result.metadata().assetType());
    }

    @Test
    void createAvatarIntent_unsupportedContentType_shouldThrow415() {
        BaseException ex = assertThrows(BaseException.class, () ->
                service.createAvatarIntent(UUID.randomUUID(),
                        new PublicAssetUploadPort.UploadRequest("avatar.svg", "image/svg+xml")));

        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex.getErrorCode());
    }

    @Test
    void confirmAvatar_exceeds5MiB_shouldThrowPayloadTooLarge() {
        UUID userId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        String key = "public-assets/avatars/" + userId + "/big.png";
        PublicAssetUploadIntent intent = PublicAssetUploadIntent.builder()
                .id(intentId)
                .ownerUserId(userId)
                .purpose(FilePurpose.AVATAR)
                .objectKey(key)
                .expectedContentType("image/png")
                .expiresAt(DateTimeUtil.now().plusMinutes(10))
                .build();

        when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
        when(storageGatewayProvider.getIfAvailable()).thenReturn(storageGateway);
        when(storageGateway.headObject(key)).thenReturn(
                new StorageGateway.ObjectMetadata(key, "image/png", 5L * 1024 * 1024 + 1, Map.of()));

        BaseException ex = assertThrows(BaseException.class, () -> service.confirmAvatar(userId, intentId));

        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    void confirmAvatar_validWithin5MiB_shouldPersistStoredFile() {
        UUID userId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String key = "public-assets/avatars/" + userId + "/me.jpg";
        PublicAssetUploadIntent intent = PublicAssetUploadIntent.builder()
                .id(intentId)
                .ownerUserId(userId)
                .purpose(FilePurpose.AVATAR)
                .objectKey(key)
                .expectedContentType("image/jpeg")
                .expiresAt(DateTimeUtil.now().plusMinutes(10))
                .build();

        when(intentRepository.findByIdForUpdate(intentId)).thenReturn(Optional.of(intent));
        when(storageGatewayProvider.getIfAvailable()).thenReturn(storageGateway);
        when(storageGateway.headObject(key)).thenReturn(
                new StorageGateway.ObjectMetadata(key, "image/jpeg", 5L * 1024 * 1024, Map.of()));
        when(storageGateway.storageProviderName()).thenReturn("R2");
        when(storageGateway.resolvePublicUrl(key)).thenReturn("https://cdn.example.com/" + key);
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId(fileId);
            return f;
        });

        PublicAssetUploadPort.FileAssetMetadata metadata = service.confirmAvatar(userId, intentId);

        assertEquals(fileId, metadata.assetId());
        assertEquals("https://cdn.example.com/" + key, metadata.publicUrl());
        assertEquals("USER_AVATAR_IMAGE", metadata.metadata().assetType());
    }
}
