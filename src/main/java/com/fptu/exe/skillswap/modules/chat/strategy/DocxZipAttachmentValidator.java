package com.fptu.exe.skillswap.modules.chat.strategy;

import com.fptu.exe.skillswap.infrastructure.storage.StorageObjectReader;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocxZipAttachmentValidator implements AttachmentFormatValidator {

    private static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200;

    @Override
    public boolean supports(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.trim().toLowerCase(Locale.ROOT);
        return MIME.equalsIgnoreCase(lower) || lower.contains("wordprocessingml");
    }

    @Override
    public String getCanonicalContentType() {
        return MIME;
    }

    @Override
    public String getStandardExtension() {
        return ".docx";
    }

    @Override
    public boolean isInlineCapable() {
        return false;
    }

    @Override
    public boolean matchesFilename(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".docx");
    }

    @Override
    public void validateSignature(String objectKey, StorageObjectReader storageReader) {
        long uncompressedBytes = 0L;
        int entryCount = 0;
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;
        try (var raw = storageReader.openObject(objectKey);
             var zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES || entry.isDirectory()) {
                    if (entryCount > MAX_ENTRIES) {
                        throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
                    }
                    continue;
                }
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasWordDocument |= "word/document.xml".equals(entry.getName());
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressedBytes += read;
                    if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
                    }
                }
            }
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể đọc cấu trúc tệp đính kèm");
        }
        if (!hasContentTypes || !hasWordDocument) {
            throw new BaseException(ErrorCode.CHAT_ATTACHMENT_INVALID);
        }
    }
}
