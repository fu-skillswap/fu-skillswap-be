package com.fptu.exe.skillswap.modules.catalog.port;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;

import java.util.List;

public interface CatalogQueryPort {
    List<Tag> findAllTags();
}
