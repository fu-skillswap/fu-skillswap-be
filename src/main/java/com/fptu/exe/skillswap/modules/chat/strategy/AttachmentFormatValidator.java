package com.fptu.exe.skillswap.modules.chat.strategy;

import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;

public interface AttachmentFormatValidator {

    boolean supports(String contentType);

    String getCanonicalContentType();

    String getStandardExtension();

    boolean isInlineCapable();

    boolean matchesFilename(String filename);

    void validateSignature(String objectKey, StorageObjectReader storageReader);
}
