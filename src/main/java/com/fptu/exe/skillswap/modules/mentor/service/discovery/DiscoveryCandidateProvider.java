package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DiscoveryCandidateProvider {

    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";
    private final MentorProfileRepository mentorProfileRepository;

    public CandidatePage searchPage(
            MentorDiscoverySearchRequest request,
            String keywordPattern,
            String normalizedKeywordPattern,
            List<Sort.Order> orders,
            LocalDateTime now
    ) {
        int page = Math.min(Math.max(request.getPage(), 0), 19);
        int size = Math.min(Math.max(request.getSize(), 1), MentorDiscoverySearchRequest.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size, Sort.by(orders));
        boolean hasKeyword = normalizedKeywordPattern != null && !normalizedKeywordPattern.isBlank();
        Page<UUID> result = hasKeyword
                ? mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                        MentorStatus.ACTIVE, keywordPattern, normalizedKeywordPattern,
                        ACCENTED_CHARACTERS, PLAIN_CHARACTERS, now, pageable)
                : mentorProfileRepository.findDiscoverableCandidateIds(MentorStatus.ACTIVE, now, pageable);
        return new CandidatePage(result.getContent(), result.getTotalElements());
    }

    public List<MentorDiscoveryQueryRow> recallForRecommendation(
            UUID currentUserId,
            boolean richProfile,
            int safeLimit,
            LocalDateTime now,
            int defaultRecallWindowSize
    ) {
        int candidateFetchSize = richProfile
                ? Math.max(defaultRecallWindowSize, Math.max(safeLimit * 10, 60))
                : Math.max(Math.min(defaultRecallWindowSize, 120), Math.max(safeLimit * 5, safeLimit));

        return mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(
                MentorStatus.ACTIVE,
                currentUserId,
                now,
                PageRequest.of(0, candidateFetchSize)
        );
    }



}
