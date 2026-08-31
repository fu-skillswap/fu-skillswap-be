package com.fptu.exe.skillswap.modules.filestorage.service;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.PublicAssetUploadIntent;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.PublicAssetUploadIntentRepository;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicAssetUploadService implements PublicAssetUploadPort {
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final PublicAssetUploadIntentRepository intentRepository;
    private final StoredFileRepository storedFileRepository;

    @Transactional
    @Override
    public PublicAssetUploadPort.UploadIntent createBlogImageIntent(UUID ownerId, PublicAssetUploadPort.UploadRequest request) {
        String contentType = normalizeImageType(request.contentType());
        validateFilename(request.filename());
        String prefix = "public-assets/blog/" + ownerId;
        StorageGateway.PresignedUpload upload = storageGateway().generatePresignedUploadUrl(request.filename(), contentType, prefix);
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        PublicAssetUploadIntent intent = intentRepository.save(PublicAssetUploadIntent.builder()
                .ownerUserId(ownerId)
                .purpose(FilePurpose.BLOG_IMAGE)
                .objectKey(upload.objectKey())
                .expectedContentType(contentType)
                .expiresAt(expiresAt)
                .build());
        return new PublicAssetUploadPort.UploadIntent(intent.getId(), upload.uploadUrl(), expiresAt, Map.of("Content-Type", contentType));
    }

    @Transactional
    @Override
    public PublicAssetUploadPort.FileAssetMetadata confirmBlogImage(UUID ownerId, UUID intentId) {
        PublicAssetUploadIntent intent = intentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (!intent.getOwnerUserId().equals(ownerId) || intent.getPurpose() != FilePurpose.BLOG_IMAGE) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        if (intent.getConfirmedFile() != null) {
            return toResponse(intent.getConfirmedFile());
        }
        if (intent.getExpiresAt().isBefore(DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Upload intent đã hết hạn");
        }
        StorageGateway.ObjectMetadata metadata = storageGateway().headObject(intent.getObjectKey());
        String contentType = normalizeImageType(metadata.contentType() == null ? intent.getExpectedContentType() : metadata.contentType());
        if (metadata.sizeBytes() > 10L * 1024 * 1024) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh blog vượt quá 10 MiB");
        }
        StoredFile file = storedFileRepository.save(StoredFile.builder()
                .ownerUserId(intent.getOwnerUserId())
                .purpose(FilePurpose.BLOG_IMAGE)
                .originalName(intent.getObjectKey().substring(intent.getObjectKey().lastIndexOf('/') + 1))
                .storageProvider(storageGateway().storageProviderName())
                .storageKey(intent.getObjectKey())
                .publicUrl(storageGateway().resolvePublicUrl(intent.getObjectKey()))
                .mimeType(contentType)
                .sizeBytes(metadata.sizeBytes())
                .build());
        intent.setConfirmedFile(file);
        intent.setConfirmedAt(DateTimeUtil.now());
        return toResponse(file);
    }

    @Transactional
    @Override
    public PublicAssetUploadPort.UploadIntent createPortfolioImageIntent(UUID ownerId, PublicAssetUploadPort.UploadRequest request) {
        String contentType = normalizeImageType(request.contentType());
        validateFilename(request.filename());
        String prefix = "public-assets/portfolio/" + ownerId;
        StorageGateway.PresignedUpload upload = storageGateway().generatePresignedUploadUrl(request.filename(), contentType, prefix);
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        PublicAssetUploadIntent intent = intentRepository.save(PublicAssetUploadIntent.builder()
                .ownerUserId(ownerId)
                .purpose(FilePurpose.PORTFOLIO)
                .objectKey(upload.objectKey())
                .expectedContentType(contentType)
                .expiresAt(expiresAt)
                .build());
        return new PublicAssetUploadPort.UploadIntent(intent.getId(), upload.uploadUrl(), expiresAt, Map.of("Content-Type", contentType));
    }

    @Transactional
    @Override
    public PublicAssetUploadPort.FileAssetMetadata confirmPortfolioImage(UUID ownerId, UUID intentId) {
        PublicAssetUploadIntent intent = intentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent"));
        if (!intent.getOwnerUserId().equals(ownerId) || intent.getPurpose() != FilePurpose.PORTFOLIO) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy upload intent");
        }
        if (intent.getConfirmedFile() != null) {
            return toResponse(intent.getConfirmedFile());
        }
        if (intent.getExpiresAt().isBefore(DateTimeUtil.now())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Upload intent đã hết hạn");
        }
        StorageGateway.ObjectMetadata metadata = storageGateway().headObject(intent.getObjectKey());
        String contentType = normalizeImageType(metadata.contentType() == null ? intent.getExpectedContentType() : metadata.contentType());
        if (metadata.sizeBytes() > 5L * 1024 * 1024) {
            throw new BaseException(ErrorCode.PAYLOAD_TOO_LARGE, "Ảnh dự án vượt quá 5 MiB");
        }
        StoredFile file = storedFileRepository.save(StoredFile.builder()
                .ownerUserId(intent.getOwnerUserId())
                .purpose(FilePurpose.PORTFOLIO)
                .originalName(intent.getObjectKey().substring(intent.getObjectKey().lastIndexOf('/') + 1))
                .storageProvider(storageGateway().storageProviderName())
                .storageKey(intent.getObjectKey())
                .publicUrl(storageGateway().resolvePublicUrl(intent.getObjectKey()))
                .mimeType(contentType)
                .sizeBytes(metadata.sizeBytes())
                .build());
        intent.setConfirmedFile(file);
        intent.setConfirmedAt(DateTimeUtil.now());
        return toResponse(file);
    }

    @Transactional(readOnly = true)
    @Override
    public PublicAssetUploadPort.FileAssetMetadata requireOwnedPortfolioImage(UUID ownerId, UUID assetId) {
        return storedFileRepository.findById(assetId)
                .filter(file -> file.getPurpose() == FilePurpose.PORTFOLIO && file.getOwnerUserId().equals(ownerId))
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Portfolio asset không hợp lệ"));
    }

    @Transactional(readOnly = true)
    @Override
    public PublicAssetUploadPort.FileAssetMetadata requireOwnedBlogImage(UUID ownerId, UUID assetId) {
        return storedFileRepository.findById(assetId)
                .filter(file -> file.getPurpose() == FilePurpose.BLOG_IMAGE && file.getOwnerUserId().equals(ownerId))
                .map(this::toResponse)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Blog asset không hợp lệ"));
    }

    @Transactional(readOnly = true)
    public void requireOwnedBlogImageUrl(UUID ownerId, String publicUrl) {
        if (!storedFileRepository.existsByOwnerUserIdAndPurposeAndPublicUrl(ownerId, FilePurpose.BLOG_IMAGE, publicUrl)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Ảnh inline phải là blog asset đã được xác nhận");
        }
    }

    private PublicAssetUploadPort.FileAssetMetadata toResponse(StoredFile file) {
        return new PublicAssetUploadPort.FileAssetMetadata(file.getId(), file.getPublicUrl(), file.getMimeType(), file.getSizeBytes() == null ? 0L : file.getSizeBytes());
    }

    private StorageGateway storageGateway() {
        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage cho public asset");
        }
        return storageGateway;
    }

    private String normalizeImageType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Ảnh chỉ hỗ trợ định dạng PNG, JPEG hoặc WebP");
        }
        return type;
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tên file ảnh không hợp lệ");
        }
    }
}
