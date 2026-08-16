package com.fptu.exe.skillswap.modules.chat.strategy;

import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AttachmentValidationRegistry {

    private final List<AttachmentFormatValidator> validators;

    public AttachmentValidationRegistry(List<AttachmentFormatValidator> validatorList) {
        this.validators = validatorList != null ? new ArrayList<>(validatorList) : new ArrayList<>();
    }

    public AttachmentFormatValidator resolveValidator(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        for (AttachmentFormatValidator validator : validators) {
            if (validator.supports(contentType)) {
                return validator;
            }
        }
        throw new BaseException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    public String normalizeContentType(String contentType) {
        return resolveValidator(contentType).getCanonicalContentType();
    }

    public void validateFilename(String filename, String contentType) {
        if (filename == null || filename.isBlank()) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
        }
        AttachmentFormatValidator validator = resolveValidator(contentType);
        if (!validator.matchesFilename(filename)) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
        }
    }

    public String extensionFor(String contentType) {
        return resolveValidator(contentType).getStandardExtension();
    }

    public boolean inlineCapable(String contentType) {
        try {
            return resolveValidator(contentType).isInlineCapable();
        } catch (BaseException ignored) {
            return false;
        }
    }

    public void validateSignature(String objectKey, String contentType, StorageObjectReader storageReader) {
        AttachmentFormatValidator validator = resolveValidator(contentType);
        validator.validateSignature(objectKey, storageReader);
    }
}
