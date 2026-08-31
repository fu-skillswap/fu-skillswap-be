package com.fptu.exe.skillswap.modules.catalog.port;

import java.util.List;

/** Read-only tag labels exposed for keyword discovery without exposing catalog entities. */
public interface CatalogKeywordQueryPort {
    List<String> findAllTagLabels();
}
