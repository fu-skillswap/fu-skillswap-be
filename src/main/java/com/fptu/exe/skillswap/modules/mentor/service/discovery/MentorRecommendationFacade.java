package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.infrastructure.config.DiscoveryProperties;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;

import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MentorRecommendationFacade {

    private final StudentProfileRepository studentProfileRepository;

    private final DiscoveryCandidateProvider discoveryCandidateProvider;
    private final DiscoveryEnrichmentService discoveryEnrichmentService;
    private final DiscoveryRankingService discoveryRankingService;
    private final DiscoveryMapper discoveryMapper;
    private final DiscoveryProperties discoveryProperties;

    @Transactional(readOnly = true)
    public List<MentorRecommendationResponse> getRecommendations(UUID currentUserId, int limit) {
        if (currentUserId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }

        int safeLimit = Math.min(Math.max(limit, 1), 12);
        StudentProfile menteeProfile = studentProfileRepository.findWithDetailsByUserId(currentUserId).orElse(null);

        MentorMatchingContext context = new MentorMatchingContext(
                currentUserId,
                menteeProfile,

                DateTimeUtil.now(),
                discoveryProperties.recommendationAlgorithmVersion()
        );
        boolean richProfile = menteeProfile != null
                && menteeProfile.getProgram() != null
                && menteeProfile.getSpecialization() != null;

        List<MentorDiscoveryQueryRow> candidates = discoveryCandidateProvider.recallForRecommendation(
                context.menteeUserId(),
                richProfile,
                safeLimit,
                context.evaluatedAt(),
                discoveryProperties.recallWindowSize()
        );
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<UUID> candidateIds = candidates.stream()
                .map(MentorDiscoveryQueryRow::mentorUserId)
                .toList();
        Map<UUID, MentorEnrichedData> enrichedDataByMentor = discoveryEnrichmentService.loadMentorEnrichedData(
                candidateIds,
                context.evaluatedAt()
        );

        return candidates.stream()
                .map(candidate -> {
                    MentorEnrichedData enrichedData = enrichedDataByMentor.getOrDefault(
                            candidate.mentorUserId(),
                            MentorEnrichedData.empty()
                    );
                    DiscoveryRankingService.RecommendationScore score = discoveryRankingService.scoreRecommendation(
                            candidate,
                            enrichedData,
                            context.menteeProfile(),
                            context.evaluatedAt()
                    );
                    return new RankedRecommendation(
                            candidate,
                            discoveryMapper.toRecommendation(candidate, enrichedData, score)
                    );
                })
                .sorted(Comparator
                        .comparing((RankedRecommendation ranked) -> ranked.response().matchScore(), Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                        .thenComparing(ranked -> defaultInteger(ranked.candidate().completedSessions()), Comparator.reverseOrder())
                        .thenComparing(ranked -> defaultDecimal(ranked.candidate().ratingAverage()), Comparator.reverseOrder()))
                .limit(safeLimit)
                .map(RankedRecommendation::response)
                .toList();
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private record RankedRecommendation(
            MentorDiscoveryQueryRow candidate,
            MentorRecommendationResponse response
    ) {
    }
}
