package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.port.CatalogQueryPort;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryPortImpl implements CatalogQueryPort {

    private final TagRepository tagRepository;

    @Override
    public List<Tag> findAllTags() {
        return tagRepository.findAll();
    }
}
