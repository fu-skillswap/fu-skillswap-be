package com.fptu.exe.skillswap.modules.filestorage.controller;

import com.fptu.exe.skillswap.modules.filestorage.dto.response.PresignedUploadResponse;
import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "API liên quan đến lưu trữ file và Cloudflare R2")
public class FileStorageController {

    private final StorageGateway storageGateway;

    @Operation(summary = "Lấy Presigned URL để upload file", description = "Trả về một URL tạm thời (sống trong 15 phút) để client upload file trực tiếp lên Cloudflare R2 bằng HTTP PUT. Sau khi upload thành công, client sử dụng publicUrl để lưu vào database.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/upload-url")
    public ApiResponse<PresignedUploadResponse> getUploadUrl(
            @RequestParam(required = false) String filename,
            @RequestParam(required = false, defaultValue = "application/octet-stream") String contentType
    ) {
        var presigned = storageGateway.generatePresignedUploadUrl(filename, contentType);
        return ApiResponse.success(PresignedUploadResponse.builder()
                .uploadUrl(presigned.uploadUrl())
                .publicUrl(presigned.publicUrl())
                .objectKey(presigned.objectKey())
                .build());
    }
}
