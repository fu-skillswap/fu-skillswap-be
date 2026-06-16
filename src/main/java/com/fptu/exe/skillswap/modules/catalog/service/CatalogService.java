package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.dto.response.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final TagRepository tagRepository;

    public List<HelpTopicResponse> getHelpTopics() {
        return tagRepository.findByTypeAndStatusOrderByWeightDescNameViAsc(TagType.HELP_TOPIC, TagStatus.ACTIVE)
                .stream()
                .map(this::toHelpTopicResponse)
                .toList();
    }

    private HelpTopicResponse toHelpTopicResponse(Tag tag) {
        return HelpTopicResponse.builder()
                .id(tag.getId())
                .code(tag.getCode())
                .nameVi(tag.getNameVi())
                .nameEn(tag.getNameEn())
                .weight(tag.getWeight())
                .build();
    }
}
