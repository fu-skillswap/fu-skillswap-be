package com.fptu.exe.skillswap.modules.filestorage.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.PublicAssetUploadIntent;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.dto.request.PublicAssetUploadIntentRequest;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.PublicAssetUploadIntentResponse;
import com.fptu.exe.skillswap.modules.filestorage.repository.PublicAssetUploadIntentRepository;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicAssetUploadService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final StorageGateway storageGateway;
    private final PublicAssetUploadIntentRepository intentRepository;
    private final StoredFileRepository storedFileRepository;
    private final EntityManager entityManager;

    @Transactional
    public PublicAssetUploadIntentResponse createBlogImageIntent(UUID ownerId, PublicAssetUploadIntentRequest request) {
        String contentType = normalizeImageType(request.contentType());
        validateFilename(request.filename());
        String prefix = "public-assets/blog/" + ownerId;
        StorageGateway.PresignedUpload upload = storageGateway.generatePresignedUploadUrl(request.filename(), contentType, prefix);
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        PublicAssetUploadIntent intent = intentRepository.save(PublicAssetUploadIntent.builder()
                .owner(entityManager.getReference(User.class, ownerId)).purpose(FilePurpose.BLOG_IMAGE)
                .objectKey(upload.objectKey()).expectedContentType(contentType).expiresAt(expiresAt).build());
        return new PublicAssetUploadIntentResponse(intent.getId(), upload.uploadUrl(), expiresAt, Map.of("Content-Type", contentType));
    }

    @Transactional
    public PublicAssetResponse confirmBlogImage(UUID ownerId, UUID intentId) {
        PublicAssetUploadIntent intent = intentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (!intent.getOwner().getId().equals(ownerId) || intent.getPurpose() != FilePurpose.BLOG_IMAGE) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        if (intent.getConfirmedFile() != null) return toResponse(intent.getConfirmedFile());
        if (intent.getExpiresAt().isBefore(DateTimeUtil.now())) throw new BaseException(ErrorCode.BAD_REQUEST, "Upload intent đã hết hạn");
        StorageGateway.ObjectMetadata metadata = storageGateway.headObject(intent.getObjectKey());
        String contentType = normalizeImageType(metadata.contentType() == null ? intent.getExpectedContentType() : metadata.contentType());
        if (metadata.sizeBytes() > 10L * 1024 * 1024) throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh blog vượt quá 10 MiB");
        StoredFile file = storedFileRepository.save(StoredFile.builder().owner(intent.getOwner()).purpose(FilePurpose.BLOG_IMAGE)
                .originalName(intent.getObjectKey().substring(intent.getObjectKey().lastIndexOf('/') + 1))
                .storageProvider(storageGateway.storageProviderName()).storageKey(intent.getObjectKey())
                .publicUrl(storageGateway.resolvePublicUrl(intent.getObjectKey())).mimeType(contentType).sizeBytes(metadata.sizeBytes()).build());
        intent.setConfirmedFile(file); intent.setConfirmedAt(DateTimeUtil.now());
        return toResponse(file);
    }

    @Transactional(readOnly = true)
    public StoredFile requireOwnedBlogImage(UUID ownerId, UUID assetId) {
        return storedFileRepository.findById(assetId)
                .filter(file -> file.getPurpose() == FilePurpose.BLOG_IMAGE && file.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Blog asset không hợp lệ"));
    }

    @Transactional(readOnly = true)
    public void requireOwnedBlogImageUrl(UUID ownerId, String publicUrl) {
        if (!storedFileRepository.existsByOwnerIdAndPurposeAndPublicUrl(ownerId, FilePurpose.BLOG_IMAGE, publicUrl)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ảnh inline phải là blog asset đã được xác nhận");
        }
    }

    private PublicAssetResponse toResponse(StoredFile file) { return new PublicAssetResponse(file.getId(), file.getPublicUrl(), file.getMimeType(), file.getSizeBytes() == null ? 0L : file.getSizeBytes()); }
    private String normalizeImageType(String value) { String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT); if (!IMAGE_TYPES.contains(type)) throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Blog chỉ hỗ trợ ảnh PNG, JPEG hoặc WebP"); return type; }
    private void validateFilename(String filename) { if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) throw new BaseException(ErrorCode.BAD_REQUEST, "Tên file ảnh không hợp lệ"); }
}
