package com.fptu.exe.skillswap.modules.catalog.support;

import java.util.List;
import java.util.UUID;

/** Test fixture providing standardized catalog categories and skill keyword data. */
public final class CatalogTestFixture {

    private CatalogTestFixture() {}

    public static UUID randomCategoryId() {
        return UUID.randomUUID();
    }

    public static List<String> sampleKeywords() {
        return List.of("Java", "Spring Boot", "Architecture", "Microservices", "PostgreSQL");
    }
}
