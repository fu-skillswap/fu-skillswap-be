package com.fptu.exe.skillswap.modules.filestorage.dto.response;

/**
 * Runtime capability only. It intentionally exposes neither bucket details nor object-storage configuration.
 */
public record FileStorageCapabilityResponse(
        boolean privateFileStorageAvailable,
        boolean chatAttachmentsAvailable,
        boolean mentorServiceResourcesAvailable,
        boolean blogAssetUploadsAvailable
) {
}
