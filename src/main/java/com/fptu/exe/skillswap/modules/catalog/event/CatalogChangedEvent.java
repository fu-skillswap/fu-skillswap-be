package com.fptu.exe.skillswap.modules.catalog.event;

/** Published after a transactional catalog writer changes master data. */
public record CatalogChangedEvent(String source) {
}
