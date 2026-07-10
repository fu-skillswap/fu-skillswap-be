package com.fptu.exe.skillswap.modules.catalog.service;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.dto.response.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileOptionsResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorSupportLevelOptionResponse;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final TagRepository tagRepository;

    @Cacheable(cacheNames = "catalog", key = "'helpTopics'")
    public List<HelpTopicResponse> getHelpTopics() {
        return tagRepository.findByTypeAndStatusOrderByWeightDescNameViAsc(TagType.HELP_TOPIC, TagStatus.ACTIVE)
                .stream()
                .map(this::toHelpTopicResponse)
                .toList();
    }

    @Cacheable(cacheNames = "catalog", key = "'mentorProfileOptions'")
    public MentorProfileOptionsResponse getMentorProfileOptions() {
        List<MentorSupportLevelOptionResponse> foundationLevels = List.of(
                MentorSupportLevelOptionResponse.builder().value(1).label("Gợi ý nhanh để mentee tự ôn lại").build(),
                MentorSupportLevelOptionResponse.builder().value(2).label("Giải thích lại các ý chính khi mentee bị vướng").build(),
                MentorSupportLevelOptionResponse.builder().value(3).label("Gỡ rõ phần mentee đang hổng và cho hướng luyện lại").build(),
                MentorSupportLevelOptionResponse.builder().value(4).label("Kèm mentee hiểu lại từ gốc với lộ trình ngắn").build()
        );
        List<MentorSupportLevelOptionResponse> outputLevels = List.of(
                MentorSupportLevelOptionResponse.builder().value(1).label("Góp ý nhanh trước khi mentee chốt bài").build(),
                MentorSupportLevelOptionResponse.builder().value(2).label("Review các điểm chính trong bài nộp/project/CV/report").build(),
                MentorSupportLevelOptionResponse.builder().value(3).label("Review kỹ và chỉ ra phần chưa ổn cần sửa").build(),
                MentorSupportLevelOptionResponse.builder().value(4).label("Xem trực tiếp, góp ý sâu và hướng dẫn cách cải thiện").build()
        );
        List<MentorSupportLevelOptionResponse> directionLevels = List.of(
                MentorSupportLevelOptionResponse.builder().value(1).label("Chia sẻ góc nhìn nhanh khi mentee phân vân").build(),
                MentorSupportLevelOptionResponse.builder().value(2).label("Giúp mentee so sánh lựa chọn học, ngành hoặc việc làm").build(),
                MentorSupportLevelOptionResponse.builder().value(3).label("Gỡ rối định hướng/OJT/career bằng kinh nghiệm thực tế").build(),
                MentorSupportLevelOptionResponse.builder().value(4).label("Giúp mentee chốt bước tiếp theo và kế hoạch hành động").build()
        );
        return MentorProfileOptionsResponse.builder()
                .foundationSupportLevels(foundationLevels)
                .outputReviewSupportLevels(outputLevels)
                .directionSupportLevels(directionLevels)
                .build();
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
