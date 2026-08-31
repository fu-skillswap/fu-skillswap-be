package com.fptu.exe.skillswap.modules.filestorage.service;

import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.port.VerificationDocumentStoragePort;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class VerificationDocumentStoragePortImpl implements VerificationDocumentStoragePort {

    private final StoredFileRepository storedFileRepository;

    @Override
    @Transactional
    public VerificationDocumentMetadata registerVerificationDocument(VerificationDocumentRegistration command) {
        if (command == null || command.ownerUserId() == null || command.storageKey() == null || command.storageKey().isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thông tin file xác thực không hợp lệ");
        }
        StoredFile saved = storedFileRepository.save(StoredFile.builder()
                .ownerUserId(command.ownerUserId())
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(command.originalFilename())
                .storageProvider(command.storageProvider())
                .storageKey(command.storageKey())
                .publicUrl("private://" + command.storageKey())
                .mimeType(command.contentType())
                .sizeBytes(command.sizeBytes())
                .build());
        return new VerificationDocumentMetadata(
                saved.getId(), saved.getOriginalName(), saved.getMimeType(), saved.getSizeBytes(), saved.getPublicUrl());
    }
}
