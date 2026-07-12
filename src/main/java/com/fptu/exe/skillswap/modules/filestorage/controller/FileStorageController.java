package com.fptu.exe.skillswap.modules.filestorage.controller;

import com.fptu.exe.skillswap.modules.filestorage.dto.response.PresignedUploadResponse;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "API liên quan đến lưu trữ file và Cloudflare R2")
public class FileStorageController {

    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final StorageProperties storageProperties;
    private final Environment environment;

    @Value("${application.upload.dir:${java.io.tmpdir}/skillswap-storage}")
    private String uploadDir;

    @Operation(summary = "Lấy Presigned URL để upload file", description = "Trả về một URL tạm thời (sống trong 15 phút) để client upload file trực tiếp lên Cloudflare R2 bằng HTTP PUT. Sau khi upload thành công, client sử dụng publicUrl để lưu vào database.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/upload-url")
    public ApiResponse<PresignedUploadResponse> getUploadUrl(
            @RequestParam(required = false) String filename,
            @RequestParam(required = false, defaultValue = "application/octet-stream") String contentType
    ) {
        validatePresignedUploadRequest(filename, contentType);
        StorageGateway storageGateway = requireStorageGateway();
        var presigned = storageGateway.generatePresignedUploadUrl(filename, contentType);
        return ApiResponse.success(PresignedUploadResponse.builder()
                .uploadUrl(presigned.uploadUrl())
                .publicUrl(presigned.publicUrl())
                .objectKey(presigned.objectKey())
                .build());
    }

    @Operation(summary = "Local-only upload endpoint", description = "Chỉ bật ở profile local để FE có thể upload file thật vào local storage khi giả lập presigned upload flow.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(path = "/local-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PresignedUploadResponse> localUpload(
            @RequestParam String objectKey,
            @RequestPart("file") MultipartFile file
    ) {
        ensureLocalProfile();
        StorageGateway storageGateway = requireStorageGateway();
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "File upload local không được để trống");
        }
        writeLocalObject(objectKey, file);
        return ApiResponse.created(PresignedUploadResponse.builder()
                .uploadUrl(null)
                .publicUrl(storageGateway.resolvePublicUrl(objectKey))
                .objectKey(objectKey)
                .build());
    }

    @Operation(summary = "Local-only raw upload endpoint", description = "Chỉ bật ở profile local để mô phỏng direct PUT presigned upload.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping(path = "/local-upload")
    public ApiResponse<PresignedUploadResponse> localUploadRaw(
            @RequestParam String objectKey,
            @RequestBody byte[] body
    ) {
        ensureLocalProfile();
        StorageGateway storageGateway = requireStorageGateway();
        if (body == null || body.length == 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Body upload local không được để trống");
        }
        writeLocalObject(objectKey, body);
        return ApiResponse.created(PresignedUploadResponse.builder()
                .uploadUrl(null)
                .publicUrl(storageGateway.resolvePublicUrl(objectKey))
                .objectKey(objectKey)
                .build());
    }

    private void ensureLocalProfile() {
        if (!environment.matchesProfiles("local")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private void writeLocalObject(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            writeLocalObject(objectKey, inputStream);
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể lưu file local");
        }
    }

    private void writeLocalObject(String objectKey, byte[] body) {
        try (InputStream inputStream = new java.io.ByteArrayInputStream(body)) {
            writeLocalObject(objectKey, inputStream);
        } catch (IOException ex) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Không thể lưu file local");
        }
    }

    private void writeLocalObject(String objectKey, InputStream inputStream) throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(requireSafeObjectKey(objectKey)).normalize();
        if (!target.startsWith(root)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "objectKey không hợp lệ");
        }
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String requireSafeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "objectKey không được để trống");
        }
        String normalized = objectKey.replace("\\", "/").trim();
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "objectKey không hợp lệ");
        }
        return normalized;
    }

    private void validatePresignedUploadRequest(String filename, String contentType) {
        if (filename == null || filename.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "filename không được để trống");
        }
        if (filename.contains("/") || filename.contains("\\")) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "filename không hợp lệ");
        }
        String normalizedContentType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!storageProperties.getAllowedContentTypes().contains(normalizedContentType)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "contentType không được hỗ trợ cho presigned upload");
        }
    }

    private StorageGateway requireStorageGateway() {
        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            throw new BaseException(ErrorCode.STORAGE_ERROR, "Hệ thống chưa cấu hình storage để upload file");
        }
        return storageGateway;
    }
}
