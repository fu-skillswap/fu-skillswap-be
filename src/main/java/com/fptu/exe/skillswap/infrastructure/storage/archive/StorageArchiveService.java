package com.fptu.exe.skillswap.infrastructure.storage.archive;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageArchiveService {

    private final ObjectProvider<StorageGateway> storageGatewayProvider;

    public record ArchiveBatchResult(String objectKey, String sha256Hex, int rowCount) {}

    /**
     * Lưu danh sách JSON lên R2 dạng GZIP, có key cố định và kiểm tra checksum.
     */
    public ArchiveBatchResult archiveJsonLines(String datasetPrefix, long firstId, long lastId, List<String> jsonLines) {
        return archiveJsonLines(datasetPrefix, firstId + "-" + lastId, jsonLines);
    }

    public ArchiveBatchResult archiveJsonLines(String datasetPrefix, String identity, List<String> jsonLines) {
        if (jsonLines == null || jsonLines.isEmpty()) {
            return null;
        }

        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            log.warn("StorageGateway not available. Archiving skipped.");
            return null;
        }

        // 1. Tạo JSONL chuẩn và tính SHA-256 trước khi nén.
        StringBuilder jsonlBuilder = new StringBuilder();
        for (String line : jsonLines) {
            jsonlBuilder.append(line).append("\n"); // Canonical newline
        }
        String jsonlContent = jsonlBuilder.toString();
        byte[] rawBytes = jsonlContent.getBytes(StandardCharsets.UTF_8);
        
        String sha256Hex = calculateSha256(rawBytes);

        // 2. Key cố định để retry vẫn dùng cùng tên file.
        String safeIdentity = identity == null || identity.isBlank()
                ? "batch"
                : identity.replaceAll("[^a-zA-Z0-9._-]", "-");
        String fileName = String.format("%s-%s.jsonl.gz", safeIdentity, sha256Hex);
        String objectKey = datasetPrefix.endsWith("/") ? datasetPrefix + fileName : datasetPrefix + "/" + fileName;

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("archive-", ".jsonl.gz");
            
        // 3. Nén GZIP.
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
                 GZIPOutputStream gzipOut = new GZIPOutputStream(fos)) {
                gzipOut.write(rawBytes);
            }

        // 4. Tải lên R2 kèm metadata.
            Map<String, String> metadata = new HashMap<>();
            metadata.put("sha256", sha256Hex);
            metadata.put("row-count", String.valueOf(jsonLines.size()));
            metadata.put("batch-identity", safeIdentity);

            storageGateway.uploadFile(objectKey, tempFile, "application/gzip", metadata);
            
        // 5. Kiểm tra lại bằng HeadObject.
            StorageGateway.ObjectMetadata head = storageGateway.headObject(objectKey);
            long expectedLength = Files.size(tempFile);
            
        // Local fallback có thể trả 0 khi giả lập upload; nếu file có thật thì vẫn trả đúng kích thước.
            if (head.sizeBytes() != expectedLength) {
                throw new RuntimeException(String.format("Integrity mismatch: expected size %d but got %d for %s", expectedLength, head.sizeBytes(), objectKey));
            }
            if (head.metadata() == null || !sha256Hex.equals(head.metadata().get("sha256"))) {
                throw new RuntimeException(String.format("Integrity mismatch: expected sha256 %s but got %s for %s", sha256Hex,
                        head.metadata() == null ? null : head.metadata().get("sha256"), objectKey));
            }
            if (!String.valueOf(jsonLines.size()).equals(head.metadata().get("row-count"))) {
                throw new RuntimeException(String.format("Integrity mismatch: expected row count %d but got %s for %s",
                        jsonLines.size(), head.metadata().get("row-count"), objectKey));
            }

            log.info("Successfully archived {} records to {}. SHA-256: {}", jsonLines.size(), objectKey, sha256Hex);
            return new ArchiveBatchResult(objectKey, sha256Hex, jsonLines.size());

        } catch (IOException e) {
            log.error("Failed to write/upload archive temp file", e);
            throw new RuntimeException("Archive failed", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.error("Failed to delete temp archive file {}", tempFile, e);
                }
            }
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
