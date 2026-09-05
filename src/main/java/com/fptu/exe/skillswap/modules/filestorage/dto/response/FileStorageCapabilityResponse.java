package com.fptu.exe.skillswap.modules.filestorage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Runtime capability only. It intentionally exposes neither bucket details nor object-storage configuration.
 */
@Schema(description = "Khả năng upload/download mà FE có thể dùng để quyết định hiển thị nút file. Không chứa thông tin bucket hoặc storage provider.")
public record FileStorageCapabilityResponse(
        @Schema(description = "Có thể dùng private file storage hay không.", example = "true")
        boolean privateFileStorageAvailable,
        @Schema(description = "Có thể upload file đính kèm chat hay không.", example = "true")
        boolean chatAttachmentsAvailable,
        @Schema(description = "Có thể upload asset blog hay không.", example = "true")
        boolean blogAssetUploadsAvailable
) {
}
