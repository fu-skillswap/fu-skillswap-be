package com.fptu.exe.skillswap.modules.filestorage.controller;

import com.fptu.exe.skillswap.modules.filestorage.dto.response.InternalStorageUploadResponse;
import com.fptu.exe.skillswap.modules.filestorage.dto.response.FileStorageCapabilityResponse;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.ProviderNeutralUploadMetadata;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "API upload file cho các luồng được cấp quyền. FE dùng uploadUrl/asset ID; không cần biết bucket, provider hoặc object key.")
public class FileStorageController {

    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final StorageProperties storageProperties;
    private final Environment environment;

    @Value("${application.upload.dir:${java.io.tmpdir}/skillswap-storage}")
    private String uploadDir;

    @Operation(summary = "Xem khả năng file storage hiện tại", description = "Dùng cho FE quyết định có hiển thị thao tác upload/download file hay không. Response không tiết lộ bucket, endpoint hoặc object key.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/capabilities")
    public ApiResponse<FileStorageCapabilityResponse> getCapabilities() {
        boolean available = storageGatewayProvider.getIfAvailable() != null;
        return ApiResponse.success(new FileStorageCapabilityResponse(available, available, available, available));
    }

    @Operation(tags = {"Internal/System"}, summary = "Lấy URL tạm thời để upload file", description = "Internal/System - không dùng cho FE production. Endpoint local trả về objectKey để công cụ kiểm thử hoàn tất upload; các flow nghiệp vụ production phải dùng upload intent của module tương ứng.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/upload-url")
    public ApiResponse<InternalStorageUploadResponse> getUploadUrl(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false, defaultValue = "application/octet-stream") String contentType
    ) {
        // Generic uploads are intentionally unavailable outside local development.
        // Production flows must use a purpose-scoped upload intent.
        ensureLocalProfile();
        validatePresignedUploadRequest(filename, contentType);
        if (principal == null || principal.getPublicId() == null) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Cần đăng nhập để upload file");
        }
        StorageGateway storageGateway = requireStorageGateway();
        var presigned = storageGateway.generatePresignedUploadUrl(filename, contentType, verificationPrefix(principal));
        return ApiResponse.success(new InternalStorageUploadResponse(
                presigned.uploadUrl(),
                null,
                presigned.objectKey(),
                new ProviderNeutralUploadMetadata(null, null, presigned.uploadUrl(),
                        Instant.now().plus(Math.max(storageProperties.getPresignedTtlMinutes(), 1), ChronoUnit.MINUTES),
                        "VERIFICATION_DOCUMENT", Map.of())));
    }

    @Operation(tags = {"Internal/System"}, summary = "Local-only upload endpoint", description = "Internal/System - không dùng cho FE production. Chỉ bật ở profile local để kiểm thử upload.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PostMapping(path = "/local-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InternalStorageUploadResponse> localUpload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String objectKey,
            @RequestPart("file") MultipartFile file
    ) {
        ensureLocalProfile();
        StorageGateway storageGateway = requireStorageGateway();
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "File upload local không được để trống");
        }
        validateOwnedObjectKey(principal, objectKey);
        writeLocalObject(objectKey, file);
        return ApiResponse.created(new InternalStorageUploadResponse(null, null, objectKey, null));
    }

    @Operation(tags = {"Internal/System"}, summary = "Local-only raw upload endpoint", description = "Internal/System - không dùng cho FE production. Chỉ bật ở profile local để kiểm thử direct upload.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @PutMapping(path = "/local-upload")
    public ApiResponse<InternalStorageUploadResponse> localUploadRaw(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String objectKey,
            @RequestBody byte[] body
    ) {
        ensureLocalProfile();
        StorageGateway storageGateway = requireStorageGateway();
        if (body == null || body.length == 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Body upload local không được để trống");
        }
        validateOwnedObjectKey(principal, objectKey);
        writeLocalObject(objectKey, body);
        return ApiResponse.created(new InternalStorageUploadResponse(null, null, objectKey, null));
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

    private String verificationPrefix(UserPrincipal principal) {
        String basePrefix = storageProperties.getDocumentsPrefix() == null ? "skillswap/verification-documents" : storageProperties.getDocumentsPrefix();
        return basePrefix.replaceAll("^/+|/+$", "") + "/users/" + principal.getPublicId();
    }

    private void validateOwnedObjectKey(UserPrincipal principal, String objectKey) {
        String key = requireSafeObjectKey(objectKey);
        boolean verificationObject = principal != null && principal.getPublicId() != null && key.startsWith(verificationPrefix(principal) + "/");
        boolean blogPublicObject = principal != null && principal.getPublicId() != null
                && key.startsWith("public-assets/blog/" + principal.getPublicId() + "/");
        boolean portfolioPublicObject = principal != null && principal.getPublicId() != null
                && key.startsWith("public-assets/portfolio/" + principal.getPublicId() + "/");
        boolean courseMaterialObject = principal != null && principal.getPublicId() != null
                && key.startsWith("course-materials/" + principal.getPublicId() + "/");
        if (!verificationObject && !blogPublicObject && !portfolioPublicObject && !courseMaterialObject) {
            throw new BaseException(ErrorCode.ACCESS_DENIED, "objectKey không thuộc phạm vi upload của người dùng");
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
