package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DiscoveryCandidateProvider {

    private static final String ACCENTED_CHARACTERS = "àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ";
    private static final String PLAIN_CHARACTERS = "aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy";
    private static final UUID EMPTY_TAG_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final MentorProfileRepository mentorProfileRepository;
    private final DataSource dataSource;
    private volatile Boolean postgresDetected = null;
    private final Object postgresDetectionLock = new Object();

    public CandidateWindow recallForSearch(
            MentorDiscoverySearchRequest request,
            String normalizedKeyword,
            String keywordPattern,
            String normalizedKeywordPattern,
            boolean relevanceSort,
            List<Sort.Order> orders,
            LocalDateTime now,
            int defaultRecallWindowSize
    ) {
        int requestedPage = Math.max(request.getPage(), 0);
        int requestedSize = Math.min(Math.max(request.getSize(), 1), 30);
        int recallWindowSize = recallWindowSize(requestedPage, requestedSize, relevanceSort, defaultRecallWindowSize);
        List<UUID> tagIds = normalizedTagIds(request.getTagIds());
        boolean hasKeyword = normalizedKeyword != null && !normalizedKeyword.isBlank();

        if (hasKeyword && isPostgresDataSource()) {
            return findCandidatesByFts(normalizedKeyword, request, tagIds, now, recallWindowSize);
        }

        Pageable pageable = PageRequest.of(0, recallWindowSize, Sort.by(orders));
        Page<UUID> candidatePage;
        if (hasKeyword) {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIdsWithKeyword(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    request.getCampusId(),
                    request.getSpecializationId(),
                    hasTagFilter(request.getTagIds()),
                    tagIds,
                    keywordPattern,
                    normalizedKeywordPattern,
                    ACCENTED_CHARACTERS,
                    PLAIN_CHARACTERS,
                    now,
                    pageable
            );
        } else {
            candidatePage = mentorProfileRepository.findDiscoverableCandidateIds(
                    MentorStatus.ACTIVE,
                    MentorTagType.HELP_TOPIC,
                    request.getCampusId(),
                    request.getSpecializationId(),
                    hasTagFilter(request.getTagIds()),
                    tagIds,
                    now,
                    pageable
            );
        }
        return new CandidateWindow(candidatePage.getContent(), candidatePage.getTotalElements());
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
                MentorTagType.HELP_TOPIC,
                currentUserId,
                now,
                PageRequest.of(0, candidateFetchSize)
        );
    }

    public int recallWindowSize(int requestedPage, int requestedSize, boolean relevanceSort, int defaultRecallWindowSize) {
        int minimumWindow = Math.max(defaultRecallWindowSize, (requestedPage + 1) * requestedSize);
        if (!relevanceSort) {
            return minimumWindow;
        }
        return Math.max(minimumWindow, (requestedPage + 1) * requestedSize * 5);
    }

    private CandidateWindow findCandidatesByFts(
            String normalizedKeyword,
            MentorDiscoverySearchRequest request,
            List<UUID> tagIds,
            LocalDateTime now,
            int recallWindowSize
    ) {
        boolean hasTagFilter = hasTagFilter(request.getTagIds());
        String tagIdsArray = tagIds.isEmpty()
                ? "{00000000-0000-0000-0000-000000000000}"
                : "{" + tagIds.stream().map(UUID::toString).collect(Collectors.joining(",")) + "}";

        List<UUID> ids = mentorProfileRepository.findDiscoverableCandidateIdsByFts(
                normalizedKeyword,
                request.getCampusId(),
                request.getSpecializationId(),
                hasTagFilter,
                tagIdsArray,
                now,
                recallWindowSize,
                0
        );

        long totalCount = mentorProfileRepository.countDiscoverableCandidatesByFts(
                normalizedKeyword,
                request.getCampusId(),
                request.getSpecializationId(),
                hasTagFilter,
                tagIdsArray,
                now
        );

        return new CandidateWindow(ids, totalCount);
    }

    private List<UUID> normalizedTagIds(List<UUID> tagIds) {
        if (!hasTagFilter(tagIds)) {
            return List.of(EMPTY_TAG_ID);
        }
        return tagIds.stream().distinct().toList();
    }

    private boolean hasTagFilter(List<UUID> tagIds) {
        return tagIds != null && !tagIds.isEmpty();
    }

    public boolean isPostgresDataSource() {
        if (postgresDetected != null) {
            return postgresDetected;
        }
        synchronized (postgresDetectionLock) {
            if (postgresDetected != null) {
                return postgresDetected;
            }
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                String productName = meta.getDatabaseProductName();
                postgresDetected = productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
            } catch (Exception ex) {
                postgresDetected = false;
            }
            return postgresDetected;
        }
    }
}
