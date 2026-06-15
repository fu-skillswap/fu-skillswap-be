package com.fptu.exe.skillswap.modules.mentor.dto;

import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record MentorVerificationDocumentResponse(
        UUID id,
        VerificationDocumentType documentType,
        VerificationDocumentStatus status,
        VerificationStorageKind storageKind,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String fileUrl,
        boolean isActive,
        Integer version,
        String reviewNote,
        String rejectedReason,
        LocalDateTime uploadedAt
) {
}
