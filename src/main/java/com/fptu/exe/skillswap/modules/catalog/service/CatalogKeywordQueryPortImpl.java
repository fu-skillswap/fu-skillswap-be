package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.port.CatalogKeywordQueryPort;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
class CatalogKeywordQueryPortImpl implements CatalogKeywordQueryPort {
    private final TagRepository tagRepository;

    @Override
    public List<String> findAllTagLabels() {
        return tagRepository.findAll().stream()
                .flatMap(tag -> Stream.of(tag.getNameVi(), tag.getNameEn()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
