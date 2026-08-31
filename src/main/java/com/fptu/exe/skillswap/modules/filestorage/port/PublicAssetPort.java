package com.fptu.exe.skillswap.modules.filestorage.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only asset metadata contract; storage entities never cross module boundaries. */
public interface PublicAssetPort {
    Map<UUID, AssetMetadata> findPublicAssets(Collection<UUID> assetIds);

    record AssetMetadata(UUID fileId, UUID ownerUserId, String originalFilename,
                         String contentType, long sizeBytes, String url) { }
}
