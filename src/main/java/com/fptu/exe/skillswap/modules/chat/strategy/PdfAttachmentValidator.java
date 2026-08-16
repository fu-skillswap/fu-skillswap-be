package com.fptu.exe.skillswap.modules.chat.strategy;

import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class PdfAttachmentValidator implements AttachmentFormatValidator {

    private static final String MIME = "application/pdf";

    @Override
    public boolean supports(String contentType) {
        return MIME.equalsIgnoreCase(contentType == null ? "" : contentType.trim());
    }

    @Override
    public String getCanonicalContentType() {
        return MIME;
    }

    @Override
    public String getStandardExtension() {
        return ".pdf";
    }

    @Override
    public boolean isInlineCapable() {
        return false;
    }

    @Override
    public boolean matchesFilename(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf");
    }

    @Override
    public void validateSignature(String objectKey, StorageObjectReader storageReader) {
        try (var in = storageReader.openObject(objectKey)) {
            byte[] header = in.readNBytes(8);
            boolean valid = new String(header, StandardCharsets.US_ASCII).startsWith("%PDF-");
            if (!valid) {
                throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
            }
        } catch (IOException exception) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể kiểm tra tệp đính kèm");
        }
    }
}
