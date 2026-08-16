package com.fptu.exe.skillswap.modules.chat.strategy;

import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

@Component
public class PngAttachmentValidator implements AttachmentFormatValidator {

    private static final String MIME = "image/png";

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
        return ".png";
    }

    @Override
    public boolean isInlineCapable() {
        return true;
    }

    @Override
    public boolean matchesFilename(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png");
    }

    @Override
    public void validateSignature(String objectKey, StorageObjectReader storageReader) {
        try (var in = storageReader.openObject(objectKey)) {
            byte[] header = in.readNBytes(8);
            boolean valid = header.length >= 4
                    && header[0] == (byte) 0x89
                    && header[1] == (byte) 0x50
                    && header[2] == (byte) 0x4e
                    && header[3] == (byte) 0x47;
            if (!valid) {
                throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
            }
        } catch (IOException exception) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể kiểm tra tệp đính kèm");
        }
    }
}
